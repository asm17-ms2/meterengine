import http from 'k6/http';
import { config } from './config.js';

const NAME_PREFIX = 'k6-customer-';
const CUSTOMER_COUNT = 100;

function requireOk(res, action) {
  if (res.status !== 200 && res.status !== 201) {
    throw new Error(`${action} 실패: ${res.status} ${res.body}`);
  }
  return res;
}

function customerIdsByName() {
  const res = requireOk(http.get(`${config.baseUrl}/v1/customers`, { headers: config.headers, responseType: 'text' }), '고객 목록 조회');
  return Object.fromEntries(res.json('customers').map((customer) => [customer.name, customer.id]));
}

function createCustomer(name) {
  const res = http.post(`${config.baseUrl}/v1/customers`, JSON.stringify({ name }), { headers: config.headers, responseType: 'text' });
  return requireOk(res, `고객 ${name} 생성`).json('id');
}

export function loadCustomerIds() {
  const existing = customerIdsByName();
  return Array.from({ length: CUSTOMER_COUNT }, (_, index) => {
    const name = `${NAME_PREFIX}${String(index + 1).padStart(3, '0')}`;
    return existing[name] || createCustomer(name);
  });
}
