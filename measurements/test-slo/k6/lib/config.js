const ORGANIZATION_ID = 'd7cee55d-8c82-4afc-b996-6749d8b26a4e';
const DEFAULT_BASE_URL = 'https://test-slo.meterengine.com';
const RUN_ID_PATTERN = /^[A-Za-z0-9_-]{1,64}$/;

function invalid(name, value) {
  throw new Error(`${name} 값이 잘못됐다: ${value}`);
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

export const config = {
  baseUrl: (__ENV.BASE_URL || DEFAULT_BASE_URL).replace(/\/$/, ''),
  organizationId: ORGANIZATION_ID,
  headers: { 'Content-Type': 'application/json', 'X-Organization-Id': ORGANIZATION_ID },
};
