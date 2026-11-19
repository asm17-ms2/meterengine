package com.meterengine.event.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;

public record EventIssuesResponse(
    @Schema(description = "조회한 달(yyyy-MM, KST)") String month,
    @Schema(description = "검사한 occurred_at의 상한(미포함, ISO 8601)입니다. 지금 시각과 그 달의 끝 가운데 이른 쪽입니다.")
        @JsonProperty("cutoff_at")
        OffsetDateTime cutoffAt,
    @Schema(description = "0부터 세는 페이지 번호") int page,
    @Schema(description = "한 페이지에 담는 개수") int size,
    @Schema(description = "조건에 맞는 문제 행 수입니다. 한 이벤트가 여러 과금 미터에서 문제가 되면 여러 번 셉니다.") long total,
    @Schema(description = "이 페이지의 문제 행") List<EventIssueResponse> issues,
    @Schema(description = "문제 종류별 행 수입니다. 고객, 검색어, 사유 조건을 적용합니다.") Summary summary,
    @Schema(description = "고객, 검색어, 사유 조건과 관계없이 그 달 전체에서 센 경고입니다.") Warnings warnings) {
  public record Summary(
      @Schema(description = "unknown_event_type(연결할 과금 미터 없음) 행 수")
          @JsonProperty("unknown_event_type")
          long unknownEventType,
      @Schema(description = "invalid_target_property(수량을 읽을 수 없음) 행 수")
          @JsonProperty("invalid_target_property")
          long invalidTargetProperty,
      @Schema(description = "unpriced_usage(적용 단가 없음) 행 수") @JsonProperty("unpriced_usage")
          long unpricedUsage) {
    public long total() {
      return Math.addExact(Math.addExact(unknownEventType, invalidTargetProperty), unpricedUsage);
    }
  }

  public record Warnings(
      @Schema(description = "경고를 센 범위입니다. 값은 organization_month(그 달 전체)입니다.") String scope,
      @Schema(description = "그 달에 발생했지만 occurred_at이 cutoff_at 이후라 아직 집계되지 않은 이벤트 수")
          @JsonProperty("future_event_count")
          long futureEventCount,
      @Schema(description = "수량이 음수인 이벤트를 과금 미터별로 센 합입니다. 한 이벤트가 여러 과금 미터에 걸리면 여러 번 셉니다.")
          @JsonProperty("negative_event_metric_count")
          long negativeEventMetricCount) {}
}
