package org.v.shortly.service;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.v.shortly.util.ClickClassifier;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class ClickRecorder {

    private static final Logger log = LoggerFactory.getLogger(ClickRecorder.class);

    private static final int QUEUE_CAPACITY = 10_000;   // at most 10k unsaved clicks held in memory
    private static final int MAX_BATCH = 1_000;         // at most 1k clicks written per database round trip

    // INSERT ... SELECT: "insert a row built from the urls row whose short_code matches".
    // If the link was deleted while the click waited in the queue, the SELECT finds nothing and
    // no row is inserted: no foreign-key error, no extra lookup query.
    private static final String INSERT_SQL =
            "INSERT INTO click_events (url_id, clicked_at, browser, device, referrer_host, visitor_hash) "
                    + "SELECT id, ?, ?, ?, ?, ? FROM urls WHERE short_code = ?";

    // The counter is incremented INSIDE the database, so two updates can never overwrite each other.
    private static final String COUNTER_SQL =
            "UPDATE urls SET click_count = click_count + ? WHERE short_code = ?";

    // What we capture on the request thread. HttpServletRequest must NOT be used from another thread,
    // so we copy out plain strings now and do the processing later.
    private record RawClick(String shortCode, LocalDateTime clickedAt, String ip, String userAgent, String referer) {}

    private final BlockingQueue<RawClick> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);   // thread-safe, fixed size
    private final AtomicLong dropped = new AtomicLong();    // thread-safe counter of events we had to throw away
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final String salt;

    public ClickRecorder(JdbcTemplate jdbc, PlatformTransactionManager txManager,
                         @Value("${app.analytics.salt}") String salt) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(txManager);       // lets us start a transaction explicitly in code
        this.salt = salt;
    }

    // Called on the redirect request thread. Must be FAST and must never throw.
    public void record(String shortCode, String ip, String userAgent, String referer) {
        RawClick click = new RawClick(shortCode, LocalDateTime.now(), ip,   // timestamp is taken NOW, not at flush time
                cut(userAgent, 256), cut(referer, 512));
        // Header sizes are attacker-controlled; cutting them stops someone filling our memory with huge headers.
        if (!queue.offer(click)) {          // offer() returns false at once if the queue is full (put() would block!)
            dropped.incrementAndGet();      // backpressure: sacrifice the analytics event, protect the redirect
        }
    }

    @Scheduled(fixedDelay = 2000)           // run 2 seconds after the previous run FINISHED (so runs never overlap)
    public void flush() {
        long lost = dropped.getAndSet(0);   // read the counter and reset it in one atomic step
        if (lost > 0) log.warn("Click queue was full: dropped {} click event(s)", lost);

        List<RawClick> batch = new ArrayList<>(MAX_BATCH);
        while (queue.drainTo(batch, MAX_BATCH) > 0) {   // move up to 1000 events from the queue into our list
            int size = batch.size();
            try {
                tx.executeWithoutResult(status -> write(batch));   // everything in write() = ONE transaction
                log.info("Flushed {} click event(s)", size);
            } catch (Exception e) {
                log.error("Could not save {} click event(s); they are lost", size, e);   // never crash the scheduler
            }
            batch.clear();                  // reuse the list for the next chunk
        }
    }

    @PreDestroy                             // runs once when the app shuts down
    public void flushOnShutdown() {
        flush();                            // don't throw away events that are still waiting in memory
    }

    private void write(List<RawClick> batch) {
        // 1) One batched INSERT for all clicks. The lambda fills in the ? placeholders for each click.
        jdbc.batchUpdate(INSERT_SQL, batch, MAX_BATCH, (ps, c) -> {
            ps.setObject(1, c.clickedAt());
            ps.setString(2, ClickClassifier.browser(c.userAgent()));
            ps.setString(3, ClickClassifier.device(c.userAgent()));
            ps.setString(4, ClickClassifier.referrerHost(c.referer()));
            ps.setString(5, visitorHash(c.ip(), c.userAgent()));
            ps.setString(6, c.shortCode());
        });

        // 2) Group clicks by link, then ONE UPDATE per link. 500 clicks on a hot link = a single "+500".
        Map<String, Integer> perCode = new HashMap<>();
        for (RawClick c : batch) {
            perCode.merge(c.shortCode(), 1, Integer::sum);   // add 1 to this code's running total
        }
        jdbc.batchUpdate(COUNTER_SQL, perCode.entrySet(), 100, (ps, entry) -> {
            ps.setInt(1, entry.getValue());     // how many clicks to add
            ps.setString(2, entry.getKey());    // which link
        });
    }

    private String visitorHash(String ip, String userAgent) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] digest = sha.digest((salt + "|" + ip + "|" + userAgent).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 16);   // first 16 bytes = 32 hex characters
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);               // every JVM must have SHA-256, so this cannot happen
        }
    }

    private static String cut(String s, int max) {
        return (s == null || s.length() <= max) ? s : s.substring(0, max);
    }
}