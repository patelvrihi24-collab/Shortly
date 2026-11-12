# Shortly

A URL shortener REST API built with **Java 21, Spring Boot, PostgreSQL and Redis**. It is designed around the problems a real shortener has to solve: a read-heavy redirect path, cache invalidation, abuse protection, ownership and privacy-conscious analytics.

**Live demo:** `TODO: add your deployed URL` &nbsp;|&nbsp; **API docs:** `TODO: <your-url>/swagger-ui/index.html`

> Free hosting tiers may sleep when idle, so the first request after a pause can be slow.

## Features

- Shorten any `http(s)` URL, with an optional **custom alias** and optional **expiry** (1-365 days)
- **Redis cache-aside** on the redirect path, with graceful fallback to PostgreSQL if Redis is down
- **JWT authentication** (register / login), BCrypt password hashing
- Per-user link management: paginated listing and delete (which also evicts the cache)
- **Rate limiting** backed by Redis (stricter for anonymous users and for auth endpoints)
- **Asynchronous click analytics**: browser, device, referrer and unique visitors per link, without slowing redirects
- Consistent error format (RFC 7807 `ProblemDetail`), input validation, versioned API (`/api/v1`)
- Flyway migrations, OpenAPI/Swagger docs, health probes, Docker image, CI

## Architecture

```
                         +----------------+
 GET /{code} ----------> |  Controller    | --- 302 redirect
                         +-------+--------+
                                 |
                         +-------v--------+      hit      +-----------+
                         |  UrlService    | ------------> |   Redis   |
                         |  (cache-aside) | <------------ | (cache +  |
                         +---+--------+---+      miss      |  limits)  |
                             |        |                   +-----------+
                  DB lookup  |        | offer(click)  (in-memory queue, non-blocking)
                             v        v
                      +-----------+  +--------------+  every 2 s, one batched INSERT
                      | PostgreSQL|<-| ClickRecorder|
                      +-----------+  +--------------+
```

Layers: `controller` (HTTP) -> `service` (business logic) -> `repository` (data access). Security filters (JWT, rate limit) run before controllers.

## Tech stack

| Area | Choice |
|---|---|
| Language / framework | Java 21, Spring Boot 4 (Web, Data JPA, Security, Validation, Actuator) |
| Database | PostgreSQL 16, schema managed by Flyway |
| Cache / rate limits | Redis 7 |
| Auth | JWT (jjwt), BCrypt |
| Docs | springdoc-openapi (Swagger UI) |
| Tests | JUnit 5, Testcontainers (real Postgres and Redis), Awaitility |
| Packaging / CI | Docker (multi-stage), Docker Compose, GitHub Actions |

## API overview

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/v1/auth/register` | no | Create an account, returns a JWT |
| POST | `/api/v1/auth/login` | no | Log in, returns a JWT |
| POST | `/api/v1/urls` | optional | Create a short link (`url`, optional `customAlias`, `ttlDays`) |
| GET | `/api/v1/urls?page=0&size=20` | yes | List my links, newest first |
| GET | `/api/v1/urls/{code}/stats?days=7` | yes | Clicks, unique visitors, per-day series, top browsers/devices/referrers |
| DELETE | `/api/v1/urls/{code}` | yes | Delete my link (and evict it from the cache) |
| GET | `/{code}` | no | 302 redirect to the original URL (404 if unknown, 410 if expired) |
| GET | `/actuator/health/readiness` | no | Readiness probe |

Interactive docs are at `/swagger-ui/index.html`. Use **Authorize** and paste the token without the word "Bearer".

## Run it locally

Requirements: JDK 21 and Docker.

**Option 1: databases in Docker, app from your IDE or Maven**

```bash
docker compose up -d          # PostgreSQL + Redis
./mvnw spring-boot:run        # on Windows: mvnw.cmd spring-boot:run
```

**Option 2: everything in Docker**

```bash
docker compose --profile full up --build
```

The API is then available at `http://localhost:8080`.

### Configuration

| Variable | Purpose | Default (dev only) |
|---|---|---|
| `SPRING_DATASOURCE_URL`, `DB_USER`, `DB_PASSWORD` | PostgreSQL connection | local Docker database |
| `SPRING_DATA_REDIS_HOST` / `SPRING_DATA_REDIS_URL` | Redis connection | `localhost` |
| `JWT_SECRET` | JWT signing key (48+ random characters in production) | insecure dev value |
| `ANALYTICS_SALT` | Salt for hashing visitor identifiers | insecure dev value |
| `APP_BASE_URL` | Public base URL used in returned short links | `http://localhost:8080` |
| `FORWARD_HEADERS` | Set to `native` behind a trusted reverse proxy | `none` |
| `APP_CACHE_ENABLED` | Set to `false` to bypass Redis (benchmarking) | `true` |

**Never deploy with the default secrets.** They are in this public repository.

## Tests

```bash
./mvnw verify                 # needs Docker (Testcontainers starts PostgreSQL and Redis)
```

- **Unit tests** for the Base62 encoder and the user-agent / referrer classifier
- **Integration tests** that start the real application against real PostgreSQL and Redis containers and exercise it over HTTP: redirect behavior, validation, alias conflicts, ownership checks, cache eviction on delete, asynchronous click recording and rate limiting. Flyway migrations are applied from scratch on every run.

## Design decisions

1. **Short codes are Base62(id).** Insert the row, then encode its database id. IDs are unique, so codes never collide and need no retry loop. Trade-off: codes are sequential and guessable. Alternatives: random codes with collision retry, or a distributed ID generator.
2. **Custom aliases are 6-10 characters.** Generated codes stay below 6 characters until about 916 million links (62^5), so an alias can never collide with a generated code in practice. The UNIQUE constraint is still the real guard against two simultaneous requests for the same alias.
3. **Cache-aside with aligned TTLs.** The cache entry lives for at most 24 hours, or until the link expires if that is sooner, so Redis can never serve an expired link. Deleting a link evicts its cache entry immediately. Redis errors are treated as cache misses, so an outage means slower redirects, not downtime.
4. **302, not 301.** Browsers cache 301 redirects permanently, which would hide repeat visits from the server and break analytics.
5. **Stateless JWT authentication.** Any instance can verify a token without shared session state. Trade-off: tokens cannot be revoked before they expire. Login runs a BCrypt check even for unknown emails and returns one generic error, to avoid account enumeration and timing leaks.
6. **Ownership is part of the query.** Another user's link and a nonexistent link both return 404, so link codes cannot be probed.
7. **Fixed-window rate limiting in Redis.** Cheap and works across multiple instances. It fails open if Redis is unavailable. Trade-off: bursts are possible at window boundaries.
8. **Asynchronous, batched analytics.** A redirect only places an event in a bounded in-memory queue. A scheduled job writes events in one batched INSERT every 2 seconds and updates counters atomically in SQL. When the queue is full, events are dropped to protect redirect latency. Delivery is at-most-once. Visitors are stored only as a salted hash, and only the referrer host is kept.
9. **Readiness depends on the database, not Redis.** Because Redis is optional by design, a Redis outage must not make the platform take the app out of service.

## Performance

Load test: 50 virtual users, 30 seconds, `GET /{code}` (k6, script in `load-test/`). Everything ran on one laptop, so absolute numbers are conservative; the relative difference is the point.

| | Requests/s | Avg latency | p(95) latency |
|---|---|---|---|
| Redis cache ON | TODO | TODO | TODO |
| Redis cache OFF | TODO | TODO | TODO |

## Project structure

```
src/main/java/org/v/shortly
  controller/   REST endpoints (links, auth, redirect)
  service/      UrlService, AuthService, StatsService, ClickRecorder
  repository/   Spring Data repositories + JDBC stats queries
  entity/       Url, User
  dto/          request/response records
  security/     JWT filter, rate-limit filter, security config
  exception/    domain exceptions + global handler
  config/       scheduling, OpenAPI
  util/         click classification
src/main/resources/db/migration   Flyway migrations (V1..V4)
```

## Known limitations and next steps

- Analytics delivery is at-most-once: a crash can lose up to about 2 seconds of events. A durable queue (Redis Streams, Kafka or RabbitMQ) would fix this.
- Fixed-window rate limiting allows boundary bursts; a sliding window or token bucket would be smoother.
- No refresh tokens, logout or token revocation; no email verification or password reset.
- Sequential short codes are guessable; reserved aliases (such as `swagger-ui`) are not blocked yet.
- `click_events` grows without bound; production would partition by month and keep daily rollups.
- Day boundaries in statistics are UTC.
- Crawlers and link-preview bots can inflate click counts.
