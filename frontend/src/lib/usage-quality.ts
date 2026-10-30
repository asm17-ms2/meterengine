export type UnbilledEventCounts = {
  month: string;
  unmatched_type: { event_count: number };
  non_numeric_value: { event_count: number };
  future_timestamp: { event_count: number };
  negative_quantity: { event_count: number };
};

export type UsageQualityReason = { title: string; count: number; detail: string; href: string; action: string };

export function usageQualityReasons(data: UnbilledEventCounts): UsageQualityReason[] {
  return [
    { title: "미터와 연결되지 않은 유형", count: data.unmatched_type.event_count, detail: "이벤트 유형에 맞는 미터를 등록하세요.", href: `/event-issues?reason=unknown_event_type&month=${data.month}`, action: "대상 이벤트 보기" },
    { title: "집계할 수 없는 속성", count: data.non_numeric_value.event_count, detail: "집계 함수에 필요한 속성이 없거나 값 형식이 맞지 않아 사용량에서 제외됐습니다.", href: `/event-issues?reason=invalid_target_property&month=${data.month}`, action: "대상 이벤트 보기" },
    { title: "미래 시각의 이벤트", count: data.future_timestamp.event_count, detail: "시각이 도래하기 전까지 집계에 포함하지 않습니다.", href: `/events?month=${data.month}`, action: "시각 확인" },
    { title: "음수 수량의 이벤트", count: data.negative_quantity.event_count, detail: "선택한 집계 함수에 따라 반영합니다. 집계값이 음수인 조합은 청구 검토에서 확인하세요.", href: `/billing?month=${data.month}`, action: "청구 검토" },
  ];
}
