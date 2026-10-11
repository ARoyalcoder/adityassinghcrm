// k6 smoke/load test for the public API surface.
//
//   k6 run perf/k6/smoke.js                              # against http://localhost:8080
//   k6 run -e BASE_URL=http://localhost:8088 perf/k6/smoke.js   # through Nginx (docker compose --profile app)
//
// It doubles as a contract check: the thresholds fail the run if latency or error rate regress.
import http from 'k6/http'
import { check, sleep } from 'k6'

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080'

export const options = {
  stages: [
    { duration: '15s', target: 10 },
    { duration: '30s', target: 10 },
    { duration: '10s', target: 0 },
  ],
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<300'],
    checks: ['rate>0.99'],
  },
}

export default function () {
  const info = http.get(`${BASE_URL}/api/v1/system/info`)
  check(info, {
    'system info: 200': (r) => r.status === 200,
    'system info: has request id': (r) => !!r.headers['X-Request-Id'],
  })

  const verticals = http.get(`${BASE_URL}/api/v1/service-verticals`)
  check(verticals, {
    'verticals: 200': (r) => r.status === 200,
    'verticals: exactly six': (r) => r.json().length === 6,
  })

  // An expected 401 must not count against http_req_failed.
  const secured = http.get(`${BASE_URL}/api/v1/not-public`, {
    responseCallback: http.expectedStatuses(401),
  })
  check(secured, {
    'secured: 401 problem+json': (r) =>
      r.status === 401 && r.json('code') === 'UNAUTHENTICATED',
  })

  sleep(1)
}
