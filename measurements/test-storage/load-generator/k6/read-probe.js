import http from 'k6/http';
import exec from 'k6/execution';
import { sleep } from 'k6';
import { config, intEnv, runIdEnv } from './lib/config.js';
import { loadCustomerIds } from './lib/customers.js';

// 수집 부하와 따로 돌려, 같은 DB를 쓰는 조회가 그동안 얼마나 느려지는지 본다.
const RUN_ID = runIdEnv('RUN_ID');
const RATE = intEnv('RATE');
const DURATION_SECONDS = intEnv('DURATION_SECONDS');
const TIMEOUT_SECONDS = intEnv('TIMEOUT_SECONDS');
const ANCHOR_ID = `${RUN_ID}-read-anchor`;
const ANCHOR_WAIT_SECONDS = 60;
const PAGE_SIZE = 20;
const MIN_VUS = 20;
const OK = 200;
const KST_OFFSET_MS = 9 * 60 * 60 * 1000;
const KNOWN_READS = ['lookup', 'list'];
// 목록 조회는 그 도입사의 이벤트를 다 훑어서 행이 많으면 그것만으로 DB를 채운다. 그래서 따로 켠다.
const READS = (__ENV.READS || 'lookup').split(',');
if (READS.some((read) => !KNOWN_READS.includes(read))) {
  throw new Error(`READS 값이 잘못됐다: ${__ENV.READS}`);
}

export const options = {
  summaryTrendStats: ['med', 'p(99)', 'max'],
  scenarios: {
    read: {
      executor: 'constant-arrival-rate',
      rate: RATE,
      timeUnit: '1s',
      duration: `${DURATION_SECONDS}s`,
      preAllocatedVUs: Math.max(MIN_VUS, RATE * TIMEOUT_SECONDS),
      maxVUs: Math.max(MIN_VUS, RATE * TIMEOUT_SECONDS),
      gracefulStop: `${TIMEOUT_SECONDS}s`,
    },
  },
};

function lookupUrl() {
  return `${config.baseUrl}/v1/events/lookup?transaction_id=${ANCHOR_ID}`;
}

// 사용량 조회와 같은 KST 기준의 달이다.
function currentMonth() {
  return new Date(Date.now() + KST_OFFSET_MS).toISOString().slice(0, 7);
}

function storeAnchor(customerId) {
  const event = { transaction_id: ANCHOR_ID, customer_id: customerId, type: 'chat_completion', properties: { token: 1 }, timestamp: new Date().toISOString() };
  const res = http.post(`${config.baseUrl}/v1/events`, JSON.stringify(event), { headers: config.headers });
  if (res.status !== OK && res.status !== 202) {
    throw new Error(`기준 이벤트 접수 실패: ${res.status} ${res.body}`);
  }
}

// 카프카 경로에서는 컨슈머가 저장한 뒤에야 찾을 수 있다. 컨슈머가 도는 동안 시작해야 한다.
function waitUntilAnchorStored() {
  for (let waited = 0; waited < ANCHOR_WAIT_SECONDS; waited += 1) {
    if (http.get(lookupUrl(), { headers: config.headers }).status === OK) {
      return;
    }
    sleep(1);
  }
  throw new Error(`기준 이벤트가 ${ANCHOR_WAIT_SECONDS}초 안에 저장되지 않았다. 컨슈머가 도는지 본다`);
}

export function setup() {
  const customerId = loadCustomerIds(config.headers)[0];
  storeAnchor(customerId);
  waitUntilAnchorStored();
  return { customerId, month: currentMonth(), startedAt: new Date().toISOString() };
}

function url(read, data) {
  if (read === 'lookup') {
    return lookupUrl();
  }
  return `${config.baseUrl}/v1/events?customer_id=${data.customerId}&month=${data.month}&size=${PAGE_SIZE}`;
}

export default function (data) {
  const read = READS[exec.scenario.iterationInTest % READS.length];
  const sentMs = Date.now();
  const res = http.get(url(read, data), { headers: config.headers, tags: { name: `read ${read}` }, timeout: `${TIMEOUT_SECONDS}s` });
  console.log(`READ ${read} ${sentMs} ${Date.now() - sentMs} ${res.status}`);
}

export function handleSummary(data) {
  const metadata = { rate: RATE, timeout_seconds: TIMEOUT_SECONDS, started_at: data.setup_data?.startedAt ?? null, ended_at: new Date().toISOString() };
  return {
    [`${config.resultDir}/${RUN_ID}-read-probe.json`]: JSON.stringify({ run_id: RUN_ID, metadata, k6: data }, null, 2),
  };
}
