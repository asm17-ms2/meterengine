import assert from "node:assert/strict";
import { test } from "node:test";
import { MODELS, TRANSACTION_ID_PREFIX } from "../../src/lib/catalog.ts";
import { CUSTOMERS } from "./customers.ts";
import { checkFirstDay, checkUntil, eventsDigest, firstDayDigest, generateDay, generateEvents, kstDate, TEXT_MODELS, TOKEN_LIMITS, usageRows } from "./events.ts";
import { estimateCharges, lineAmount, priceRatePlans } from "./prices.ts";

const UNTIL = new Date("2026-11-11T05:00:00Z");
const events = generateEvents("test-seed", CUSTOMERS, UNTIL);
const PROPERTIES = ["model", "surface", "input_tokens", "output_tokens", "cache_read_tokens", "cache_write_tokens", "latency_ms"];

test("같은 씨앗이면 같은 이벤트, 다른 씨앗이면 다른 이벤트가 나온다", () => {
  assert.equal(eventsDigest(generateEvents("test-seed", CUSTOMERS, UNTIL)), eventsDigest(events));
  assert.notEqual(eventsDigest(generateEvents("other-seed", CUSTOMERS, UNTIL)), eventsDigest(events));
});

test("속성 일곱이 빠짐없이 있고 토큰과 응답 시간은 음수가 아닌 정수다", () => {
  for (const event of events) {
    assert.deepEqual(Object.keys(event.properties).sort(), [...PROPERTIES].sort());
    for (const key of PROPERTIES.slice(2)) {
      const value = event.properties[key as keyof typeof event.properties] as number;
      assert.ok(Number.isInteger(value) && value >= 0, `${key}=${value}`);
    }
  }
});

test("모델과 용도는 catalog가 허용하는 조합만 나오고 임베딩은 입력 토큰만 쓴다", () => {
  for (const event of events) {
    const model = MODELS.find(candidate => candidate.id === event.properties.model);
    assert.ok(model && model.surfaces.includes(event.properties.surface), `${event.properties.model}/${event.properties.surface}`);
    if (model.kind === "embedding") {
      assert.equal(event.properties.output_tokens + event.properties.cache_read_tokens + event.properties.cache_write_tokens, 0);
    } else {
      assert.ok(event.properties.output_tokens >= 1, `${event.properties.model} 출력 토큰`);
    }
  }
});

test("catalog의 글 모델과 토큰 분포 표의 모델이 같고, 표에 없는 모델은 오류로 멈춘다", () => {
  assert.deepEqual(Object.keys(TEXT_MODELS).sort(), MODELS.filter(model => model.kind === "text").map(model => model.id).sort());
  const saved = TEXT_MODELS["ab-5-mini"];
  delete TEXT_MODELS["ab-5-mini"];
  try {
    assert.throws(() => generateEvents("test-seed", CUSTOMERS, UNTIL), /ab-5-mini의 토큰 분포/);
  } finally { TEXT_MODELS["ab-5-mini"] = saved; }
});

test("--until은 이번 달 안의 지난 시각만 받는다", () => {
  const now = new Date("2026-11-11T05:00:00Z");
  checkUntil(now, now);
  checkUntil(new Date("2026-10-31T15:00:00Z"), now);
  assert.throws(() => checkUntil(new Date("2026-10-31T14:59:59Z"), now), /이번 달/);
  assert.throws(() => checkUntil(new Date("2026-10-20T00:00:00Z"), now), /이번 달/);
  assert.throws(() => checkUntil(new Date("2026-11-11T05:00:01Z"), now), /지금보다 뒤/);
  // 한국 시간으로 달이 바뀐 직후에는 UTC로 같은 달이어도 지난달이다.
  assert.throws(() => checkUntil(new Date("2026-11-30T14:00:00Z"), new Date("2026-11-30T15:30:00Z")), /이번 달/);
});

test("1일 하루치 지문은 그 뒤의 --until과 무관하게 같고 1일이 끝나기 전에는 없다", () => {
  const first = firstDayDigest("test-seed", CUSTOMERS, UNTIL);
  assert.match(first!, /^[0-9a-f]{64}$/);
  assert.equal(firstDayDigest("test-seed", CUSTOMERS, new Date("2026-11-01T15:00:00Z")), first);
  assert.equal(firstDayDigest("test-seed", CUSTOMERS, new Date("2026-11-25T00:00:00Z")), first);
  assert.notEqual(firstDayDigest("other-seed", CUSTOMERS, UNTIL), first);
  assert.equal(firstDayDigest("test-seed", CUSTOMERS, new Date("2026-11-01T14:59:59Z")), null);
});

test("1일 지문은 같은 달에서만 비교한다. 달이 바뀐 뒤 다시 실행해도 멈추지 않는다", () => {
  const november = checkFirstDay(undefined, "test-seed", CUSTOMERS, UNTIL);
  assert.deepEqual(november, { month: "2026-11", digest: firstDayDigest("test-seed", CUSTOMERS, UNTIL) });
  assert.deepEqual(checkFirstDay(november, "test-seed", CUSTOMERS, new Date("2026-11-20T00:00:00Z")), november);
  const december = checkFirstDay(november, "test-seed", CUSTOMERS, new Date("2026-12-05T03:00:00Z"));
  assert.equal(december!.month, "2026-12");
  assert.notEqual(december!.digest, november!.digest);
  assert.throws(() => checkFirstDay({ month: "2026-11", digest: december!.digest }, "test-seed", CUSTOMERS, UNTIL), /Node 버전/);
  // 1일이 끝나기 전에는 지문이 없어 지난 기록을 그대로 둔다.
  assert.deepEqual(checkFirstDay(november, "test-seed", CUSTOMERS, new Date("2026-12-01T03:00:00Z")), november);
});

test("거래 ID는 req_ 뒤 소문자 16진수 32자이고 겹치지 않는다", () => {
  const ids = new Set(events.map(event => event.transactionId));
  assert.equal(ids.size, events.length);
  for (const id of ids) assert.match(id, new RegExp(`^${TRANSACTION_ID_PREFIX}[0-9a-f]{32}$`));
});

test("이벤트는 이번 달 1일부터 기준 시각 전까지만 있다", () => {
  for (const event of events) {
    assert.ok(Date.parse(event.timestamp) < UNTIL.getTime());
    assert.ok(kstDate(new Date(event.timestamp)) >= "2026-11-01");
  }
});

test("나중에 다시 만들면 앞서 만든 이벤트를 그대로 포함한다", () => {
  const later = new Set(generateEvents("test-seed", CUSTOMERS, new Date("2026-11-11T09:00:00Z")).map(event => event.transactionId));
  for (const event of events) assert.ok(later.has(event.transactionId));
  const customer = CUSTOMERS[0];
  assert.deepEqual(generateDay("test-seed", customer, "2026-11-04"), generateDay("test-seed", customer, "2026-11-04"));
});

test("평일 하루 이벤트가 주말 하루보다 많다", () => {
  const perDay = new Map<string, number>();
  for (const event of events) perDay.set(kstDate(new Date(event.timestamp)), (perDay.get(kstDate(new Date(event.timestamp))) ?? 0) + 1);
  const weekend = ["2026-11-01", "2026-11-07", "2026-11-08"];
  const weekdays = ["2026-11-02", "2026-11-03", "2026-11-04", "2026-11-05", "2026-11-06", "2026-11-09", "2026-11-10"];
  const average = (dates: string[]) => dates.reduce((sum, date) => sum + (perDay.get(date) ?? 0), 0) / dates.length;
  assert.ok(average(weekdays) > 1.5 * average(weekend), `평일 ${average(weekdays)}, 주말 ${average(weekend)}`);
});

test("상한이나 최댓값과 같은 이벤트가 속성마다 0.1% 미만이다", () => {
  const limit = events.length * 0.001;
  const isEmbedding = (event: (typeof events)[number]) => event.properties.model === "ab-embed-3";
  const limits: Array<[keyof typeof TOKEN_LIMITS, (event: (typeof events)[number]) => number | undefined]> = [
    ["input_tokens", event => (isEmbedding(event) ? undefined : event.properties.input_tokens)],
    ["output_tokens", event => event.properties.output_tokens],
    ["cache_read_tokens", event => event.properties.cache_read_tokens],
    ["cache_write_tokens", event => event.properties.cache_write_tokens],
    ["embedding_input_tokens", event => (isEmbedding(event) ? event.properties.input_tokens : undefined)],
  ];
  for (const [key, read] of limits) {
    const values = events.map(read).filter((value): value is number => value !== undefined);
    assert.ok(values.every(value => value <= TOKEN_LIMITS[key]), key);
    assert.ok(values.filter(value => value === TOKEN_LIMITS[key]).length < limit, `${key} 상한`);
  }
  for (const key of ["input_tokens", "output_tokens", "cache_read_tokens", "cache_write_tokens", "latency_ms"] as const) {
    const max = events.reduce((largest, event) => Math.max(largest, event.properties[key]), 0);
    assert.ok(events.filter(event => event.properties[key] === max).length < limit, `${key} 최댓값 ${max}`);
  }
});

test("작은 곳과 신규 고객은 모델을 한두 개만 쓴다", () => {
  for (const customer of CUSTOMERS.filter(candidate => candidate.size === "small" || candidate.size === "new")) {
    const models = new Set(events.filter(event => event.customerSlug === customer.slug).map(event => event.properties.model));
    assert.ok(models.size <= 2, `${customer.slug} ${[...models]}`);
  }
});

test("캐시 읽기 토큰 합이 입력 토큰 합의 10배를 넘는다", () => {
  const sum = (key: "input_tokens" | "cache_read_tokens") => events.reduce((total, event) => total + event.properties[key], 0);
  assert.ok(sum("cache_read_tokens") > 10 * sum("input_tokens"));
});

test("사용량이 있는 조합은 모두 단가가 있고 금액은 규모를 따른다", () => {
  const amounts = estimateCharges(usageRows(events), priceRatePlans(CUSTOMERS));
  const of = (size: string) => CUSTOMERS.filter(customer => customer.size === size && !customer.noUsage).map(customer => amounts.get(customer.slug) ?? 0);
  assert.ok(of("large").every(amount => amount >= 100_000), `large ${of("large")}`);
  assert.ok(of("small").every(amount => amount >= 1000 && amount < 100_000), `small ${of("small")}`);
  assert.ok(Math.min(...of("large")) > Math.max(...of("small")));
  assert.equal(CUSTOMERS.filter(customer => customer.noUsage).every(customer => !events.some(event => event.customerSlug === customer.slug)), true);
});

test("줄 금액은 10원 아래를 버린다", () => {
  assert.equal(lineAmount(200_000, 4000), 800);
  assert.equal(lineAmount(1_333_333, 7.5), 0);
  assert.equal(lineAmount(3_000_000, 7.5), 20);
});

test("고객 구성: 큰 곳 5, 중간 15, 작은 곳 12, 신규 8이고 체험 계정은 규모별 한 곳씩이다", () => {
  const count = (size: string) => CUSTOMERS.filter(customer => customer.size === size).length;
  assert.deepEqual([count("large"), count("medium"), count("small"), count("new")], [5, 15, 12, 8]);
  assert.equal(new Set(CUSTOMERS.map(customer => customer.slug)).size, CUSTOMERS.length);
  const trials = CUSTOMERS.filter(customer => customer.trial);
  assert.deepEqual(trials.map(customer => customer.size), ["large", "medium", "small", "new"]);
  for (const customer of trials) assert.match(customer.slug, /^[a-z0-9][a-z0-9_-]{0,63}$/);
  assert.equal(trials[3].noUsage, true);
});
