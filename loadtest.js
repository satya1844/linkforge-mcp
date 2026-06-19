import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = 'http://localhost:8080';
const API_KEY = 'test-key-123';

// Short codes that exist in your DB
const EXISTING_CODES = ['G', 'H', 'github', 'me', 'awwwards', 'leetcode'];

const HEADERS = {
    'Content-Type': 'application/json',
    'X-API-Key': API_KEY,
};

export const options = {
    scenarios: {
        // Scenario 1: Cache warmup — hit same codes repeatedly
        warmup: {
            executor: 'constant-vus',
            vus: 50,
            duration: '15s',
            tags: { scenario: 'warmup' },
        },
        // Scenario 2: Sustained load — mixed traffic
        sustained: {
            executor: 'constant-vus',
            vus: 250,
            duration: '30s',
            startTime: '15s',
            tags: { scenario: 'sustained' },
        },
    },
    thresholds: {
        http_req_failed: ['rate<0.01'],       // <1% errors
        http_req_duration: ['p(99)<1000'],    // P99 under 1s
    },
};

export default function () {
    const scenario = Math.random();

    if (scenario < 0.70) {
        // 70% — redirect (hot path, this is what Redis caches)
        const code = EXISTING_CODES[Math.floor(Math.random() * EXISTING_CODES.length)];
        const res = http.get(`${BASE_URL}/urls/${code}`, {
            redirects: 0,  // don't follow redirect — measure only our server
        });
        check(res, {
            'redirect is 302': (r) => r.status === 302,
            'has location header': (r) => r.headers['Location'] !== undefined,
        });

    } else if (scenario < 0.85) {
        // 15% — analytics
        const code = EXISTING_CODES[Math.floor(Math.random() * EXISTING_CODES.length)];
        const res = http.get(`${BASE_URL}/urls/${code}/analytics`, {
            headers: HEADERS,
        });
        check(res, {
            'analytics is 200': (r) => r.status === 200,
            'has totalClicks': (r) => JSON.parse(r.body).totalClicks !== undefined,
        });

    } else if (scenario < 0.95) {
        // 10% — create new short link
        const res = http.post(`${BASE_URL}/urls`,
            JSON.stringify({
                originalUrl: `https://example.com/page-${Math.floor(Math.random() * 10000)}`,
            }),
            { headers: HEADERS }
        );
        check(res, {
            'create is 201': (r) => r.status === 201,
            'has shortCode': (r) => JSON.parse(r.body).shortCode !== undefined,
        });

    } else {
        // 5% — probe non-existent codes (Bloom filter test)
        const fakeCode = Math.random().toString(36).substring(2, 8);
        const res = http.get(`${BASE_URL}/urls/${fakeCode}`, {
            redirects: 0,
        });
        check(res, {
            'unknown code is 404': (r) => r.status === 404,
        });
    }

    sleep(0.1);
}