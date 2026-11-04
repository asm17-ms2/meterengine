import http from 'k6/http';
import exec from 'k6/execution';
import { config, intEnv, runIdEnv } from './lib/config.js';
import { loadCustomerIds } from './lib/customers.js';
import { buildEvent } from './lib/events.js';

const REQUEST = 'POST /v1/events';
const RUN_ID = runIdEnv('RUN_ID');
const RATE = intEnv('RATE', 100);
const DURATION_SECONDS = intEnv('DURATION_SECONDS', 600);
const TIMEOUT_SECONDS = intEnv('TIMEOUT_SECONDS', 5);
const VUS = RATE * TIMEOUT_SECONDS;
const GRACEFUL_STOP_EXTRA_SECONDS = 10;
const RESULT_DIR = `results/${RUN_ID}`;

export const options = {
  discardResponseBodies: true,
  scenarios: {
    ingest: {
      executor: 'constant-arrival-rate',
      rate: RATE,
      timeUnit: '1s',
      duration: `${DURATION_SECONDS}s`,
      preAllocatedVUs: VUS,
      maxVUs: VUS,
      gracefulStop: `${TIMEOUT_SECONDS + GRACEFUL_STOP_EXTRA_SECONDS}s`,
    },
  },
  thresholds: {
    dropped_iterations: ['count==0'],
  },
};

export function setup() {
  return { customerIds: loadCustomerIds() };
}

export default function (data) {
  const sequence = exec.scenario.iterationInTest;
  const sentMs = Date.now();
  const res = http.post(`${config.baseUrl}/v1/events`, JSON.stringify(buildEvent(data.customerIds, `${RUN_ID}-${sequence}`, sequence)), {
    headers: { ...config.headers, 'X-Request-Seq': String(sequence) },
    tags: { name: REQUEST },
    timeout: `${TIMEOUT_SECONDS}s`,
  });
  console.log(`REQ ${sequence} ${sentMs} ${Date.now() - sentMs} ${res.status} ${res.error_code}`);
}

export function handleSummary(data) {
  const count = (name) => data.metrics[name]?.values.count ?? 0;
  const summary = [
    `rate ${RATE}`,
    `vus ${VUS}`,
    `timeout_seconds ${TIMEOUT_SECONDS}`,
    `iterations ${count('iterations')}`,
    `dropped_iterations ${count('dropped_iterations')}`,
  ].join('\n');
  return {
    stdout: `${summary}\n`,
    [`${RESULT_DIR}/summary.txt`]: `${summary}\n`,
  };
}
