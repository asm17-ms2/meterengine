"""otel_map 테스트. 네트워크 없이 변환만 확인한다."""

import unittest
from decimal import Decimal

from bridge import otel_map
from core.model import build_body_text, loads_decimal, parse_rfc3339


def payload(*records):
    return {"resourceLogs": [{"scopeLogs": [{"logRecords": list(records)}]}]}


def record(name, attributes, time_nano="1787278841429000000"):
    items = [{"key": "event.name", "value": {"stringValue": name}}]
    for key, value in attributes.items():
        items.append({"key": key, "value": value})
    return {"timeUnixNano": time_nano, "attributes": items}


def api_request(**overrides):
    attributes = {
        "session.id": {"stringValue": "sess-1"},
        "model": {"stringValue": "claude-opus-5"},
        "input_tokens": {"intValue": 2},
        "output_tokens": {"intValue": 75},
        "cache_read_tokens": {"intValue": 33661},
        "cache_creation_tokens": {"intValue": 0},
        "cost_usd": {"doubleValue": Decimal("0.0187155")},
        "duration_ms": {"intValue": 2060},
        "request_id": {"stringValue": "req_011CeF71RPMGTvwPW6WsEw79"},
        "speed": {"stringValue": "normal"},
        "effort": {"stringValue": "high"},
    }
    attributes.update(overrides)
    return record("api_request", attributes)


class SplitTest(unittest.TestCase):
    def test_보낼_이벤트만_추린다(self):
        data = payload(
            api_request(),
            record("user_prompt", {"prompt_length": {"intValue": 10}}),
            record("assistant_response", {"response_length": {"intValue": 4}}),
            record("tool_result", {"tool_use_id": {"stringValue": "toolu_1"}}),
            record("hook_registered", {}),
        )
        names = [name for _, name, _ in otel_map.split_records(data)]
        self.assertEqual(names, ["api_request", "tool_result"])

    def test_session_id를_함께_돌려준다(self):
        picked = otel_map.split_records(payload(api_request()))
        self.assertEqual(picked[0][2], "sess-1")

    def test_구조가_어긋나면_건너뛴다(self):
        self.assertEqual(otel_map.split_records({}), [])
        self.assertEqual(otel_map.split_records({"resourceLogs": "이상함"}), [])
        self.assertEqual(otel_map.split_records({"resourceLogs": [{"scopeLogs": [{}]}]}), [])


class ToEventTest(unittest.TestCase):
    def test_시드_미터가_읽는_event_type으로_간다(self):
        event = otel_map.to_event(api_request(), "c" * 8, {})
        self.assertEqual(event.event_type, "llm_request")

    def test_request_id가_멱등키다(self):
        event = otel_map.to_event(api_request(), "cid", {})
        self.assertEqual(event.transaction_id, "api_request:req_011CeF71RPMGTvwPW6WsEw79")

    def test_도구_이벤트는_tool_use_id가_멱등키다(self):
        data = record("tool_result", {"tool_use_id": {"stringValue": "toolu_01M3"}})
        event = otel_map.to_event(data, "cid", {})
        self.assertEqual(event.event_type, "tool_call")
        self.assertEqual(event.transaction_id, "tool_result:toolu_01M3")

    def test_같은_id를_실은_다른_이벤트가_멱등키를_나눠_갖지_않는다(self):
        """이름을 안 붙이면 뒤엣것이 서버에서 duplicate로 조용히 사라진다.

        api_refusal이 먼저 닿으면 토큰이 실린 llm_request 쪽이 버려진다.
        """
        same = {"request_id": {"stringValue": "req_ABC"}}
        request = otel_map.to_event(record("api_request", same), "cid", {})
        refusal = otel_map.to_event(record("api_refusal", same), "cid", {})
        self.assertNotEqual(request.transaction_id, refusal.transaction_id)

    def test_멱등키가_없으면_세션과_순번으로_만든다(self):
        data = record(
            "api_request",
            {"session.id": {"stringValue": "sess-9"}, "event.sequence": {"intValue": 41}},
        )
        event = otel_map.to_event(data, "cid", {})
        self.assertEqual(event.transaction_id, "api_request:sess-9:41")

    def test_멱등키로_쓸_값이_전혀_없으면_거부한다(self):
        with self.assertRaises(otel_map.UnmappableRecord):
            otel_map.to_event(record("api_request", {}), "cid", {})

    def test_transaction_id는_255자를_넘지_않는다(self):
        # 이름을 붙인 뒤에도 서버 상한(255) 안이어야 한다.
        data = record("api_request", {"request_id": {"stringValue": "r" * 400}})
        event = otel_map.to_event(data, "cid", {})
        self.assertEqual(len(event.transaction_id), 255)

    def test_보내지_않는_이벤트는_거부한다(self):
        with self.assertRaises(otel_map.UnmappableRecord):
            otel_map.to_event(record("user_prompt", {}), "cid", {})

    def test_시각은_KST_오프셋_RFC3339다(self):
        event = otel_map.to_event(api_request(), "cid", {})
        moment = parse_rfc3339(event.timestamp_text)
        self.assertEqual(moment.utcoffset().total_seconds(), 9 * 3600)
        # timeUnixNano가 가리키는 순간과 같아야 한다
        self.assertEqual(moment.timestamp(), 1787278841.429)

    def test_timeUnixNano가_없으면_event_timestamp를_쓴다(self):
        data = record("api_request", {"request_id": {"stringValue": "r1"}}, time_nano=None)
        del data["timeUnixNano"]
        data["attributes"].append(
            {"key": "event.timestamp", "value": {"stringValue": "2026-08-21T02:20:41.429Z"}}
        )
        event = otel_map.to_event(data, "cid", {})
        self.assertEqual(event.timestamp_text, "2026-08-21T02:20:41.429Z")


class PropertiesTest(unittest.TestCase):
    def properties_of(self, event):
        return loads_decimal(event.properties_text)

    def test_개인정보는_담지_않는다(self):
        data = api_request(
            **{
                "user.email": {"stringValue": "someone@example.com"},
                "user.id": {"stringValue": "hash"},
                "user.account_uuid": {"stringValue": "uuid"},
                "user.account_id": {"stringValue": "user_01"},
                "organization.id": {"stringValue": "anthropic-org"},
            }
        )
        properties = self.properties_of(otel_map.to_event(data, "cid", {}))
        for blocked in ("user_email", "user_id", "user_account_uuid", "user_account_id", "organization_id"):
            self.assertNotIn(blocked, properties)
        self.assertNotIn("someone@example.com", otel_map.to_event(data, "cid", {}).properties_text)

    def test_점을_언더스코어로_바꾼다(self):
        data = api_request(**{"agent.name": {"stringValue": "Explore"}})
        properties = self.properties_of(otel_map.to_event(data, "cid", {}))
        self.assertEqual(properties["agent_name"], "Explore")
        self.assertNotIn("agent.name", properties)

    def test_토큰은_JSON_number다(self):
        """미터의 집계는 값이 JSON number일 때만 돈다. 문자열이면 조용히 빠진다."""
        properties = self.properties_of(otel_map.to_event(api_request(), "cid", {}))
        self.assertIsInstance(properties["input_tokens"], int)
        self.assertIsInstance(properties["output_tokens"], int)
        self.assertEqual(properties["input_tokens"], 2)
        self.assertEqual(properties["output_tokens"], 75)

    def test_문자열로_온_수치도_number로_바꾼다(self):
        """tool_result의 duration_ms는 stringValue로 온다 (실측)."""
        data = record(
            "tool_result",
            {
                "tool_use_id": {"stringValue": "toolu_1"},
                "duration_ms": {"stringValue": "145"},
                "tool_result_size_bytes": {"stringValue": "2"},
                "success": {"stringValue": "true"},
            },
        )
        properties = self.properties_of(otel_map.to_event(data, "cid", {}))
        self.assertEqual(properties["duration_ms"], 145)
        self.assertIsInstance(properties["duration_ms"], int)
        self.assertEqual(properties["tool_result_size_bytes"], 2)
        # success는 수치 키가 아니라 문자열 그대로 둔다 (차원 후보다)
        self.assertEqual(properties["success"], "true")

    def test_문자열로_온_int64도_받는다(self):
        """protobuf JSON 매핑은 int64를 문자열로 쓴다. 어느 쪽이든 받아야 한다."""
        data = api_request(input_tokens={"intValue": "40000"})
        properties = self.properties_of(otel_map.to_event(data, "cid", {}))
        self.assertEqual(properties["input_tokens"], 40000)

    def test_소수는_자릿수가_보존된다(self):
        data = api_request(cost_usd={"doubleValue": Decimal("0.0187155")})
        event = otel_map.to_event(data, "cid", {})
        self.assertIn('"cost_usd": 0.0187155', event.properties_text)

    def test_수치로_바꿀_수_없으면_담지_않는다(self):
        data = api_request(duration_ms={"stringValue": "빠름"})
        properties = self.properties_of(otel_map.to_event(data, "cid", {}))
        self.assertNotIn("duration_ms", properties)

    def test_NaN과_Infinity는_담지_않는다(self):
        """JSON에 표기가 없는 값이라, 실으면 본문이 JSON으로 성립하지 않는다.

        서버가 거절할 뿐 아니라 로그에도 request_raw만 남아 verify가 그 줄을
        재구성하지 못한다. Decimal("NaN")이 성립하기 때문에 걸리는 자리다.
        """
        for text in ("NaN", "Infinity", "-Infinity"):
            data = api_request(duration_ms={"stringValue": text})
            event = otel_map.to_event(data, "cid", {})
            self.assertNotIn("duration_ms", self.properties_of(event), text)
            # 본문 전체가 다시 읽히는지까지 본다
            loads_decimal(event.properties_text)

    def test_브리지가_덧붙인_값이_들어간다(self):
        event = otel_map.to_event(api_request(), "cid", {"project": "meterengine", "owner": "박성종"})
        properties = self.properties_of(event)
        self.assertEqual(properties["project"], "meterengine")
        self.assertEqual(properties["owner"], "박성종")
        self.assertEqual(properties["otel_event"], "api_request")

    def test_OTel_속성이_덧붙인_값을_이긴다(self):
        event = otel_map.to_event(api_request(), "cid", {"model": "덮어쓰기 시도"})
        self.assertEqual(self.properties_of(event)["model"], "claude-opus-5")

    def test_event_name과_timestamp는_properties에_없다(self):
        properties = self.properties_of(otel_map.to_event(api_request(), "cid", {}))
        self.assertNotIn("event_name", properties)
        self.assertNotIn("event_timestamp", properties)

    def test_평평하지_않은_값은_담지_않는다(self):
        data = api_request(weird={"arrayValue": {"values": [{"stringValue": "a"}]}})
        self.assertNotIn("weird", self.properties_of(otel_map.to_event(data, "cid", {})))


class WireBodyTest(unittest.TestCase):
    def test_전송_본문이_JSON으로_성립한다(self):
        event = otel_map.to_event(api_request(), "35bc8d12-9d38-57ab-bc9b-bbd35d779a26", {"project": "p"})
        body = loads_decimal(build_body_text(event))
        self.assertEqual(body["customer_id"], "35bc8d12-9d38-57ab-bc9b-bbd35d779a26")
        self.assertEqual(body["type"], "llm_request")
        self.assertEqual(body["properties"]["input_tokens"], 2)
        self.assertEqual(set(body), {"transaction_id", "customer_id", "type", "properties", "occurred_at"})

    def test_한글이_그대로_실린다(self):
        event = otel_map.to_event(api_request(), "cid", {"owner": "박성종"})
        self.assertIn("박성종", build_body_text(event))


if __name__ == "__main__":
    unittest.main()
