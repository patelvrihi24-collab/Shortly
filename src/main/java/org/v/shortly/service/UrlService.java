package org.v.shortly.service;

import org.slf4j.Logger;                                        // logging interface (Spring Boot includes it)
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException; // thrown when a DB constraint (like UNIQUE) is violated
import org.springframework.data.redis.core.StringRedisTemplate; // Spring's ready-made Redis client for text keys/values
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.v.shortly.Base62;                                    // keep YOUR existing import path
import org.v.shortly.dto.ShortenRequest;
import org.v.shortly.dto.ShortenResponse;
import org.v.shortly.entity.Url;
import org.v.shortly.exception.AliasAlreadyExistsException;
import org.v.shortly.exception.ExpiredLinkException;
import org.v.shortly.exception.NotFoundException;
import org.v.shortly.repository.UrlRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import java.util.List;
import org.v.shortly.dto.PagedResponse;
import org.v.shortly.dto.UrlSummaryResponse;

@Service
public class UrlService {

    private static final Logger log = LoggerFactory.getLogger(UrlService.class);
    // One logger per class. "static final" = created once and shared by all instances.

    private static final String CACHE_PREFIX = "url:";
    // Every Redis key starts with "url:" (e.g. "url:2"), so our keys can't clash with other uses of Redis.

    private static final Duration MAX_CACHE_TTL = Duration.ofHours(24);
    // The longest we keep a link in Redis. Bounds memory use and limits how stale an entry can get.

    private final UrlRepository urlRepository;
    private final StringRedisTemplate redis;                    // Spring injects this automatically
    private final String baseUrl;

    public UrlService(UrlRepository urlRepository,
                      StringRedisTemplate redis,
                      @Value("${app.base-url}") String baseUrl) {
        this.urlRepository = urlRepository;
        this.redis = redis;
        this.baseUrl = baseUrl;
    }

    @Transactional(readOnly = true)
    public PagedResponse<UrlSummaryResponse> listForUser(Long userId, int page, int size) {
        int safePage = Math.max(page, 0);                       // negative pages would crash
        int safeSize = Math.min(Math.max(size, 1), 100);        // clamp to 1..100: stops ?size=10000000 from dumping the table
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "id"));
        // Newest first. The id grows with creation time, is indexed, and gives a stable order (no ties).

        Page<Url> result = urlRepository.findByUserId(userId, pageable);
        List<UrlSummaryResponse> items = result.getContent().stream().map(this::toSummary).toList();
        return new PagedResponse<>(items, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    // No @Transactional on purpose: repository.delete runs in its own transaction and COMMITS before we touch Redis.
    public void delete(String shortCode, Long userId) {
        Url url = urlRepository.findByShortCodeAndUserId(shortCode, userId)
                .orElseThrow(() -> new NotFoundException("Short link not found: " + shortCode));
        // Someone else's link and a missing link both give 404: attackers can't probe which codes exist.

        urlRepository.delete(url);                              // 1) database first (source of truth)
        try {
            redis.delete(CACHE_PREFIX + shortCode);             // 2) then evict the cache so the dead link stops redirecting NOW
        } catch (Exception e) {
            log.warn("Could not evict cache for {}: {}", shortCode, e.getMessage());   // the TTL will clean it up eventually
        }
    }

    private UrlSummaryResponse toSummary(Url url) {
        return new UrlSummaryResponse(url.getShortCode(), baseUrl + "/" + url.getShortCode(),
                url.getOriginalUrl(), url.getCreatedAt(), url.getExpiresAt(), url.getClickCount());
    }

    @Transactional
    public ShortenResponse shorten(ShortenRequest request, Long userId) {
        Url url = new Url(request.url(), userId);                       // build the entity (not saved yet)
        if (request.ttlDays() != null) {                        // user asked for an expiry
            url.setExpiresAt(LocalDateTime.now().plusDays(request.ttlDays()));
            // now + N days. LocalDateTime.now() is UTC because main() sets the JVM time zone to UTC.
        }

        String alias = request.customAlias();
        if (alias != null && !alias.isBlank()) {                // custom alias path
            if (urlRepository.existsByShortCode(alias)) {       // friendly early check -> clear 409
                throw new AliasAlreadyExistsException("Alias already taken: " + alias);
            }
            url.setShortCode(alias);                            // we already know the code
            try {
                urlRepository.save(url);                        // INSERT runs now (IDENTITY strategy)
            } catch (DataIntegrityViolationException e) {
                // Two people can pass the check above at the same moment (a race condition).
                // The UNIQUE constraint in the database is the real guard, and it rejects the second one.
                throw new AliasAlreadyExistsException("Alias already taken: " + alias);
            }
        } else {                                                // generated code path (same as Phase 1)
            url = urlRepository.save(url);                      // INSERT to get the id
            url.setShortCode(Base62.encode(url.getId()));       // id -> short code; Hibernate auto-saves this change
        }

        return toResponse(url);
    }

    // NOTE: no @Transactional here. See the explanation below.
    public String resolve(String shortCode) {
        String key = CACHE_PREFIX + shortCode;

        String cached = readCache(key);                         // 1) try Redis first
        if (cached != null) {
            log.info("CACHE HIT  {}", shortCode);
            return cached;                                      // done: the database was never touched
        }

        log.info("CACHE MISS {}", shortCode);                   // 2) fall back to Postgres
        Url url = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new NotFoundException("Short link not found: " + shortCode));

        LocalDateTime now = LocalDateTime.now();
        if (url.getExpiresAt() != null && url.getExpiresAt().isBefore(now)) {
            throw new ExpiredLinkException("This link has expired");   // 410 Gone
        }

        Duration ttl = MAX_CACHE_TTL;                           // 3) decide how long Redis may keep it
        if (url.getExpiresAt() != null) {
            Duration remaining = Duration.between(now, url.getExpiresAt());   // time left until expiry
            if (remaining.compareTo(ttl) < 0) {                 // if the link expires sooner than 24h...
                ttl = remaining;                                // ...the cache entry must not outlive the link
            }
        }
        writeCache(key, url.getOriginalUrl(), ttl);             // 4) store for next time
        return url.getOriginalUrl();
    }

    private String readCache(String key) {
        try {
            return redis.opsForValue().get(key);                // GET key (null if missing)
        } catch (Exception e) {
            log.warn("Redis read failed, falling back to DB: {}", e.getMessage());
            return null;                                        // treat a Redis failure as a cache miss
        }
    }

    private void writeCache(String key, String value, Duration ttl) {
        try {
            redis.opsForValue().set(key, value, ttl);           // SET key value EX <seconds>
        } catch (Exception e) {
            log.warn("Redis write failed, continuing without cache: {}", e.getMessage());
        }
    }

    private ShortenResponse toResponse(Url url) {
        return new ShortenResponse(
                url.getShortCode(),
                baseUrl + "/" + url.getShortCode(),
                url.getOriginalUrl(),
                url.getCreatedAt(),
                url.getExpiresAt()
        );
    }
}