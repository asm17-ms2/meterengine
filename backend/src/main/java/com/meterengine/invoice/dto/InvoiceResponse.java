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
    @JsonProperty("finalized_at") OffsetDateTime finalizedAt,
    @JsonProperty("supply_amount") long supplyAmount,
    @JsonProperty("tax_amount") long taxAmount,
    @JsonProperty("total_amount") long totalAmount,
    List<InvoiceLineResponse> lines) {

  public record InvoiceLineResponse(
      @JsonProperty("billable_metric_code") String billableMetricCode,
      @JsonProperty("target_property") String targetProperty,
      @Schema(description = "확정할 때 저장한 조합. 이 조합의 단가를 수량에 곱했다. 미터에 dimension_properties가 없었으면 빈 객체다")
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
          BigDecimal quantityDenominator) {}
}
