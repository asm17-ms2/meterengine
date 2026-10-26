const DEFAULT_BASE_URL = 'https://test-slo.meterengine.com';
const RUN_ID_PATTERN = /^[A-Za-z0-9_-]{1,64}$/;

function invalid(name, value) {
  throw new Error(`${name} 값이 잘못됐다: ${value}`);
}

function requiredSecretEnv(name) {
  if (!__ENV[name]) {
    throw new Error(`${name} 환경변수가 없다. export ${name} 뒤 실행한다`);
  }
  return __ENV[name];
}

export function intEnv(name, fallback) {
  if (__ENV[name] === undefined) {
    return fallback;
  }
  const value = Number(__ENV[name]);
  return Number.isInteger(value) && value > 0 ? value : invalid(name, __ENV[name]);
}

export function runIdEnv(name) {
  const value = __ENV[name];
  return RUN_ID_PATTERN.test(value || '') ? value : invalid(name, value);
}

const API_KEY = requiredSecretEnv('API_KEY');

export const config = {
  baseUrl: (__ENV.BASE_URL || DEFAULT_BASE_URL).replace(/\/$/, ''),
  headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${API_KEY}` },
};
