# LinkForge — Design Decisions

## 1. Why 302 over 301?
301 is cached permanently by browsers — subsequent clicks never reach our
server, making analytics impossible. 302 forces every redirect through our
server, ensuring every click is tracked. For a link management platform,
analytics accuracy outweighs the marginal performance gain of browser caching.

## 2. Why Base62 over random strings or MD5 hashing?
Base62 encoding of auto-incremented DB IDs guarantees zero collisions by
design — two links can never get the same short code. MD5 hashing of the
original URL causes the same URL to always produce the same code (not always
desirable) and has collision risk at scale. Random strings require a uniqueness
check on every insert. Base62 is deterministic, compact, and collision-free.

## 3. Why Cache-Aside over Write-Through?
This system is read-heavy (10:1 read/write ratio minimum). Write-Through
populates cache on every write — wasted for links that may never be accessed.
Cache-Aside only caches on first read, meaning only actively-accessed links
consume Redis memory. Under load testing, 99.45% cache hit rate confirms this
pattern works well for this access distribution.

## 4. TTL Strategy — Why 24 hours?
Long enough that hot links stay cached across peak traffic periods.
Short enough that deleted or expired links don't redirect indefinitely
(maximum 24h window where a deleted link could still resolve).
Cache invalidation on delete closes this window immediately for explicit deletes.

## 5. Why append-only link_clicks over counter columns?
A counter column (clicks = clicks + 1) causes row-level lock contention on
hot links under concurrent load. Append-only inserts have no contention —
each click is an independent INSERT. It also enables richer queries:
clicks by hour, by referrer, by user agent — none of which are possible
with a single counter.

## 6. Why async analytics writes?
The user redirecting does not benefit from waiting for our analytics write.
Decoupling the write from the response reduces redirect latency. The accepted
tradeoff: if the application crashes between the redirect response and the
async write, that click event is lost. At <1% event loss under realistic
failure conditions, this is acceptable for a link shortener.

## 7. Why Redis-backed rate limiting over in-memory?
In-memory rate limiting is per-instance — with two app instances, a user
gets double the rate limit by alternating between instances. Redis-backed
rate limiting (token bucket via INCR + EXPIRE) shares state across all
instances, enforcing the limit correctly regardless of horizontal scale.

## 8. Bloom Filter — false positive rate and tradeoff
RedisBloom default false positive rate is ~1%. A false positive means the
filter says a short code might exist when it doesn't — causing an unnecessary
Redis lookup. The benefit: zero false negatives, meaning codes that definitely
don't exist (bot probes, random scans) are rejected before touching Redis or
Postgres. At 1% FP rate, the filter still eliminates ~99% of invalid probes.

## 9. Cache stampede — known risk and mitigation
When a hot cached key expires, simultaneous requests all miss cache and hit
Postgres concurrently. Current mitigation: 24h TTL significantly reduces
expiry frequency for hot links. Full mitigation would require mutex locking
or stale-while-revalidate, deferred to a future iteration.

## 10. Scaling to multiple instances
The current design is stateless at the application layer — Redis and Postgres
are shared, rate limiting is Redis-backed, Bloom filter is seeded from
Postgres on startup. Horizontal scaling requires: a load balancer, shared
Redis Cluster, and read replicas on Postgres for analytics queries.
The Base62 encoder uses DB-generated IDs so no distributed ID generation
is needed unless Postgres is sharded.