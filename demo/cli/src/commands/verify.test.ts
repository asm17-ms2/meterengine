import assert from "node:assert/strict";
import { test } from "node:test";
import { EXIT_FAILED, EXIT_OK, EXIT_RETRYABLE, EXIT_USAGE } from "../exit-code.ts";
import { run } from "../program.ts";
import { json, setup } from "../testing.ts";

const BASE = "http://localhost:8080";
const STARTED = "2026-09-01T00:00:00Z";
const NOT_FOUND = { code: "event_not_found", message: "이벤트를 찾을 수 없습니다" };

type Rejection = { transaction_id: string | null; error_code: string; recorded_at?: string };

function rejection(item: Rejection) {
  return {
    id: crypto.randomUUID(), customer_id: null, type: "api_call", occurred_at: null, error_fields: [], request_id: null, batch_index: null,
    recorded_at: "2026-11-10T00:00:00Z", ...item,
  };
}

function page(month: string, total: number, rejections: Rejection[], recordingStartedAt = STARTED) {
  return json(200, { month, recording_started_at: recordingStartedAt, page: 0, size: 100, total, rejections: rejections.map(rejection) });
}

// 요청 주소를 보고 답하는 가짜 서버다. 호출 순서에 기대지 않는다.
function server(stored: string[], rejections: (params: URLSearchParams) => Response | Error) {
  return (url: string) => {
    const parsed = new URL(url);
    if (parsed.pathname === "/v1/events/lookup") {
      const id = parsed.searchParams.get("transaction_id") ?? "";
      return stored.includes(id) ? json(200, { transaction_id: id }) : json(404, NOT_FOUND);
    }
    if (parsed.pathname === "/v1/event-rejections/search") return rejections(parsed.searchParams);
    return new Error(`모르는 요청: ${url}`);
  };
}

function replies(count: number, reply: (url: string) => Response | Error) {
  return Array.from({ length: count }, () => reply);
}

function lookupUrl(id: string) {
  return `${BASE}/v1/events/lookup?transaction_id=${encodeURIComponent(id)}`;
}

test("verify sent는 줄마다 쓴 id와 JSON 줄을 함께 읽어 stored, rejected, missing으로 나눈다", async () => {
  const file = [
    "# 11/14 재전송 대상",
    "tx-ok",
    "",
    '{"transaction_id":"tx-typo","customer_id":"c","type":"api_calls"}',
    "  tx-nocust  ",
    "tx-extid",
    "tx-ok",
  ].join("\r\n");
  const reply = server(["tx-ok", "tx-typo"], (params) => {
    if (params.get("month") === "2026-11") {
      return page("2026-11", 2, [
        { transaction_id: "tx-nocust", error_code: "customer_not_found", recorded_at: "2026-11-13T00:00:00Z" },
        { transaction_id: "tx-nocust-2", error_code: "event_too_old" },
      ]);
    }
    return page("2026-10", 0, []);
  });
  const { deps, calls, stdoutJson, err } = await setup(replies(6, reply), { files: { "sent.txt": file } });

  const code = await run(["verify", "sent", "sent.txt"], deps);

  assert.equal(code, EXIT_OK);
  assert.equal(err.join(""), "");
  const output = stdoutJson() as Record<string, unknown>;
  assert.deepEqual(
    { ...output, notes: undefined },
    {
      total: 4,
      stored: ["tx-ok", "tx-typo"],
      rejected: [{ transaction_id: "tx-nocust", code: "customer_not_found", error_fields: [], recorded_at: "2026-11-13T00:00:00Z" }],
      missing: ["tx-extid"],
      duplicate_lines: 1,
      rejection_months: ["2026-11", "2026-10"],
      notes: undefined,
    },
  );
  assert.equal((output.notes as string[]).length, 3);
  assert.match((output.notes as string[])[0], /202/);
  assert.deepEqual(
    calls.map((call) => call.url).sort(),
    [
      ...["tx-ok", "tx-typo", "tx-nocust", "tx-extid"].map(lookupUrl),
      `${BASE}/v1/event-rejections/search?month=2026-11&page=0&size=100`,
      `${BASE}/v1/event-rejections/search?month=2026-10&page=0&size=100`,
    ].sort(),
  );
  assert.ok(calls.every((call) => call.method === "GET"));
});

test("모두 저장됐으면 거절 기록을 조회하지 않고 notes가 비어 있다", async () => {
  const { deps, calls, stdoutJson } = await setup(replies(2, server(["a", "b"], () => new Error("부르면 안 된다"))), { files: { ids: "a\nb\n" } });

  assert.equal(await run(["verify", "sent", "ids"], deps), EXIT_OK);
  assert.deepEqual(stdoutJson(), { total: 2, stored: ["a", "b"], rejected: [], missing: [], duplicate_lines: 0, rejection_months: [], notes: [] });
  assert.equal(calls.length, 2);
});

test("-면 표준 입력으로 받은 내용을 대조한다", async () => {
  const { deps, calls } = await setup(replies(1, server(["tx-1"], () => new Error("부르면 안 된다"))), { files: { "-": "tx-1\n" } });

  assert.equal(await run(["verify", "sent", "-"], deps), EXIT_OK);
  assert.equal(calls[0].url, lookupUrl("tx-1"));
});

test("거절 목록의 남은 페이지가 찾을 id 수 이하면 목록을 끝까지 받아 대조한다", async () => {
  const reply = server([], (params) => {
    const pageNumber = Number(params.get("page"));
    assert.equal(params.get("q"), null);
    if (pageNumber === 2) return page("2026-11", 250, [{ transaction_id: "x2", error_code: "customer_archived" }]);
    if (pageNumber === 1) return page("2026-11", 250, [{ transaction_id: "x1", error_code: "validation_error" }]);
    return page("2026-11", 250, [{ transaction_id: "other", error_code: "customer_not_found" }]);
  });
  const { deps, calls, stdoutJson } = await setup(replies(6, reply), { files: { ids: "x1\nx2\nx3" } });

  assert.equal(await run(["verify", "sent", "ids", "--month", "2026-11"], deps), EXIT_OK);
  const output = stdoutJson() as { rejected: { transaction_id: string; code: string }[]; missing: string[] };
  assert.deepEqual(output.rejected.map((item) => [item.transaction_id, item.code]), [["x1", "validation_error"], ["x2", "customer_archived"]]);
  assert.deepEqual(output.missing, ["x3"]);
  assert.deepEqual(
    calls.filter((call) => call.url.includes("event-rejections")).map((call) => call.url).sort(),
    [0, 1, 2].map((number) => `${BASE}/v1/event-rejections/search?month=2026-11&page=${number}&size=100`),
  );
});

test("거절 목록의 남은 페이지가 찾을 id보다 많으면 id마다 q로 찾고 transaction_id가 정확히 같은 것만 쓴다", async () => {
  const reply = server([], (params) => {
    const q = params.get("q");
    if (q === null) return page("2026-11", 1000, [{ transaction_id: "noise", error_code: "customer_not_found" }]);
    if (q === "tx-1" && params.get("page") === "0") return page("2026-11", 150, [{ transaction_id: "tx-10", error_code: "event_too_old" }]);
    if (q === "tx-1") return page("2026-11", 150, [{ transaction_id: "tx-1", error_code: "customer_not_found" }]);
    return page("2026-11", 1, [{ transaction_id: "TX-2", error_code: "customer_not_found" }]);
  });
  const { deps, calls, stdoutJson } = await setup(replies(6, reply), { files: { ids: "tx-1\ntx-2" } });

  assert.equal(await run(["verify", "sent", "ids", "--month", "2026-11"], deps), EXIT_OK);
  const output = stdoutJson() as { rejected: { transaction_id: string; code: string }[]; missing: string[] };
  assert.deepEqual(output.rejected.map((item) => [item.transaction_id, item.code]), [["tx-1", "customer_not_found"]]);
  assert.deepEqual(output.missing, ["tx-2"]);
  assert.deepEqual(
    calls.filter((call) => call.url.includes("q=")).map((call) => call.url).sort(),
    [
      `${BASE}/v1/event-rejections/search?month=2026-11&q=tx-1&page=0&size=100`,
      `${BASE}/v1/event-rejections/search?month=2026-11&q=tx-1&page=1&size=100`,
      `${BASE}/v1/event-rejections/search?month=2026-11&q=tx-2&page=0&size=100`,
    ],
  );
});

test("--month를 여러 번 주면 최근 달부터 찾고 이미 찾은 id는 이전 달에서 다시 찾지 않는다", async () => {
  const reply = server([], (params) => {
    if (params.get("month") === "2026-10") return page("2026-10", 1, [{ transaction_id: "a", error_code: "event_too_old", recorded_at: "2026-10-31T00:00:00Z" }]);
    return page("2026-09", 1, [{ transaction_id: "a", error_code: "customer_not_found" }]);
  });
  const { deps, calls, stdoutJson } = await setup(replies(2, reply), { files: { ids: "a" } });

  assert.equal(await run(["verify", "sent", "ids", "--month", "2026-09", "--month", "2026-10", "--month", "2026-10"], deps), EXIT_OK);
  const output = stdoutJson() as { rejected: { code: string }[]; rejection_months: string[] };
  assert.equal(output.rejected[0].code, "event_too_old");
  assert.deepEqual(output.rejection_months, ["2026-10"]);
  assert.equal(calls.length, 2);
});

test("거절 기록을 시작한 시각이 찾은 달보다 늦으면 missing 안내에 그 시각을 붙인다", async () => {
  const reply = server([], (params) => page(params.get("month") ?? "", 0, [], "2026-11-14T01:29:57.332477Z"));
  const { deps, stdoutJson } = await setup(replies(3, reply), { files: { ids: "gone" } });

  assert.equal(await run(["verify", "sent", "ids"], deps), EXIT_OK);
  const notes = (stdoutJson() as { notes: string[] }).notes;
  assert.equal(notes.length, 4);
  assert.match(notes[3], /2026-11-14T01:29:57\.332477Z/);
});

test("기본 달은 KST로 이번 달과 지난달이다", async () => {
  const reply = server([], (params) => page(params.get("month") ?? "", 0, []));
  const { deps, stdoutJson } = await setup(replies(3, reply), { files: { ids: "gone" }, now: new Date("2026-10-31T15:30:00Z") });

  assert.equal(await run(["verify", "sent", "ids"], deps), EXIT_OK);
  assert.deepEqual((stdoutJson() as { rejection_months: string[] }).rejection_months, ["2026-11", "2026-10"]);
});

test("잘못된 줄이 있으면 서버에 보내지 않고 줄 번호와 함께 종료 코드 2다", async () => {
  const file = ["ok", "{not json", '{"customer_id":"c"}', '{"transaction_id":"  "}', "x".repeat(256), '{"transaction_id":"a\\u0000b"}'].join("\n");
  const { deps, calls, stderrJson } = await setup([], { files: { ids: file } });

  assert.equal(await run(["verify", "sent", "ids"], deps), EXIT_USAGE);
  assert.equal(calls.length, 0);
  const error = stderrJson() as { code: string; message: string };
  assert.equal(error.code, "invalid_input_line");
  for (const line of [2, 3, 4, 5, 6]) assert.match(error.message, new RegExp(`${line}번째 줄`));
  assert.doesNotMatch(error.message, /1번째 줄/);
});

test("잘못된 줄이 많으면 다섯 줄까지만 적고 나머지는 수로 알린다", async () => {
  const { deps, stderrJson } = await setup([], { files: { ids: Array.from({ length: 8 }, () => "{bad").join("\n") } });

  assert.equal(await run(["verify", "sent", "ids"], deps), EXIT_USAGE);
  assert.match((stderrJson() as { message: string }).message, /외 3줄$/);
});

test("대조할 id가 없거나 너무 많거나 파일이 없거나 달이 틀리면 서버에 보내지 않고 종료 코드 2다", async () => {
  const many = Array.from({ length: 1001 }, (_value, index) => `tx-${index}`).join("\n");
  const cases: [string[], string][] = [
    [["verify", "sent", "empty"], "no_transaction_ids"],
    [["verify", "sent", "many"], "too_many_transaction_ids"],
    [["verify", "sent", "nope"], "file_not_readable"],
    [["verify", "sent", "empty", "--month", "2026-13"], "invalid_month"],
    [["verify", "sent"], "invalid_usage"],
  ];
  for (const [argv, expected] of cases) {
    const { deps, calls, stderrJson } = await setup([], { files: { empty: "# 주석만\n\n", many } });
    assert.equal(await run(argv, deps), EXIT_USAGE, argv.join(" "));
    assert.equal(calls.length, 0);
    assert.equal((stderrJson() as { code: string }).code, expected);
  }
});

test("중복을 빼고 1000개면 대조한다", async () => {
  const ids = Array.from({ length: 1000 }, (_value, index) => `tx-${index}`);
  const stored = new Set(ids);
  const reply = (url: string) => (stored.has(new URL(url).searchParams.get("transaction_id") ?? "") ? json(200, {}) : json(404, NOT_FOUND));
  const { deps, calls, stdoutJson } = await setup(replies(1000, reply), { files: { ids: [...ids, ...ids.slice(0, 5)].join("\n") } });

  assert.equal(await run(["verify", "sent", "ids"], deps), EXIT_OK);
  const output = stdoutJson() as { total: number; duplicate_lines: number; stored: string[] };
  assert.equal(output.total, 1000);
  assert.equal(output.duplicate_lines, 5);
  assert.deepEqual(output.stored, ids);
  assert.equal(calls.length, 1000);
});

test("lookup이 event_not_found가 아닌 오류면 진단을 멈추고 오류에 맞는 종료 코드를 낸다", async () => {
  const cases: [Response, number, string][] = [
    [json(401, { code: "invalid_credentials", message: "인증 정보가 올바르지 않습니다" }), EXIT_FAILED, "invalid_credentials"],
    [json(404, { code: "not_found", message: "없음" }), EXIT_FAILED, "not_found"],
    [json(503, { code: "service_unavailable", message: "잠시 뒤" }), EXIT_RETRYABLE, "service_unavailable"],
  ];
  for (const [response, exitCode, errorCode] of cases) {
    const { deps, out, stderrJson } = await setup([response], { files: { ids: "a" } });
    assert.equal(await run(["verify", "sent", "ids"], deps), exitCode);
    assert.equal(out.join(""), "");
    assert.equal((stderrJson() as { code: string }).code, errorCode);
  }
});

test("거절 조회가 실패해도 진단을 멈추고 오류에 맞는 종료 코드를 낸다", async () => {
  const reply = server([], () => json(500, { code: "internal_server_error", message: "오류" }));
  const { deps, out } = await setup(replies(2, reply), { files: { ids: "a" } });

  assert.equal(await run(["verify", "sent", "ids", "--month", "2026-11"], deps), EXIT_RETRYABLE);
  assert.equal(out.join(""), "");
});

test("조회는 동시에 네 개까지만 보낸다", async () => {
  const { deps, stdoutJson } = await setup([], { files: { ids: Array.from({ length: 12 }, (_value, index) => `tx-${index}`).join("\n") } });
  let inFlight = 0;
  let peak = 0;
  deps.clientOptions = {
    ...deps.clientOptions,
    fetch: async () => {
      inFlight += 1;
      peak = Math.max(peak, inFlight);
      await new Promise((resolve) => setTimeout(resolve, 5));
      inFlight -= 1;
      return json(200, {});
    },
  };

  assert.equal(await run(["verify", "sent", "ids"], deps), EXIT_OK);
  assert.equal((stdoutJson() as { stored: string[] }).stored.length, 12);
  assert.equal(peak, 4);
});

function rateLimited(): Response {
  return new Response(JSON.stringify({ code: "rate_limit_exceeded", message: "한도" }), {
    status: 429,
    headers: { "Content-Type": "application/json", "Retry-After": "0" },
  });
}

test("429를 받으면 하나씩 보내며 Retry-After만큼 기다려 다시 보내 끝까지 대조한다", async () => {
  const ids = Array.from({ length: 12 }, (_value, index) => `tx-${index}`);
  const { deps, stdoutJson } = await setup([], { files: { ids: ids.join("\n") } });
  let inFlight = 0;
  let responses = 0;
  const startedWith: number[] = [];
  deps.clientOptions = {
    ...deps.clientOptions,
    fetch: async () => {
      inFlight += 1;
      startedWith.push(inFlight);
      await new Promise((resolve) => setTimeout(resolve, 2));
      inFlight -= 1;
      responses += 1;
      // 토큰이 다 떨어진 키처럼 처음 여덟 번은 두 번에 한 번씩 거절한다.
      return responses <= 8 && responses % 2 === 1 ? rateLimited() : json(200, {});
    },
  };

  assert.equal(await run(["verify", "sent", "ids"], deps), EXIT_OK);
  assert.deepEqual((stdoutJson() as { stored: string[] }).stored, ids);
  assert.equal(startedWith.length, 16);
  assert.equal(startedWith[3], 4);
  // 429 전에 나간 요청이 끝나면 그 뒤로는 하나씩만 나간다.
  assert.deepEqual(startedWith.slice(-4), [1, 1, 1, 1]);
});

test("429가 계속되면 기다리는 횟수를 다 쓴 뒤 종료 코드 3이다", async () => {
  const { deps, calls, out, stderrJson } = await setup(replies(31, () => rateLimited()), { files: { ids: "a" } });

  assert.equal(await run(["verify", "sent", "ids"], deps), EXIT_RETRYABLE);
  assert.equal(calls.length, 31);
  assert.equal(out.join(""), "");
  assert.equal((stderrJson() as { code: string }).code, "rate_limit_exceeded");
});

test("거절 목록을 받는 동안 기록이 늘어 밀려난 id는 q로 다시 찾는다", async () => {
  const reply = server([], (params) => {
    const q = params.get("q");
    if (q === "old") return page("2026-11", 1, [{ transaction_id: "old", error_code: "customer_not_found" }]);
    if (q !== null) return page("2026-11", 0, []);
    // 첫 페이지를 받은 뒤 새 거절 5건이 앞에 쌓여 마지막 기록 old가 3페이지로 밀렸다.
    if (params.get("page") === "0") return page("2026-11", 200, [{ transaction_id: "noise", error_code: "customer_not_found" }]);
    return page("2026-11", 205, [{ transaction_id: "noise", error_code: "customer_not_found" }]);
  });
  const { deps, calls, stdoutJson } = await setup(replies(8, reply), { files: { ids: "old\ngone" } });

  assert.equal(await run(["verify", "sent", "ids", "--month", "2026-11"], deps), EXIT_OK);
  const output = stdoutJson() as { rejected: { transaction_id: string; code: string }[]; missing: string[] };
  assert.deepEqual(output.rejected.map((item) => [item.transaction_id, item.code]), [["old", "customer_not_found"]]);
  assert.deepEqual(output.missing, ["gone"]);
  assert.deepEqual(
    calls.filter((call) => call.url.includes("q=")).map((call) => call.url).sort(),
    ["gone", "old"].map((id) => `${BASE}/v1/event-rejections/search?month=2026-11&q=${id}&page=0&size=100`),
  );
});

test("거절 목록의 기록 수가 그대로면 q로 다시 찾지 않는다", async () => {
  const reply = server([], () => page("2026-11", 200, [{ transaction_id: "noise", error_code: "customer_not_found" }]));
  const { deps, calls } = await setup(replies(4, reply), { files: { ids: "a\nb" } });

  assert.equal(await run(["verify", "sent", "ids", "--month", "2026-11"], deps), EXIT_OK);
  assert.equal(calls.filter((call) => call.url.includes("q=")).length, 0);
});

test("verify 도움말은 무엇을 대조하는지 알리고 --month에 빈 기본값을 붙이지 않는다", async () => {
  const top = await setup([]);
  assert.equal(await run(["--help"], top.deps), EXIT_OK);
  assert.match(top.out.join(""), /verify\s+보낸 이벤트가 저장됐는지 대조한다/);

  const sent = await setup([]);
  assert.equal(await run(["verify", "sent", "--help"], sent.deps), EXIT_OK);
  const help = sent.out.join("").replace(/\s+/g, " ");
  assert.doesNotMatch(help, /default: \[\]/);
  assert.match(help, /앞뒤 공백이 있거나 #이나 \{로 시작하는 id는 JSON 한 줄로/);
  assert.match(help, /요청 한도/);
});

test("앞뒤 공백이 있는 id는 JSON 한 줄로 적으면 공백까지 그대로 조회한다", async () => {
  const { deps, calls } = await setup(replies(1, server([" padded "], () => new Error("부르면 안 된다"))), { files: { ids: '{"transaction_id":" padded "}' } });

  assert.equal(await run(["verify", "sent", "ids"], deps), EXIT_OK);
  assert.equal(new URL(calls[0].url).searchParams.get("transaction_id"), " padded ");
});
