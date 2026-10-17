import http from 'k6/http';
import exec from 'k6/execution';
import { Counter } from 'k6/metrics';
import { config, intEnv, runIdEnv, stepEnv } from './lib/config.js';
import { loadCustomerIds } from './lib/customers.js';
import { buildEvent, isResend, transactionId } from './lib/events.js';

const REQUEST = 'POST /v1/events';
const RUN_ID = runIdEnv('RUN_ID');
const STEP = stepEnv('STEP');
const RATE = intEnv('RATE');
const DURATION_SECONDS = intEnv('DURATION_SECONDS');
const PRE_ALLOCATED_VUS = Math.max(50, Math.ceil(RATE * 0.15));
const MAX_VUS = Math.max(500, RATE);
const DUPLICATE_PERIOD = 100;

const started = new Counter('events_started');
const stored = new Counter('events_stored');
const duplicate = new Counter('events_duplicate');
const failed = new Counter('events_failed');

const SUBMETRICS_IN_SUMMARY = {
  [`http_req_duration{name:${REQUEST}}`]: ['max>=0'],
  [`http_req_failed{name:${REQUEST}}`]: ['rate>=0'],
};

export const options = {
  summaryTrendStats: ['med', 'p(99)'],
  scenarios: {
    ingest: {
      executor: 'constant-arrival-rate',
      rate: RATE,
      timeUnit: '1s',
      duration: `${DURATION_SECONDS}s`,
      preAllocatedVUs: PRE_ALLOCATED_VUS,
      maxVUs: MAX_VUS,
      gracefulStop: '60s',
    },
  },
  thresholds: SUBMETRICS_IN_SUMMARY,
};

export function setup() {
  return { customerIds: loadCustomerIds(), startedAt: new Date().toISOString() };
}

export default function (data) {
  started.add(1);
  const sequence = exec.scenario.iterationInTest;
  const resend = isResend(sequence, DUPLICATE_PERIOD);
  const eventSequence = resend ? sequence - 1 : sequence;
  const id = transactionId(RUN_ID, STEP, eventSequence);
  const res = http.post(`${config.baseUrl}/v1/events`, JSON.stringify(buildEvent(data.customerIds, id, eventSequence)), {
    headers: config.headers,
    tags: { name: REQUEST },
    timeout: '30s',
  });
  if (res.status !== 200) {
    failed.add(1);
    if (!resend) {
      console.log(`FAILED_ID ${id} status=${res.status} error_code=${res.error_code}`);
    }
    return;
  }
  (res.json('duplicate') ? duplicate : stored).add(1);
}

export function handleSummary(data) {
  const metadata = {
    step: STEP,
    rate: RATE,
    max_vus: MAX_VUS,
    duplicate_period: DUPLICATE_PERIOD,
    organization_id: config.organizationId,
    started_at: data.setup_data?.startedAt ?? null,
    ended_at: new Date().toISOString(),
  };
  return {
    [`${config.resultDir}/${RUN_ID}-ingest-${STEP}.json`]: JSON.stringify({ run_id: RUN_ID, metadata, k6: data }, null, 2),
  };
}
