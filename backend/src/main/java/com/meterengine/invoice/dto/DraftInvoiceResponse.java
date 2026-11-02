package com.meterengine.invoice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record DraftInvoiceResponse(
    String month,
    @JsonProperty("calculated_at") OffsetDateTime calculatedAt,
    @Schema(description = "인보이스 자동 확정이 켜져 있으면 true") @JsonProperty("auto_finalize_enabled")
        boolean autoFinalizeEnabled,
    @Schema(
            description =
                "이 달을 다음에 자동 확정하는 시각(KST). 그 달의 확정 시각(다음 달 1일 14:00) 전이면 그 시각이다. 지났으면 요청한 환경에서 아직 다 훑지 않았거나 마지막 시도가 실패인 고객이 있을 때만 다음 정시고, 다 훑었고 실패도 없거나 첫 고객 등록 전의 달이면 null이다. 단가 없는 사용량으로 보류된 고객은 지난달 단가를 등록할 수 없어 다시 시도하지 않으므로 수동 확정으로 끝낸다. 꺼져 있으면 null")
        @JsonProperty("auto_finalize_at")
        OffsetDateTime autoFinalizeAt,
    @Schema(
            description =
                "자동 확정이 지금 돌고 있고 요청한 환경에서 확정 시각이 지난 이 달을 아직 다 훑지 않았으면 true. 이번 실행이 이 달에 닿기 전에도 true다. 다 훑은 뒤 남은 고객은 보류나 실패라 false다. 자동 확정이 꺼져 있으면 false")
        @JsonProperty("auto_finalize_running")
        boolean autoFinalizeRunning,
    @JsonProperty("total_amount") long totalAmount,
    List<DraftInvoiceCustomer> customers) {

  public record DraftInvoiceCustomer(
      @JsonProperty("customer_id") UUID customerId,
      @JsonProperty("customer_name") String customerName,
      long amount,
      List<DraftInvoiceLine> lines,
      @Schema(description = "단가가 없어 라인에서 빠진 조합의 사용량. 금액에 들지 않는다. 수량이 0인 조합은 싣지 않고, 없으면 빈 배열이다")
          @JsonProperty("unpriced_usages")
          List<DraftInvoiceUnpricedUsage> unpricedUsages,
      @Schema(description = "변환 전 집계량이 음수여서 금액을 0으로 보고 라인에서 뺀 조합의 사용량. 금액에 들지 않는다. 없으면 빈 배열이다")
          @JsonProperty("negative_usages")
          List<DraftInvoiceNegativeUsage> negativeUsages,
      @Schema(
              description =
                  "자동 확정이 켜져 있고 금액이 0원이 아니면서 단가 없는 사용량이 있어 자동 확정이 이 고객을 보류하면 true. 검색의 미터 조건과 관계없이 고객의 모든 미터로 정한다")
          @JsonProperty("auto_finalize_held")
          boolean autoFinalizeHeld) {}

  public record DraftInvoiceUnpricedUsage(
      @JsonProperty("billable_metric_code") String billableMetricCode,
      @JsonProperty("target_property") String targetProperty,
      @JsonProperty("dimension_values") Map<String, Object> dimensionValues,
      BigDecimal quantity) {}

  public record DraftInvoiceNegativeUsage(
      @JsonProperty("billable_metric_code") String billableMetricCode,
      @JsonProperty("target_property") String targetProperty,
      @JsonProperty("dimension_values") Map<String, Object> dimensionValues,
      BigDecimal quantity) {}

  public record DraftInvoiceLine(
      @JsonProperty("billable_metric_code") String billableMetricCode,
      @JsonProperty("target_property") String targetProperty,
      @Schema(
              description =
                  "미터의 dimension_properties마다 이벤트 properties에서 뽑은 값. 같은 조합의 단가를 곱한다. 미터에"
                      + " dimension_properties가 없으면 빈 객체다")
          @JsonProperty("dimension_values")
          Map<String, Object> dimensionValues,
      @Schema(description = "표시용 수량. 금액 계산에는 quantity_numerator/quantity_denominator를 사용한다")
          BigDecimal quantity,
      @JsonProperty("unit_price") BigDecimal unitPrice,
      long amount,
      @Schema(
              allowableValues = {
                "per_unit",
                "volume",
                "graduated_tier",
                "base_fee",
                "minimum_adjustment"
              })
          @JsonProperty("line_type")
          String lineType,
      @JsonProperty("tier_index") int tierIndex,
      @Schema(description = "금액과 구간 판정에 사용한 정확 청구 수량의 분자") @JsonProperty("quantity_numerator")
          BigDecimal quantityNumerator,
      @Schema(description = "정확 청구 수량의 양수 분모. 분자/분모에 단가를 곱해 라인별 10원 미만을 버린다")
          @JsonProperty("quantity_denominator")
          BigDecimal quantityDenominator) {}
}
