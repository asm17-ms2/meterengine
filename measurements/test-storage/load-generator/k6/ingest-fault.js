import http from 'k6/http';
import exec from 'k6/execution';
import { Counter } from 'k6/metrics';
import { config, intEnv, runIdEnv, stepEnv } from './lib/config.js';
import { loadCustomerIds } from './lib/customers.js';
import { buildEvent, isResend, transactionId } from './lib/events.js';
import { loadOrganizationId } from './lib/organization.js';

const REQUEST = 'POST /v1/events';
const RUN_ID = runIdEnv('RUN_ID');
const STEP = stepEnv('STEP');
const RATE = intEnv('RATE');
const DURATION_SECONDS = intEnv('DURATION_SECONDS');
const TIMEOUT_SECONDS = intEnv('TIMEOUT_SECONDS');
const GRACEFUL_STOP_EXTRA_SECONDS = 30;
const MIN_VUS = 50;
const MAX_VUS = Math.max(MIN_VUS, RATE);
const DUPLICATE_PERIOD = 100;
const NO_VALUE = '-';

const started = new Counter('events_started');
const stored = new Counter('events_stored');
const duplicate = new Counter('events_duplicate');
const failed = new Counter('events_failed');

export const options = {
  summaryTrendStats: ['med', 'p(99)', 'max'],
  scenarios: {
    ingest: {
      executor: 'constant-arrival-rate',
      rate: RATE,
      timeUnit: '1s',
      duration: `${DURATION_SECONDS}s`,
      preAllocatedVUs: MAX_VUS,
      maxVUs: MAX_VUS,
      gracefulStop: `${TIMEOUT_SECONDS + GRACEFUL_STOP_EXTRA_SECONDS}s`,
    },
  },
  thresholds: {
    [`http_req_duration{name:${REQUEST}}`]: ['max>=0'],
    [`http_req_failed{name:${REQUEST}}`]: ['rate>=0'],
  },
};

export function setup() {
  return { organizationId: loadOrganizationId(), customerIds: loadCustomerIds(), startedAt: new Date().toISOString() };
}

function duplicateFlag(res) {
  if (res.status !== 200) {
    return NO_VALUE;
  }
  return res.json('duplicate') ? 1 : 0;
}

export default function (data) {
  started.add(1);
  const sequence = exec.scenario.iterationInTest;
  const resend = isResend(sequence, DUPLICATE_PERIOD);
  const eventSequence = resend ? sequence - 1 : sequence;
  const id = transactionId(RUN_ID, STEP, eventSequence);
  const sentMs = Date.now();
  const res = http.post(`${config.baseUrl}/v1/events`, JSON.stringify(buildEvent(data.customerIds, id, eventSequence)), {
    headers: config.headers,
    tags: { name: REQUEST },
    timeout: `${TIMEOUT_SECONDS}s`,
  });
  const elapsedMs = Date.now() - sentMs;
  console.log(`REQ ${id} ${sentMs} ${elapsedMs} ${res.status} ${res.error_code} ${duplicateFlag(res)} ${resend ? 1 : 0}`);
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
    timeout_seconds: TIMEOUT_SECONDS,
    duplicate_period: DUPLICATE_PERIOD,
    organization_id: data.setup_data?.organizationId ?? null,
    started_at: data.setup_data?.startedAt ?? null,
    ended_at: new Date().toISOString(),
  };
  return {
    [`${config.resultDir}/${RUN_ID}-ingest-${STEP}.json`]: JSON.stringify({ run_id: RUN_ID, metadata, k6: data }, null, 2),
  };
}
