# Phase 1 — The Foundation

**Goal:** Working redirect system with Postgres. Nothing else.  
**Duration:** 2–3 days

---

## 📦 Components

- **Docker Compose** → Postgres + Redis stack + App wired together
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

## 🗄️ Database Schema (End of Phase 1)

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

## 🔄 Request Flow

### POST `/urls`

1. Validate input
2. Generate Base62 short code (or use custom alias)
3. Check uniqueness
4. Persist to Postgres
5. Return:
   - `shortCode`
   - Full short URL

### GET `/{shortCode}`

1. Query Postgres by `short_code`
2. If not found → **404 Not Found**
3. If expired → **410 Gone**
4. Insert row into `link_clicks` (synchronous for now)
5. Return **302 Found** with `Location` header

### GET `/urls/{shortCode}/analytics`

1. Query Postgres
2. Execute:
   ```sql
   SELECT COUNT(*)
   FROM link_clicks
   WHERE link_id = ?;
   ```
3. Return JSON:

```json
{
  "totalClicks": 125,
  "createdAt": "2026-06-04T10:30:00Z",
  "lastAccessed": "2026-06-04T12:15:00Z"
}
```

---

## ✅ Exit Criteria

- [ ] `POST /urls` creates a link and returns short code
- [ ] `GET /{shortCode}` redirects correctly in browser
- [ ] Custom alias works
- [ ] Duplicate alias returns **409 Conflict**
- [ ] Expired links return **410 Gone**
- [ ] Analytics endpoint returns real click data
- [ ] Flyway migrations run automatically on startup
- [ ] Everything runs through `docker-compose up`
- [ ] Swagger UI accessible at `/swagger-ui.html`
