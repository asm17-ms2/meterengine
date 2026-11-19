package com.meterengine.event.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;

public record UnbilledEventsResponse(
    @Schema(description = "조회한 달(yyyy-MM, KST)") String month,
    @Schema(description = "집계에 넣는 occurred_at의 상한(미포함, ISO 8601)입니다. 지금 시각과 그 달의 끝 가운데 이른 쪽입니다.")
        @JsonProperty("cutoff_at")
        OffsetDateTime cutoffAt,
    @Schema(description = "연결할 과금 미터 없음 항목입니다. type을 세는 과금 미터가 없는 이벤트를 셉니다.")
        @JsonProperty("unmatched_type")
        UnmatchedType unmatchedType,
    @Schema(description = "수량을 읽을 수 없음 항목입니다. 과금 미터가 세는 속성이 없거나 형식이 맞지 않는 이벤트를 과금 미터별로 셉니다.")
        @JsonProperty("non_numeric_value")
        MetricCounts nonNumericValue,
    @Schema(description = "발생 시각이 미래인 이벤트 항목입니다. 그 달에 발생했고 occurred_at이 cutoff_at 이후인 이벤트를 셉니다.")
        @JsonProperty("future_timestamp")
        FutureTimestamp futureTimestamp,
    @Schema(description = "수량이 음수인 이벤트 항목입니다. 숫자 집계 과금 미터에서 수량 값이 음수인 이벤트를 과금 미터별로 셉니다.")
        @JsonProperty("negative_quantity")
        MetricCounts negativeQuantity) {

  public record UnmatchedType(
      @Schema(description = "이 항목에 든 이벤트 수") @JsonProperty("event_count") long eventCount,
      @Schema(description = "유형별 이벤트 수입니다. 건수가 많은 순입니다.") List<TypeCount> types) {}

  public record TypeCount(
      @Schema(description = "이벤트 유형") String type,
      @Schema(description = "이 유형의 이벤트 수") @JsonProperty("event_count") long eventCount) {}

  public record MetricCounts(
      @Schema(description = "이 항목에 든 이벤트 수입니다. 여러 과금 미터에 걸린 이벤트도 한 번만 셉니다.")
          @JsonProperty("event_count")
          long eventCount,
      @Schema(description = "과금 미터별 이벤트 수입니다. 과금 미터 코드순입니다.") @JsonProperty("billable_metrics")
          List<MetricCount> billableMetrics) {}

  public record MetricCount(
      @Schema(description = "과금 미터 코드") @JsonProperty("billable_metric_code")
          String billableMetricCode,
      @Schema(description = "이 과금 미터에서 걸린 이벤트 수") @JsonProperty("event_count") long eventCount) {}

  public record FutureTimestamp(
      @Schema(description = "이 항목에 든 이벤트 수") @JsonProperty("event_count") long eventCount,
      @Schema(description = "이 항목의 이벤트 가운데 가장 이른 occurred_at(ISO 8601)입니다. 이벤트가 없으면 null입니다.")
          @JsonProperty("earliest_occurred_at")
          OffsetDateTime earliestOccurredAt,
      @Schema(description = "이 항목의 이벤트 가운데 가장 늦은 occurred_at(ISO 8601)입니다. 이벤트가 없으면 null입니다.")
          @JsonProperty("latest_occurred_at")
          OffsetDateTime latestOccurredAt) {}

  @JsonIgnore
  @Schema(hidden = true)
  public boolean isEmpty() {
    return unmatchedType.eventCount() == 0
        && nonNumericValue.eventCount() == 0
        && futureTimestamp.eventCount() == 0
        && negativeQuantity.eventCount() == 0;
  }
}
