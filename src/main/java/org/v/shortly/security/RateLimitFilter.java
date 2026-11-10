package org.v.shortly.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private record Rule(String name, int limit, Duration window) {}   // "at most <limit> requests per <window>"

    private static final Rule AUTH_RULE        = new Rule("auth",        10, Duration.ofMinutes(1)); // brute-force protection
    private static final Rule CREATE_USER_RULE = new Rule("create-user", 30, Duration.ofMinutes(1));
    private static final Rule CREATE_ANON_RULE = new Rule("create-anon",  5, Duration.ofMinutes(1)); // anonymous = stricter

    private final StringRedisTemplate redis;

    public RateLimitFilter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        AuthenticatedUser user = currentUser();
        Rule rule = pickRule(request, user);
        if (rule == null) {                                       // this endpoint isn't limited
            chain.doFilter(request, response);
            return;
        }

        boolean byUser = user != null && rule != AUTH_RULE;       // auth endpoints are always limited per IP
        String who = byUser ? "user:" + user.id() : "ip:" + request.getRemoteAddr();
        String key = "rl:" + rule.name() + ":" + who;             // e.g. rl:auth:ip:127.0.0.1

        boolean blocked = false;
        long retryAfter = rule.window().toSeconds();
        try {
            Long count = redis.opsForValue().increment(key);      // atomic +1 (creates the key at 1 if missing)
            if (count != null) {
                if (count == 1) {
                    redis.expire(key, rule.window());             // first hit starts the window's countdown
                }
                response.setHeader("X-RateLimit-Limit", String.valueOf(rule.limit()));
                response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, rule.limit() - count)));
                if (count > rule.limit()) {
                    blocked = true;
                    Long ttl = redis.getExpire(key, TimeUnit.SECONDS);   // seconds until the counter resets
                    if (ttl != null && ttl > 0) retryAfter = ttl;
                }
            }
        } catch (Exception e) {
            log.warn("Rate limiter unavailable, allowing request: {}", e.getMessage());   // fail open
        }

        if (blocked) {
            response.setHeader("Retry-After", String.valueOf(retryAfter));   // tells clients when to try again
            HttpErrorWriter.write(response, HttpStatus.TOO_MANY_REQUESTS, "Too many requests. Please slow down.");
            return;                                                          // 429 and stop
        }
        chain.doFilter(request, response);
    }

    private Rule pickRule(HttpServletRequest request, AuthenticatedUser user) {
        if (!"POST".equals(request.getMethod())) return null;                // only limit writes
        String path = request.getRequestURI();
        if (path.startsWith("/api/v1/auth/")) return AUTH_RULE;
        if (path.equals("/api/v1/urls")) return user != null ? CREATE_USER_RULE : CREATE_ANON_RULE;
        return null;
    }

    private AuthenticatedUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.getPrincipal() instanceof AuthenticatedUser u) ? u : null;
    }
}