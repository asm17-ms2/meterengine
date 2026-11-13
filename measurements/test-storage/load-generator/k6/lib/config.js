const DEFAULT_BASE_URL = 'https://test-storage.meterengine.com';
const RUN_ID_PATTERN = /^[A-Za-z0-9_-]{1,64}$/;
const STEP_PATTERN = /^[A-Za-z0-9_]{1,32}$/;

function invalid(name, value) {
  throw new Error(`${name} 값이 잘못됐다: ${value}`);
}

// 조직 여러 곳을 섞어 보낼 때는 API_KEYS에 조직마다 키 하나씩 쉼표로 이어 넣는다.
function apiKeys() {
  const keys = (__ENV.API_KEYS || __ENV.API_KEY || '').split(',').filter((key) => key !== '');
  if (keys.length === 0) {
    throw new Error('API_KEY 환경변수가 없다. export API_KEY 뒤 실행한다');
  }
  return keys;
}

function headersOf(apiKey) {
  return { 'Content-Type': 'application/json', Authorization: `Bearer ${apiKey}` };
}

export function intEnv(name) {
  const value = Number(__ENV[name]);
  return Number.isInteger(value) && value > 0 ? value : invalid(name, __ENV[name]);
}

export function runIdEnv(name) {
  const value = __ENV[name];
  return RUN_ID_PATTERN.test(value || '') ? value : invalid(name, value);
}

export function stepEnv(name) {
  const value = __ENV[name];
  return STEP_PATTERN.test(value || '') ? value : invalid(name, value);
}

const HEADERS_BY_ORGANIZATION = apiKeys().map(headersOf);

export const config = {
  baseUrl: (__ENV.BASE_URL || DEFAULT_BASE_URL).replace(/\/$/, ''),
  resultDir: __ENV.RESULT_DIR || 'results',
  headers: HEADERS_BY_ORGANIZATION[0],
  headersByOrganization: HEADERS_BY_ORGANIZATION,
};
