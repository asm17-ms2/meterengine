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
const PRE_ALLOCATED_VUS = Math.max(50, Math.ceil(RATE * 0.15));
const MAX_VUS = Math.max(500, RATE);
const DUPLICATE_PERIOD = 100;
// 순번을 그대로 나눠 조직을 고르면 10건마다 도는 이벤트 유형과 맞물려 조직마다 유형이 치우친다. 큰 홀수를 곱해 섞는다.
const SEQUENCE_MIX = 0x9e3779b1;
const UINT32_RANGE = 2 ** 32;
const STORED = 200;
const ACCEPTED = 202;

const started = new Counter('events_started');
const stored = new Counter('events_stored');
const duplicate = new Counter('events_duplicate');
// 카프카 경로의 202. 받아서 발행했고 저장과 중복 판정은 컨슈머가 뒤에서 한다.
const accepted = new Counter('events_accepted');
const failed = new Counter('events_failed');

const SUBMETRICS_IN_SUMMARY = {
  [`http_req_duration{name:${REQUEST}}`]: ['max>=0'],
  [`http_req_failed{name:${REQUEST}}`]: ['rate>=0'],
};

export const options = {
  summaryTrendStats: ['med', 'p(99)'],
  // 준비 단계가 조직마다 고객 100명을 하나씩 확인한다. 조직이 수십 곳이면 기본 60초를 넘는다.
  setupTimeout: '300s',
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

// 키는 결과 파일에 남지 않게 setup 값에 넣지 않고, 같은 순서의 config.headersByOrganization에서 꺼내 쓴다.
export function setup() {
  const organizations = config.headersByOrganization.map((headers) => ({
    id: loadOrganizationId(headers),
    customerIds: loadCustomerIds(headers),
  }));
  return { organizations, startedAt: new Date().toISOString() };
}

// 다시 보내는 건도 처음 보낸 조직으로 가도록 이벤트 순번으로 고른다.
function organizationIndex(eventSequence, organizationCount) {
  const mixed = Math.imul(eventSequence, SEQUENCE_MIX) >>> 0;
  return Math.floor((mixed / UINT32_RANGE) * organizationCount);
}

export default function (data) {
  started.add(1);
  const sequence = exec.scenario.iterationInTest;
  const resend = isResend(sequence, DUPLICATE_PERIOD);
  const eventSequence = resend ? sequence - 1 : sequence;
  const id = transactionId(RUN_ID, STEP, eventSequence);
  const index = organizationIndex(eventSequence, data.organizations.length);
  const event = buildEvent(data.organizations[index].customerIds, id, eventSequence);
  const res = http.post(`${config.baseUrl}/v1/events`, JSON.stringify(event), {
    headers: config.headersByOrganization[index],
    tags: { name: REQUEST },
    timeout: '30s',
  });
  if (res.status === ACCEPTED) {
    accepted.add(1);
    return;
  }
  if (res.status !== STORED) {
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
    organization_ids: (data.setup_data?.organizations ?? []).map((organization) => organization.id),
    started_at: data.setup_data?.startedAt ?? null,
    ended_at: new Date().toISOString(),
  };
  return {
    [`${config.resultDir}/${RUN_ID}-ingest-${STEP}.json`]: JSON.stringify({ run_id: RUN_ID, metadata, k6: data }, null, 2),
  };
}
