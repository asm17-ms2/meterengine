package com.meterengine.invoice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record InvoiceResponse(
    UUID id,
    @Schema(description = "고객에게 표시하는 안정적인 인보이스 번호. 세금계산서나 영수증 번호가 아니다")
        @JsonProperty("invoice_number")
        String invoiceNumber,
    @JsonProperty("customer_id") UUID customerId,
    @JsonProperty("customer_name") String customerName,
    String month,
    @Schema(description = "확정이 읽은 이벤트의 받은 시각 상한. 그 달에 발생했고 이 시각 전에 받은 이벤트만 금액에 들었다. KST(+09:00)")
        @JsonProperty("cutoff_at")
        OffsetDateTime cutoffAt,
    @JsonProperty("finalized_at") OffsetDateTime finalizedAt,
    @JsonProperty("supply_amount") long supplyAmount,
    @JsonProperty("tax_amount") long taxAmount,
    @JsonProperty("total_amount") long totalAmount,
    List<InvoiceLineResponse> lines) {

  public record InvoiceLineResponse(
      @JsonProperty("billable_metric_code") String billableMetricCode,
      @Schema(
              description =
                  "확정할 때의 미터 이름. 미터 이름을 나중에 바꿔도 그대로다. 기본료와 최소 사용료 조정 줄, 이 값을 저장하기 전에 확정한 줄은"
                      + " null이다",
              nullable = true)
          @JsonProperty("billable_metric_name")
          String billableMetricName,
      @JsonProperty("target_property") String targetProperty,
      @Schema(description = "확정할 때 저장한 조합. 이 조합의 단가를 수량에 곱했다. 미터에 dimension_properties가 없었으면 빈 객체다")
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
