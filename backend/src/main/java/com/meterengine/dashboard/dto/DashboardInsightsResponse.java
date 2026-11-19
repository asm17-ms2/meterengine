package com.meterengine.dashboard.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record DashboardInsightsResponse(
    @Schema(description = "조회한 달(yyyy-MM, KST)") String month,
    @Schema(
            description =
                "인사이트를 계산한 시각(ISO 8601)입니다. 매일 05:00(KST)에 다시 계산하고, 그 뒤 처음 조회할 때 저장된 값이 없으면 그 자리에서 계산합니다.")
        @JsonProperty("calculated_at")
        OffsetDateTime calculatedAt,
    @Schema(description = "조회한 달까지 최근 석 달의 금액입니다. 오래된 달부터 줍니다.") @JsonProperty("monthly_amounts")
        List<MonthlyAmount> monthlyAmounts,
    @Schema(description = "조회한 달의 1일부터 계산한 날의 전날(KST)까지 날짜별 이벤트 수입니다. 계산한 날과 그 뒤의 날짜는 주지 않습니다.")
        @JsonProperty("daily_event_counts")
        List<DailyEventCount> dailyEventCounts) {
  public record MonthlyAmount(
      @Schema(description = "달(yyyy-MM, KST)") String month,
      @Schema(description = "청구액(원)입니다. 그 달 청구서의 부가세 포함 금액 합계입니다.")
          @JsonProperty("finalized_total_amount")
          long finalizedTotalAmount,
      @Schema(description = "받은 금액(원)입니다. 결제를 마친 청구서의 청구액에서 취소한 금액을 뺀 합계입니다.")
          @JsonProperty("collected_amount")
          long collectedAmount) {}

  public record DailyEventCount(
      @Schema(description = "날짜(yyyy-MM-dd, KST)") @JsonProperty("date") LocalDate eventDate,
      @Schema(description = "그 날 발생한 이벤트 수(occurred_at 기준)") @JsonProperty("event_count")
          long eventCount) {}
}
