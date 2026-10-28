import assert from "node:assert/strict";
import test from "node:test";
import { billableMetricFailureMessage } from "../src/app/(console)/billable-metrics/state.ts";

test("changing the calculation of a meter with events says what can still change instead of API field names", () => {
  const message = billableMetricFailureMessage("billable_metric_has_events", "집계된 이벤트가 있어 event_type과 target_property를 바꾸거나 미터를 삭제할 수 없습니다");
  assert.equal(message, "수집된 이벤트가 있어 이름 외의 계산 기준은 바꿀 수 없습니다. 계산 기준을 바꾸려면 새 미터를 등록하세요.");
  assert.equal(billableMetricFailureMessage("internal_error", "서버에서 요청을 처리하지 못했습니다."), "서버에서 요청을 처리하지 못했습니다.");
});
