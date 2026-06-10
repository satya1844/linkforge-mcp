# LinkForge — Build Plan

---

# Phase 1 — The Foundation

**Goal:** Working redirect system with Postgres. Nothing else.
**Duration:** 2–3 days

---

## Components

- **Docker Compose** → Postgres + Redis Stack + App wired together
- **Flyway V1** → `links` table migration
- **Flyway V2** → `link_clicks` table migration
- **Base62 Encoder** → Written from scratch, no library
- **Endpoints**
    - `POST /urls` → Create short link, optional custom alias, 409 on conflict
    - `GET /{shortCode}` → 302 redirect, Postgres only (no cache yet)
    - `GET /urls/{shortCode}/analytics` → Basic click count + last accessed
- **API Key Auth** → Simple header filter (`X-API-Key`)
- **Error Handling** → Global `@ControllerAdvice`, structured JSON errors

---

## Database Schema (End of Phase 1)

### V1__create_links_table.sql

```sql
CREATE TABLE links (
    id            BIGSERIAL PRIMARY KEY,
    short_code    VARCHAR(20) UNIQUE NOT NULL,
    original_url  TEXT NOT NULL,
    custom_alias  BOOLEAN DEFAULT FALSE,
    created_at    TIMESTAMP DEFAULT NOW(),
    expires_at    TIMESTAMP,
    deleted_at    TIMESTAMP
);
```

### V2__create_link_clicks_table.sql

```sql
CREATE TABLE link_clicks (
    id          BIGSERIAL PRIMARY KEY,
    link_id     BIGINT REFERENCES links(id),
    clicked_at  TIMESTAMP DEFAULT NOW(),
    user_agent  TEXT,
    referrer    TEXT,
    ip_address  VARCHAR(45)
);
```

---

## Request Flow

### POST `/urls`

1. Validate input
2. Generate Base62 short code (or use custom alias)
3. Check uniqueness
4. Persist to Postgres
5. Return `shortCode` + full short URL

### GET `/{shortCode}`

1. Query Postgres by `short_code`
2. If not found → **404 Not Found**
3. If expired → **410 Gone**
4. Insert row into `link_clicks` (synchronous for now)
5. Return **302 Found** with `Location` header

### GET `/urls/{shortCode}/analytics`

1. Query Postgres
2. Execute `COUNT(*) FROM link_clicks WHERE link_id = ?`
3. Return JSON:

```json
{
  "totalClicks": 125,
  "createdAt": "2026-06-04T10:30:00Z",
  "lastAccessed": "2026-06-04T12:15:00Z"
}
```

---

## Exit Criteria

- [x] `POST /urls` creates a link and returns short code
- [x] `GET /{shortCode}` redirects correctly in browser
- [x] Custom alias works
- [x] Duplicate alias returns **409 Conflict**
- [x] Expired links return **410 Gone**
- [x] Analytics endpoint returns real click data
- [x] Flyway migrations run automatically on startup
- [x] Everything runs through `docker-compose up`
- [x] Swagger UI accessible at `/swagger-ui.html`

**Status: COMPLETE — 46 tests, 0 failures**

---

# Phase 2 — Production-Grade Internals

**Goal:** Make the redirect path fast, safe, and bulletproof.
**Duration:** 3–4 days

---

## What You're Adding and Why

| What | Why |
|------|-----|
| Redis Cache-Aside | Stop Postgres waking up on every redirect |
| Bloom Filter | Stop Redis getting checked for codes that don't exist |
| Async Analytics | Stop click logging slowing down the redirect response |
| Link Expiry + Grace Period | Handle expired links correctly, not just with 404 |
| Distributed Rate Limiting | Prevent abuse, works across multiple app instances |
| Testcontainers for Redis | Prove the cache behavior works, not just assume it |

---

## Step 1 — Redis Cache-Aside

### Redis Key Structure

```
Key:   "link:{shortCode}"     e.g.  "link:abc123"
Value: originalUrl            e.g.  "https://google.com"
TTL:   86400 seconds (24h)
```

### Cache-Aside Logic

```
1. Check Redis for "link:{shortCode}"
2. HIT  → fire async click event → return 302
3. MISS → query Postgres
            → not found   → 404
            → found       → SET in Redis with TTL
                          → fire async click event
                          → return 302
```

### Cache Invalidation — Do This From Day One

```
On link delete:  DEL "link:{shortCode}"
On link expiry:  DEL "link:{shortCode}"   ← @Scheduled job handles this
```

### How to Verify It's Working

```bash
# Open Redis CLI inside container
docker exec -it <redis-container> redis-cli

# Watch all Redis commands in real time
MONITOR

# Hit redirect in another terminal — first hit shows SET, subsequent hits show GET only
# Postgres logs go silent after first hit — that silence is the proof
```

---

## Step 2 — Async Analytics

### The Problem With Phase 1

```
GET /{shortCode}
  → find URL         (fast)
  → INSERT click     (slow — synchronous DB write — user waits for this)
  → return 302
```

### What Changes

```
GET /{shortCode}
  → find URL                      (fast)
  → dispatch click event async    (non-blocking, returns immediately)
  → return 302                    (user already redirected)

Separately — async thread:
  → INSERT into link_clicks
  → done
```

### The Tradeoff — Document This in DESIGN.md

```
What you gain:   redirect latency drops, user never waits for analytics write
What you accept: if app crashes between dispatch and write, that click is lost forever

Decision: losing <1% of analytics events is acceptable.
          Adding latency to every redirect is not.
```

### Critical Bug to Avoid

`@Async` methods lose the HTTP request context. Extract everything before dispatching:

```java
// WRONG — request context gone inside async thread
asyncService.logClick(linkId, httpRequest);

// CORRECT — extract first, pass primitives
String ip = extractIp(httpRequest);
String userAgent = httpRequest.getHeader("User-Agent");
String referrer = httpRequest.getHeader("Referer");
asyncService.logClick(linkId, ip, userAgent, referrer);
```

---

## Step 3 — Bloom Filter

### The Problem It Solves

```
Bots probe random short codes constantly:
GET /aaaaaa → Redis MISS → Postgres query → 404
GET /aaaaab → Redis MISS → Postgres query → 404
GET /aaaaac → Redis MISS → Postgres query → 404
(thousands per second — all hitting Postgres for nothing)
```

### What the Bloom Filter Adds

```
GET /aaaaaa
  → Bloom filter: "definitely does not exist" → 404 immediately
  → Redis never checked, Postgres never checked
  → response in <1ms
```

### Two Guarantees to Understand

```
"Definitely NO"  → safe to return 404 immediately — zero false negatives
"Probably YES"   → still check Redis/Postgres to confirm — false positives exist
```

False positive rate is configurable — lower rate = more memory. Standard RedisBloom is sufficient.

### When to Populate

```
On link creation:  BF.ADD "bloom:links" "{shortCode}"
On app startup:    seed from all existing short_codes in Postgres
                   (Redis may have been restarted and lost state)
On link deletion:  standard Bloom filters do NOT support deletion
                   (document this limitation — it's an interview talking point)
```

### Updated Redirect Flow With Bloom Filter

```
GET /{shortCode}
  → Bloom Filter
      "definitely not exist" → 404 (done — no Redis, no Postgres)
      "probably exists"
          → Redis
              HIT  → async click → 302
              MISS → Postgres
                       not found         → 404
                       in grace period   → 410 with message
                       found             → cache in Redis → async click → 302
```

---

## Step 4 — Link Expiry + Grace Period

### Behavior

```
Link created with expiresAt = 2026-12-31

Before expiry:            → 302 normal redirect
After expiry, in grace:   → 410 "This link expired on Dec 31, 2026"
After grace period ends:  → 404 (link fully dead)
```

### Two Components

**Runtime check in redirect:**
```
if expiresAt is null              → not expired, proceed
if expiresAt > now                → not expired, proceed
if expiresAt < now AND
   grace_until > now              → 410 with message
if grace_until < now              → 404
```

**Nightly cleanup job:**
```java
@Scheduled(cron = "0 0 2 * * *")   // 2am every night
public void processExpiredLinks() {
    // Find links where expiresAt < now and grace_until is null
    // Set grace_until = expiresAt + gracePeriodHours
    // DEL Redis key for each (cache invalidation)
}
```

### New Flyway Migration

```sql
-- V3__add_grace_period.sql
ALTER TABLE links ADD COLUMN grace_until TIMESTAMP;
ALTER TABLE links ADD COLUMN grace_period_hours INTEGER DEFAULT 24; b
```

---

## Step 5 — Distributed Rate Limiting

### Why Distributed Matters

```
In-memory (wrong):
  Instance A knows user used 90/100 requests
  Instance B thinks user used 0/100
  User hits Instance B → gets another 100 free
  Effective limit = 100 × number of instances

Redis-backed (correct):
  Both instances read/write same Redis counter
  User used 90/100 → both instances know this
  Effective limit = 100 regardless of instance count
```

### What to Rate Limit

```
POST /urls          → 10 requests/minute per API key  (creation is expensive)
GET /{shortCode}    → 60 requests/minute per IP       (redirect is cheap but abuse is real)
```

### Response When Rate Limited

```json
HTTP 429 Too Many Requests
{
  "status": 429,
  "error": "Rate limit exceeded",
  "message": "Too many requests. Limit resets in 47 seconds.",
  "retryAfter": 47
}
```

Include `Retry-After` response header — that's the HTTP standard.

---

## New Flyway Migrations in Phase 2

```sql
-- V3__add_grace_period.sql
ALTER TABLE links ADD COLUMN grace_until TIMESTAMP;
ALTER TABLE links ADD COLUMN grace_period_hours INTEGER DEFAULT 24;

-- V4__add_partial_index.sql
CREATE INDEX idx_active_short_codes
ON links (short_code)
WHERE deleted_at IS NULL
AND (expires_at IS NULL OR expires_at > NOW());

-- V5__add_indexes_for_analytics.sql
CREATE INDEX idx_clicks_link_id_time
ON link_clicks (link_id, clicked_at DESC);
```

Run `EXPLAIN ANALYZE` on your analytics query before and after V5.
You should see Seq Scan become Index Scan.

---

## Testcontainers — New Tests for Phase 2

| Test | What it proves |
|------|---------------|
| Second redirect does not query Postgres | Cache-Aside is working |
| Redis key exists after first redirect | Cache population is working |
| Link deletion removes Redis key | Cache invalidation is working |
| Expired link returns 410 not 404 | Grace period logic is correct |
| Rate limit returns 429 with Retry-After | Rate limiting fires at correct threshold |
| Unknown short code returns 404 without DB query | Bloom filter is blocking invalid probes |
| Click row exists after redirect completes | Async analytics write is happening |

### The Most Important Test

```java
@Test
void secondRedirectShouldNotHitPostgres() {
    // Create a link
    // Hit redirect once (cache miss — Postgres queried)
    // Reset Hibernate query counter
    // Hit redirect again
    // Assert Postgres query count == 0
    // Assert Redis key exists
}
```

---

## Full Phase 2 Redirect Flow

```
GET /{shortCode}
        ↓
  Bloom Filter
  "definitely not exist"? ──→ 404
        ↓ "probably exists"
  Redis GET "link:{shortCode}"
  HIT? ──→ dispatch async click ──→ 302
        ↓ MISS
  Postgres SELECT WHERE short_code = ?
  Not found?        ──→ 404
  In grace period?  ──→ 410 with message
        ↓ Found
  Redis SET "link:{shortCode}" EX 86400
        ↓
  Dispatch async click event
        ↓
  302 returned to user

  (separately — async thread pool)
        ↓
  INSERT INTO link_clicks (link_id, ip, user_agent, referrer, clicked_at)
```

---

## Exit Criteria

- [ ] Redis MONITOR shows GET on second redirect, no Postgres query fired
- [ ] Bloom filter returns 404 for unknown codes — verified in Testcontainers test
- [ ] Async analytics — click rows appear after redirect without blocking it
- [ ] Expired links return 410 with message during grace period
- [ ] Rate limiting returns 429 with `Retry-After` header
- [ ] Link deletion removes Redis key — verified via `redis-cli`
- [ ] All Testcontainers tests passing including Redis container
- [ ] `EXPLAIN ANALYZE` shows Index Scan on `short_code` lookup
- [ ] DESIGN.md updated with async tradeoff and cache invalidation decisions

---

## Phase 2 Rule

Do not move to Phase 3 until the `MONITOR` output and Testcontainers tests
both prove the cache is working correctly.

Running code is not proof. Tests and observable behavior are proof.