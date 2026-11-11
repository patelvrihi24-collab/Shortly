package org.v.shortly;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.TimeZone;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.awaitility.Awaitility.await;               // "keep checking until true or timeout"
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)   // start the REAL server on a free port
@Import(TestcontainersConfig.class)                                           // use the containers above
@TestInstance(TestInstance.Lifecycle.PER_CLASS)               // one test object for all tests, so @BeforeAll can be non-static
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)         // run in @Order sequence (the rate-limit test must be last)
class ShortlyIntegrationTest {

    static {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        // main() sets UTC, but tests don't call main(). Without this, the Postgres 16 container rejects "Asia/Calcutta"
        // exactly like on day one. A static block runs when the class loads, before Spring connects to the database.
    }

    private static final String PASSWORD = "SuperSecret123";

    @Autowired
    private Environment env;                                  // lets us read the random port: local.server.port

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)       // we WANT to see the 302, not follow it
            .build();

    private String ownerEmail;
    private String ownerToken;

    @BeforeAll
    void registerOwner() throws Exception {
        ownerEmail = newEmail();
        ownerToken = register(ownerEmail);
    }

    @Test @Order(1)
    void anonymousUserCanShortenAndBeRedirected() throws Exception {
        HttpResponse<String> created = call("POST", "/api/v1/urls", "{\"url\":\"https://example.com/hello\"}", null);
        assertEquals(201, created.statusCode());

        HttpResponse<String> redirect = call("GET", "/" + field(created.body(), "shortCode"), null, null);
        assertEquals(302, redirect.statusCode());             // 302, not 301, so every click reaches our server
        assertEquals("https://example.com/hello", redirect.headers().firstValue("Location").orElse(""));
    }

    @Test @Order(2)
    void dangerousSchemesAreRejected() throws Exception {
        HttpResponse<String> r = call("POST", "/api/v1/urls", "{\"url\":\"javascript:alert(1)\"}", ownerToken);
        assertEquals(400, r.statusCode());
    }

    @Test @Order(3)
    void unknownCodeIs404() throws Exception {
        assertEquals(404, call("GET", "/zzzzzzzz", null, null).statusCode());
    }

    @Test @Order(4)
    void customAliasWorksOnceThenConflicts() throws Exception {
        String alias = "it" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);   // 10 chars, unique per run
        String body = "{\"url\":\"https://example.com/a\",\"customAlias\":\"" + alias + "\"}";
        assertEquals(201, call("POST", "/api/v1/urls", body, ownerToken).statusCode());
        assertEquals(409, call("POST", "/api/v1/urls", body, ownerToken).statusCode());
    }

    @Test @Order(5)
    void ownershipIsEnforcedAndDeleteEvictsTheCache() throws Exception {
        String code = createLink(ownerToken, "https://example.com/owned");
        assertEquals(302, call("GET", "/" + code, null, null).statusCode());   // this visit puts the link into Redis

        String otherToken = register(newEmail());
        assertEquals(404, call("DELETE", "/api/v1/urls/" + code, null, otherToken).statusCode());   // not theirs
        assertEquals(302, call("GET", "/" + code, null, null).statusCode());                       // still alive

        HttpResponse<String> list = call("GET", "/api/v1/urls", null, ownerToken);
        assertEquals(200, list.statusCode());
        assertTrue(list.body().contains("\"" + code + "\""));

        assertEquals(204, call("DELETE", "/api/v1/urls/" + code, null, ownerToken).statusCode());
        assertEquals(404, call("GET", "/" + code, null, null).statusCode());
        // If delete forgot to evict Redis, this would still be 302. That's the cache-invalidation lesson from Phase 2.
    }

    @Test @Order(6)
    void protectedEndpointsNeedATokenAndAuthRulesHold() throws Exception {
        assertEquals(401, call("GET", "/api/v1/urls", null, null).statusCode());
        assertEquals(409, call("POST", "/api/v1/auth/register", credentials(ownerEmail, PASSWORD), null).statusCode());
        assertEquals(401, call("POST", "/api/v1/auth/login", credentials(ownerEmail, "wrong-password"), null).statusCode());
        assertEquals(200, call("POST", "/api/v1/auth/login", credentials(ownerEmail, PASSWORD), null).statusCode());
    }

    @Test @Order(7)
    void clicksAreRecordedAsynchronouslyAndAppearInStats() throws Exception {
        String code = createLink(ownerToken, "https://example.com/stats");
        for (int i = 0; i < 3; i++) {
            HttpResponse<String> r = call("GET", "/" + code, null, null,
                    "User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                    "Referer", "https://news.ycombinator.com/item?id=1");
            assertEquals(302, r.statusCode());
        }
        // Clicks wait in an in-memory queue and are written every 2 seconds, so we poll instead of asserting at once.
        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(500)).untilAsserted(() -> {
            HttpResponse<String> stats = call("GET", "/api/v1/urls/" + code + "/stats?days=7", null, ownerToken);
            assertEquals(200, stats.statusCode());
            assertTrue(Pattern.compile("\"clicksInPeriod\"\\s*:\\s*3[,}]").matcher(stats.body()).find(), stats.body());
            assertTrue(stats.body().contains("Chrome"));
            assertTrue(stats.body().contains("news.ycombinator.com"));
        });
    }

    @Test @Order(9)   // keep this LAST: a tripped limiter blocks auth calls from this IP for up to a minute
    void repeatedBadLoginsGetRateLimited() throws Exception {
        boolean limited = false;
        for (int i = 0; i < 15 && !limited; i++) {            // the limit is 10 per minute, so 15 tries must trip it
            HttpResponse<String> r = call("POST", "/api/v1/auth/login", credentials(ownerEmail, "wrong-password"), null);
            if (r.statusCode() == 429) {
                limited = true;
                assertTrue(r.headers().firstValue("Retry-After").isPresent());   // tells clients when to retry
            } else {
                assertEquals(401, r.statusCode());
            }
        }
        assertTrue(limited, "expected a 429 within 15 attempts");
    }

    // ---------- helpers ----------

    private HttpResponse<String> call(String method, String path, String body, String token, String... headers) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + env.getProperty("local.server.port") + path))
                .timeout(Duration.ofSeconds(10));
        if (body != null) b.header("Content-Type", "application/json");
        if (token != null) b.header("Authorization", "Bearer " + token);
        for (int i = 0; i + 1 < headers.length; i += 2) b.header(headers[i], headers[i + 1]);   // extra header name/value pairs
        b.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String register(String email) throws Exception {
        HttpResponse<String> r = call("POST", "/api/v1/auth/register", credentials(email, PASSWORD), null);
        assertEquals(201, r.statusCode(), r.body());
        return field(r.body(), "token");
    }

    private String createLink(String token, String url) throws Exception {
        HttpResponse<String> r = call("POST", "/api/v1/urls", "{\"url\":\"" + url + "\"}", token);
        assertEquals(201, r.statusCode(), r.body());
        return field(r.body(), "shortCode");
    }

    private static String credentials(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    private static String newEmail() {
        return "it-" + UUID.randomUUID() + "@example.com";     // unique, so reruns never collide
    }

    private static String field(String json, String name) {   // tiny JSON string-field reader: no extra library needed
        Matcher m = Pattern.compile("\"" + name + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        assertTrue(m.find(), "field '" + name + "' not found in: " + json);
        return m.group(1);
    }
}