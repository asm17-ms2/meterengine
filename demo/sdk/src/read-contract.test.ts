import assert from "node:assert/strict";
import { test } from "node:test";
import { MeterEngineClient } from "./index.ts";

const API_KEY = "me_test_0123456789abcdefghijABCDEFGHIJKL";
const CUSTOMER_ID = "4a853fc7-e36b-44b1-abd5-7b6991df2f32";

test("교차 실행하는 test와 live 클라이언트가 수집부터 청구와 카드 조회까지 자신의 환경을 유지한다", async () => {
  const calls: { key: string | null; environment: string | null; pathname: string }[] = [];
  const fetchImpl: typeof fetch = async (input, init) => {
    const headers = new Headers(init?.headers);
    calls.push({ key: headers.get("Authorization"), environment: headers.get("X-MeterEngine-Environment"), pathname: new URL(String(input)).pathname });
    return Response.json({});
  };
  const clients = (["test", "live"] as const).map(environment => new MeterEngineClient({ baseUrl: "http://localhost:8080", apiKey: `key-${environment}`, environment, fetch: fetchImpl }));
  await Promise.all(clients.map(async client => {
    await client.createCustomer({ name: "환경별 고객", external_id: "same-external" });
    await client.ingestEvent({ customer_id: CUSTOMER_ID, transaction_id: "same-transaction", type: "usage", timestamp: "2026-10-01T00:00:00Z", properties: {} });
    await client.aggregateBillableMetricUsages({ month: "2026-10" });
    await client.listBillableMetricPrices({ month: "2026-10" });
    await client.previewDraftInvoice({ month: "2026-10" });
    await client.listInvoices({ month: "2026-10" });
    await client.listInvoicePayments({ month: "2026-10" });
    await client.getPaymentMethod(CUSTOMER_ID);
    await client.createPaymentMethodRegistration(CUSTOMER_ID);
    await client.listWebhookEndpoints();
  }));
  assert.equal(calls.length, 20);
  for (const call of calls) assert.equal(call.key, `Bearer key-${call.environment}`);
  await new MeterEngineClient({ baseUrl: "http://localhost:8080", apiKey: API_KEY, fetch: fetchImpl }).listCustomers();
  assert.equal(calls.at(-1)!.environment, null);
});

test("고객과 청구 조회는 같은 Bearer 키를 헤더에만 보내고 고객과 월 필터를 보존한다", async () => {
  const calls: Array<{ url: URL; headers: Headers }> = [];
  const client = new MeterEngineClient({ baseUrl: "http://localhost:8080", apiKey: API_KEY, fetch: async (input, init) => {
    calls.push({ url: new URL(String(input)), headers: new Headers(init?.headers) });
    return new Response("{}", { status: 200 });
  } });
  await client.getCustomer(CUSTOMER_ID);
  await client.aggregateBillableMetricUsages({ month: "2026-10" });
  await client.listInvoices({ month: "2026-10", customer_id: CUSTOMER_ID });
  await client.listInvoicePayments({ month: "2026-10", customer_id: CUSTOMER_ID });
  await client.getPaymentMethod(CUSTOMER_ID);
  assert.equal(calls[0]?.url.pathname, `/v1/customers/${CUSTOMER_ID}`);
  assert.equal(calls[2]?.url.searchParams.get("customer_id"), CUSTOMER_ID);
  assert.equal(calls[3]?.url.searchParams.get("month"), "2026-10");
  assert.equal(calls[4]?.url.pathname, `/v1/customers/${CUSTOMER_ID}/payment-method`);
  for (const call of calls) {
    assert.equal(call.headers.get("Authorization"), `Bearer ${API_KEY}`);
    assert.equal(call.url.href.includes(API_KEY), false);
  }
});

test("서버 응답이 유실된 카드 등록 링크를 자동으로 다시 만들지 않는다", async () => {
  let calls = 0;
  const client = new MeterEngineClient({ baseUrl: "http://localhost:8080", apiKey: API_KEY, maxRetries: 3, sleep: async () => {}, fetch: async () => {
    calls += 1;
    throw new TypeError("connection lost");
  } });
  await assert.rejects(client.createPaymentMethodRegistration(CUSTOMER_ID));
  assert.equal(calls, 1);
});

test("단가와 요금제와 외부 고객 필터를 각 API 계약의 query로 보낸다", async () => {
  const urls: URL[] = [];
  const client = new MeterEngineClient({ baseUrl: "http://localhost:8080", apiKey: API_KEY, fetch: async (input) => {
    urls.push(new URL(String(input)));
    return new Response("{}", { status: 200 });
  } });
  await client.listCustomers({ external_id: "customer/a b" });
  await client.listBillableMetricPrices({ month: "2026-10", customer_id: CUSTOMER_ID });
  await client.listPricePlans({ month: "2026-11" });
  await client.getUnbilledEvents();
  await client.listWebhookDeliveries("endpoint", { status: "failed", page: 2, size: 10 });
  assert.equal(urls[0]?.searchParams.get("external_id"), "customer/a b");
  assert.equal(urls[1]?.pathname, "/v1/billable-metric-prices");
  assert.equal(urls[1]?.searchParams.get("customer_id"), CUSTOMER_ID);
  assert.equal(urls[2]?.searchParams.get("month"), "2026-11");
  assert.equal(urls[3]?.pathname, "/v1/unbilled-events");
  assert.equal(urls[4]?.searchParams.get("status"), "failed");
});

test("이벤트 기간과 한글 검색과 JSON 속성과 정렬을 보존하고 nullable 월을 반환한다", async () => {
  const urls: URL[] = [];
  const from = "2026-10-01T09:00:00+09:00";
  const to = "2026-11-01T00:00:00+09:00";
  const response = { events: [], month: null, from, to, page: 2, size: 50, total: 0 };
  const client = new MeterEngineClient({ baseUrl: "http://localhost:8080", apiKey: API_KEY, fetch: async input => {
    urls.push(new URL(String(input)));
    return Response.json(response);
  } });
  const properties = { model: "서울/모델 +", tokens: 12, paid: true };
  const result = await client.listEvents({ from, to, q: "고객/정정 +", properties, sort: "received_at", order: "asc", page: 2, size: 50, customer_id: CUSTOMER_ID, type: "사용량/입력" });
  assert.deepEqual(result, response);
  assert.deepEqual(Object.fromEntries(urls[0]!.searchParams), {
    from, to, q: "고객/정정 +", properties: JSON.stringify(properties), sort: "received_at", order: "asc", page: "2", size: "50", customer_id: CUSTOMER_ID, type: "사용량/입력",
  });
});

test("실제 유형 조회는 기간과 고객과 검색을 보내고 JSON 문자열 속성을 다시 직렬화하지 않는다", async () => {
  const urls: URL[] = [];
  const client = new MeterEngineClient({ baseUrl: "http://localhost:8080", apiKey: API_KEY, fetch: async input => {
    urls.push(new URL(String(input)));
    return Response.json({ types: ["미터 없는/유형", "입력"] });
  } });
  const properties = '{"수량":9007199254740993,"model":"서울/모델"}';
  const result = await client.listEventTypes({ from: "2026-10-01", to: "2026-10-31", customer_id: CUSTOMER_ID, q: "서울/고객", properties });
  assert.deepEqual(result.types, ["미터 없는/유형", "입력"]);
  assert.equal(urls[0]?.pathname, "/v1/events/types");
  assert.deepEqual(Object.fromEntries(urls[0]!.searchParams), { from: "2026-10-01", to: "2026-10-31", customer_id: CUSTOMER_ID, q: "서울/고객", properties });
});

test("이벤트 단건 lookup은 슬래시와 한글과 URL 예약 문자를 경로 대신 query로 보낸다", async () => {
  const transactionId = "정정/서울?값=1#100% +a";
  const calls: Array<{ url: URL; headers: Headers }> = [];
  const response = { transaction_id: transactionId, customer_id: CUSTOMER_ID, customer_name: "서울", type: "정정", occurred_at: "2026-10-01T00:00:00+09:00", received_at: "2026-10-01T00:00:01+09:00", properties: { quantity: -1 } };
  const client = new MeterEngineClient({ baseUrl: "http://localhost:8080", apiKey: API_KEY, fetch: async (input, init) => {
    calls.push({ url: new URL(String(input)), headers: new Headers(init?.headers) });
    return Response.json(response);
  } });
  assert.deepEqual(await client.getEvent(transactionId), response);
  assert.equal(calls[0]?.url.pathname, "/v1/events/lookup");
  assert.equal(calls[0]?.url.searchParams.get("transaction_id"), transactionId);
  assert.equal(calls[0]?.url.hash, "");
  assert.equal(calls[0]?.headers.get("Authorization"), `Bearer ${API_KEY}`);
  assert.equal(calls[0]?.url.href.includes(API_KEY), false);
});

test("보관 상태 조회는 지원 API에만 보내고 보관 시각과 효력 종료 월을 반환한다", async () => {
  const urls: URL[] = [];
  const archivedAt = "2026-10-27T00:00:00+09:00";
  const client = new MeterEngineClient({ baseUrl: "http://localhost:8080", apiKey: API_KEY, fetch: async input => {
    const url = new URL(String(input));
    urls.push(url);
    const bodies: Record<string, unknown> = {
      "/v1/customers": { customers: [{ id: CUSTOMER_ID, archived_at: archivedAt }] },
      "/v1/payment-methods": { payment_methods: [{ customer_id: CUSTOMER_ID, archived_at: archivedAt }] },
      "/v1/price-plans": { price_plans: [{ archived_at: archivedAt, archived_from: "2026-11" }] },
      "/v1/billable-metric-prices": { billable_metric_prices: [{ price_rates: [{ unit_price: null, archived_at: archivedAt, archived_from: "2026-11" }] }] },
    };
    return Response.json(bodies[url.pathname]);
  } });
  const customers = await client.listCustomers({ external_id: "고객/서울", status: "archived" });
  const methods = await client.listPaymentMethods({ status: "all" });
  const plans = await client.listPricePlans({ month: "2026-10", status: "archived" });
  const prices = await client.listBillableMetricPrices({ month: "2026-10" });
  assert.equal(urls[0]?.searchParams.get("status"), "archived");
  assert.equal(urls[1]?.searchParams.get("status"), "all");
  assert.equal(urls[2]?.searchParams.get("status"), "archived");
  assert.equal(urls[3]?.searchParams.has("status"), false);
  assert.equal(customers.customers[0]?.archived_at, archivedAt);
  assert.equal(methods.payment_methods[0]?.archived_at, archivedAt);
  assert.equal(plans.price_plans[0]?.archived_from, "2026-11");
  assert.equal(prices.billable_metric_prices[0]?.price_rates[0]?.unit_price, null);
  assert.equal(prices.billable_metric_prices[0]?.price_rates[0]?.archived_from, "2026-11");
});

test("고객 검색은 외부 ID와 환경을 보내고 반환 버전을 유지한다", async () => {
  const calls: Array<{ url: URL; headers: Headers }> = [];
  const client = new MeterEngineClient({ baseUrl: "http://localhost:8080", apiKey: API_KEY, environment: "test", organizationId: CUSTOMER_ID, fetch: async (input, init) => {
    calls.push({ url: new URL(String(input)), headers: new Headers(init?.headers) });
    return Response.json({ items: [{ id: CUSTOMER_ID, name: "서울", billing_email: null, created_at: "2026-10-01T00:00:00Z", version: 2 }], total: 1, total_unfiltered: 1, page: 1, size: 25, summary: {} });
  } });
  const result = await client.searchCustomers({ external_id: "고객/서울 +1", page: 1 });
  const version: number = result.items[0]!.version;
  assert.equal(version, 2);
  assert.equal(calls[0]?.url.pathname, "/v1/customers/search");
  assert.equal(calls[0]?.url.searchParams.get("external_id"), "고객/서울 +1");
  assert.equal(calls[0]?.headers.get("X-MeterEngine-Environment"), "test");
  assert.equal(calls[0]?.headers.get("X-MeterEngine-Organization-Id"), CUSTOMER_ID);
});

test("CSV 내보내기는 검색 조건과 텍스트 응답을 유지한다", async () => {
  let headers: Headers | undefined;
  let url: URL | undefined;
  const csv = "이름,외부 ID\r\n서울,customer-a\r\n";
  const client = new MeterEngineClient({ baseUrl: "http://localhost:8080", apiKey: API_KEY, fetch: async (input, init) => {
    url = new URL(String(input)); headers = new Headers(init?.headers);
    return new Response(csv, { headers: { "Content-Type": "text/csv; charset=utf-8" } });
  } });
  assert.equal(await client.exportCsv("customers", { external_id: "customer-a", q: "서울" }), csv);
  assert.equal(url?.pathname, "/v1/customers/export");
  assert.equal(url?.searchParams.get("external_id"), "customer-a");
  assert.equal(headers?.get("Accept"), "text/csv");
});

test("이벤트 진단과 수집 거절은 영부터 시작하는 페이지와 사유 필터를 구분한다", async () => {
  const urls: URL[] = [];
  const client = new MeterEngineClient({ baseUrl: "http://localhost:8080", apiKey: API_KEY, fetch: async input => {
    const url = new URL(String(input)); urls.push(url);
    return Response.json(url.pathname.includes("event-issues") ? { issues: [], page: 0, total: 0 } : { rejections: [], page: 0, total: 0 });
  } });
  const issues = await client.searchEventIssues({ month: "2026-10", reason: "unpriced_usage", page: 0 });
  const rejections = await client.searchEventRejections({ month: "2026-10", error_code: "customer_not_found", page: 0 });
  assert.equal(issues.page, 0);
  assert.equal(rejections.page, 0);
  assert.equal(urls[0]?.pathname, "/v1/event-issues/search");
  assert.equal(urls[0]?.searchParams.get("reason"), "unpriced_usage");
  assert.equal(urls[0]?.searchParams.get("page"), "0");
  assert.equal(urls[1]?.pathname, "/v1/event-rejections/search");
  assert.equal(urls[1]?.searchParams.get("error_code"), "customer_not_found");
  assert.equal(urls[1]?.searchParams.get("reason"), null);
});

test("이벤트 CSV는 목록의 기간과 속성과 원문 선택을 환경 헤더와 함께 보낸다", async () => {
  const calls: Array<{ url: URL; headers: Headers }> = [];
  const csv = '"transaction_id","properties"\r\n"서울/1","{}"\r\n';
  const client = new MeterEngineClient({ baseUrl: "http://localhost:8080", apiKey: API_KEY, environment: "test", organizationId: CUSTOMER_ID, fetch: async (input, init) => {
    calls.push({ url: new URL(String(input)), headers: new Headers(init?.headers) });
    return new Response(csv, { headers: { "Content-Type": "text/csv;charset=UTF-8" } });
  } });
  const params = { from: "2026-10-01T09:00:00+09:00", to: "2026-10-02T09:00:00+09:00", customer_id: CUSTOMER_ID, type: "사용/입력", q: "서울 +1", properties: { model: "한국/모델" }, sort: "transaction_id" as const, order: "asc" as const, page: 1, size: 1, include_properties: true };
  assert.equal(await client.exportCsv("events", params), csv);
  assert.equal(calls[0]?.url.pathname, "/v1/events/export");
  assert.deepEqual(Object.fromEntries(calls[0]!.url.searchParams), { ...params, page: "1", size: "1", include_properties: "true", properties: JSON.stringify(params.properties) });
  assert.equal(calls[0]?.headers.get("Accept"), "text/csv");
  assert.equal(calls[0]?.headers.get("X-MeterEngine-Environment"), "test");
  assert.equal(calls[0]?.headers.get("X-MeterEngine-Organization-Id"), CUSTOMER_ID);
  assert.equal(calls[0]?.url.href.includes(API_KEY), false);
});

test("이벤트 CSV의 JSON 문자열 필터를 다시 직렬화하거나 숫자를 변환하지 않는다", async () => {
  let url: URL | undefined;
  const client = new MeterEngineClient({ baseUrl: "http://localhost:8080", apiKey: API_KEY, fetch: async input => { url = new URL(String(input)); return new Response('"transaction_id"\r\n'); } });
  const properties = '{"tokens":9007199254740993,"decimal":0.1234567890123456789}';
  await client.exportCsv("events", { month: "2026-10", properties, include_properties: false });
  assert.equal(url?.searchParams.get("properties"), properties);
  assert.equal(url?.searchParams.get("include_properties"), "false");
});

test("이벤트가_있는_월도_기존_월_조회와_같은_환경_계약으로_반환한다", async () => {
  const calls: Array<{ url: URL; headers: Headers }> = [];
  const months = ["2026-11", "2026-10"];
  const client = new MeterEngineClient({ baseUrl: "http://localhost:8080", apiKey: API_KEY, environment: "test", fetch: async (input, init) => {
    calls.push({ url: new URL(String(input)), headers: new Headers(init?.headers) });
    return Response.json({ months });
  } });
  for (const resource of ["events", "usage", "invoices", "billable-metric-prices"] as const) {
    assert.deepEqual((await client.getAvailableMonths(resource)).months, months);
    assert.equal(calls.at(-1)?.url.pathname, `/v1/${resource}/months`);
    assert.equal(calls.at(-1)?.headers.get("X-MeterEngine-Environment"), "test");
  }
});


test("사용량 검색은 페이지와 무관한 전체 라인 수를 숫자로 반환한다", async () => {
  const client = new MeterEngineClient({ baseUrl: "http://localhost:8080", apiKey: API_KEY, fetch: async input => {
    const url = new URL(String(input));
    assert.equal(url.pathname, "/v1/usage/search");
    assert.equal(url.searchParams.get("page"), "2");
    assert.equal(url.searchParams.get("customer_id"), CUSTOMER_ID);
    return Response.json({ items: [], total: 2, total_unfiltered: 2, page: 2, size: 1, summary: { month: "2026-10", customer_count: 2, metric_count: 2, line_count: 4, nonzero_customer_count: 1 } });
  } });
  const result = await client.searchUsage({ month: "2026-10", customer_id: CUSTOMER_ID, page: 2, size: 1 });
  const totalLines: number = result.summary.line_count;
  assert.equal(totalLines, 4);
  assert.equal(result.items.length, 0);
});

test("웹훅 엔드포인트 목록은 보관 상태를 query로 보내고 archived_at을 돌려준다", async () => {
  const urls: string[] = [];
  const client = new MeterEngineClient({ baseUrl: "http://localhost:8080", apiKey: API_KEY, fetch: async (input) => {
    urls.push(String(input));
    return new Response(JSON.stringify({ endpoints: [{ id: "c0b1a2d3-0000-4000-8000-000000000001", url: "https://example.com/hook", event_types: ["invoice.finalized"], enabled: false, description: null, created_at: "2026-10-01T00:00:00Z", archived_at: "2026-10-02T00:00:00Z" }] }), { status: 200 });
  } });
  const response = await client.listWebhookEndpoints({ status: "archived" });
  assert.equal(new URL(urls[0]).pathname, "/v1/webhook-endpoints");
  assert.equal(new URL(urls[0]).searchParams.get("status"), "archived");
  assert.equal(response.endpoints[0].archived_at, "2026-10-02T00:00:00Z");
});
