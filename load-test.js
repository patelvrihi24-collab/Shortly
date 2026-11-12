import http from 'k6/http';                        // k6's HTTP client
import { check } from 'k6';                        // lets us assert on responses

const BASE = __ENV.BASE_URL || 'http://localhost:8080';   // __ENV reads the -e flag we pass on the command line

export const options = {
    vus: 50,                                         // 50 "virtual users" hammering the endpoint in parallel
    duration: '30s',
    thresholds: {                                    // the run is marked FAILED if these are violated
        http_req_failed: ['rate<0.01'],                // under 1% errors
        http_req_duration: ['p(95)<250'],              // 95% of requests faster than 250 ms
    },
};

// setup() runs ONCE before the load starts. We create a user and one link, so the test only measures redirects.
export function setup() {
    const json = { headers: { 'Content-Type': 'application/json' } };
    const email = `load-${Date.now()}@example.com`;                      // unique per run
    const reg = http.post(`${BASE}/api/v1/auth/register`,
        JSON.stringify({ email: email, password: 'SuperSecret123' }), json);
    check(reg, { 'registered (201)': (r) => r.status === 201 });

    const created = http.post(`${BASE}/api/v1/urls`,
        JSON.stringify({ url: 'https://example.com/load-test' }),
        { headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${reg.json('token')}` } });
    check(created, { 'link created (201)': (r) => r.status === 201 });

    return { code: created.json('shortCode') };      // handed to every virtual user below
}

// This function runs in a loop for each virtual user until the duration ends.
export default function (data) {
    const res = http.get(`${BASE}/${data.code}`, { redirects: 0 });     // redirects: 0 = measure OUR 302, don't follow it
    check(res, { 'is 302': (r) => r.status === 302 });
}