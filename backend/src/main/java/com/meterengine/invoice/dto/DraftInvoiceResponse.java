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
          List<DraftInvoiceNegativeUsage> negativeUsages) {}

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
      @JsonProperty("line_type") String lineType,
      @JsonProperty("tier_index") int tierIndex,
      @Schema(description = "금액과 구간 판정에 사용한 정확 청구 수량의 분자") @JsonProperty("quantity_numerator")
          BigDecimal quantityNumerator,
      @Schema(description = "정확 청구 수량의 양수 분모. 분자/분모에 단가를 곱해 라인별 10원 미만을 버린다")
          @JsonProperty("quantity_denominator")
          BigDecimal quantityDenominator) {
    public DraftInvoiceLine(
        String billableMetricCode,
        String targetProperty,
        Map<String, Object> dimensionValues,
        BigDecimal quantity,
        BigDecimal unitPrice,
        long amount,
        String lineType,
        int tierIndex) {
      this(
          billableMetricCode,
          targetProperty,
          dimensionValues,
          quantity,
          unitPrice,
          amount,
          lineType,
          tierIndex,
          quantity,
          BigDecimal.ONE);
    }

    public DraftInvoiceLine(
        String billableMetricCode,
        String targetProperty,
        Map<String, Object> dimensionValues,
        BigDecimal quantity,
        BigDecimal unitPrice,
        long amount) {
      this(
          billableMetricCode,
          targetProperty,
          dimensionValues,
          quantity,
          unitPrice,
          amount,
          "per_unit",
          0);
    }
  }
}
