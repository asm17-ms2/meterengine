import http from 'k6/http';
import { check } from 'k6';
import { config } from './lib/config.js';

export const options = {
  discardResponseBodies: true,
  scenarios: {
    smoke: {
      executor: 'constant-arrival-rate',
      rate: 5,
      timeUnit: '1s',
      duration: __ENV.DURATION || '2m',
      preAllocatedVUs: 5,
    },
  },
  thresholds: {
    checks: ['rate==1'],
    dropped_iterations: ['count==0'],
  },
};

export default function () {
  const res = http.get(`${config.baseUrl}/v1/billable-metrics`, { headers: config.headers });
  check(res, { 'status 200': (r) => r.status === 200 });
}
