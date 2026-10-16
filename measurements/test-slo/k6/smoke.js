import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'https://test-slo.meterengine.com';
const ORGANIZATION_ID = __ENV.ORGANIZATION_ID || 'd7cee55d-8c82-4afc-b996-6749d8b26a4e';

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
  const res = http.get(`${BASE_URL}/v1/billable-metrics`, {
    headers: { 'X-Organization-Id': ORGANIZATION_ID },
  });
  check(res, { 'status 200': (r) => r.status === 200 });
}
