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
    { title: "연결할 미터 없음", count: data.unmatched_type.event_count, detail: "이벤트 유형에 맞는 미터를 등록하세요.", href: `/event-issues?reason=unknown_event_type&month=${data.month}`, action: "대상 이벤트 보기" },
    { title: "집계할 수량 확인 필요", count: data.non_numeric_value.event_count, detail: "집계 함수에 필요한 속성이 없거나 값 형식이 맞지 않아 사용량에서 빠졌어요.", href: `/event-issues?reason=invalid_target_property&month=${data.month}`, action: "대상 이벤트 보기" },
    { title: "발생 시각이 미래인 이벤트", count: data.future_timestamp.event_count, detail: "발생 시각이 지나야 집계에 들어가요.", href: `/events?month=${data.month}`, action: "시각 확인" },
    { title: "수량이 음수인 이벤트", count: data.negative_quantity.event_count, detail: "미터의 집계 함수대로 반영해요. 집계값이 음수인 조합은 청구 검토에서 확인하세요.", href: `/billing?month=${data.month}`, action: "청구 검토" },
  ];
}
