# LinkForge

> Self-hostable link management infrastructure for developers and AI agents.

Production-grade REST API built on Java 21 + Spring Boot 3 + PostgreSQL + Redis,
with a first-class MCP server enabling Claude and other AI agents to manage
links via natural language.

## Architecture

[insert draw.io diagram here]

Request flow:
GET /{shortCode}
→ Bloom Filter (unknown codes → 404, no Redis/DB touch)
→ Redis Cache-Aside (99.45% hit rate)
→ PostgreSQL (cache miss only)
→ Async click logging (non-blocking)

## Quick Start

git clone https://github.com/yourusername/linkforge
cd linkforge
docker-compose up

API available at http://localhost:8080
Swagger UI at http://localhost:8080/swagger-ui.html
Grafana at http://localhost:3000

## MCP Server (AI Agent Interface)

cd mcp-server
pip install -r requirements.txt
python linkforge_mcp.py

Add to Claude Desktop config — Claude can then:
- "Shorten this URL for me, expires in 30 days"
- "How many clicks did my portfolio link get this week?"
- "List all my active short links"
- "Is my portfolio link still reachable?"

[insert demo GIF here]

## Performance

Load tested at 250 concurrent users over 30 seconds:
- 1,418 requests/second sustained throughput
- 99.45% Redis cache hit rate (36,164 hits / 200 misses)
- 96ms median redirect latency
- 0% error rate across 43,248 requests
- ~36,000 Postgres queries avoided via caching

[insert Grafana screenshot here]

## Key Design Decisions

See DESIGN.md for full reasoning behind every architectural choice.

- 302 over 301 — analytics accuracy over browser caching
- Base62 encoding — collision-free, deterministic, compact
- Cache-Aside — optimal for 10:1 read/write workloads
- Append-only click_events — no lock contention, full audit trail
- Async analytics — redirect latency unaffected by write load
- Redis-backed rate limiting — correct across multiple instances
- Bloom filter — eliminates bot probe DB load at near-zero cost

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 21 |
| Framework | Spring Boot 3 |
| Database | PostgreSQL 16 + Flyway |
| Cache | Redis 7 + RedisBloom |
| Rate Limiting | Bucket4j + Redis |
| MCP Server | Python 3.13 + FastMCP |
| Testing | JUnit 5 + Mockito + Testcontainers |
| Observability | Actuator + Micrometer + Prometheus + Grafana |
| CI/CD | GitHub Actions |
| Container | Docker + Docker Compose |

## API Reference

Full docs at /swagger-ui.html when running locally.

POST   /urls                          Create short link
GET    /{shortCode}                   Redirect (302)
GET    /urls/{shortCode}/analytics    Click analytics
GET    /urls                          List all links
DELETE /urls/{shortCode}              Soft delete link