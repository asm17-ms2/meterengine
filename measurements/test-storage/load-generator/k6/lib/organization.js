import http from 'k6/http';
import { config } from './config.js';

export function loadOrganizationId() {
  const res = http.get(`${config.baseUrl}/v1/organization`, { headers: config.headers });
  if (res.status !== 200) {
    throw new Error(`도입사 조회 실패: ${res.status} ${res.body}`);
  }
  return res.json('id');
}
