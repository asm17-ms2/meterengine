package com.meterengine.invoice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record InvoiceResponse(
    @Schema(description = "청구서 ID(UUID)입니다. 결제 요청과 청구서 조회 링크 발급에 씁니다.") UUID id,
    @Schema(description = "고객에게 보여 주는 청구서 번호입니다. 바뀌지 않으며, 세금계산서나 영수증 번호가 아닙니다.")
        @JsonProperty("invoice_number")
        String invoiceNumber,
    @Schema(description = "고객 ID(UUID)") @JsonProperty("customer_id") UUID customerId,
    @Schema(description = "확정할 때 저장한 고객 이름입니다. 뒤에 고객 이름을 고쳐도 바뀌지 않습니다.")
        @JsonProperty("customer_name")
        String customerName,
    @Schema(description = "청구 대상 달(yyyy-MM, KST)") String month,
    @Schema(
            description =
                "확정이 읽은 이벤트의 수신 시각(received_at) 상한입니다. 그 달에 발생했고 수신 시각이 이 시각보다 이른 이벤트만 금액에 들었습니다. KST(+09:00)로 나옵니다.")
        @JsonProperty("cutoff_at")
        OffsetDateTime cutoffAt,
    @Schema(description = "확정한 시각(KST, +09:00)") @JsonProperty("finalized_at")
        OffsetDateTime finalizedAt,
    @Schema(description = "공급가액(원)입니다. 청구 항목 금액의 합입니다.") @JsonProperty("supply_amount")
        long supplyAmount,
    @Schema(description = "부가세(원)입니다. 공급가액의 10%입니다.") @JsonProperty("tax_amount") long taxAmount,
    @Schema(description = "부가세 포함 금액(원)입니다. 공급가액과 부가세의 합이며 결제 금액입니다.") @JsonProperty("total_amount")
        long totalAmount,
    @Schema(
            description =
                "확정할 때 저장한 청구 항목입니다. 미터 코드 오름차순, 같은 미터 안에서는 dimension_values 오름차순이고 수량이 0인 항목은 싣지 않습니다.")
        List<InvoiceLineResponse> lines) {

  public record InvoiceLineResponse(
      @Schema(description = "미터 코드입니다. 기본료와 최소 사용료 조정 항목은 null입니다.")
          @JsonProperty("billable_metric_code")
          String billableMetricCode,
      @Schema(
              description =
                  "확정할 때의 미터 이름입니다. 미터 이름을 나중에 바꿔도 그대로입니다. 기본료와 최소 사용료 조정 항목, 이 값을 저장하기 전에 확정한 항목은"
                      + " null입니다.",
              nullable = true)
          @JsonProperty("billable_metric_name")
          String billableMetricName,
      @Schema(description = "미터가 집계하는 이벤트 속성 키입니다. count 미터와 기본료, 최소 사용료 조정 항목은 null입니다.")
          @JsonProperty("target_property")
          String targetProperty,
      @Schema(
              description =
                  "확정할 때 저장한 구분 값입니다. 이 구분 값의 단가를 수량에 곱했습니다. 미터에 dimension_properties가 없었거나 기본료, 최소 사용료 조정 항목이면 빈 객체입니다.")
          @JsonProperty("dimension_values")
          Map<String, Object> dimensionValues,
      @Schema(description = "표시용 수량입니다. 금액 계산에는 quantity_numerator와 quantity_denominator를 씁니다.")
          BigDecimal quantity,
      @Schema(description = "과금 수량 1당 단가(원)입니다. 구간 모델은 그 구간의 단가이고, 기본료와 최소 사용료 조정 항목은 금액과 같습니다.")
          @JsonProperty("unit_price")
          BigDecimal unitPrice,
      @Schema(description = "청구 항목 금액(원)입니다. 10원 미만을 버린 값입니다.") long amount,
      @Schema(
              description =
                  "청구 항목 종류입니다. per_unit, volume, graduated_tier는 사용료, base_fee는 기본료, minimum_adjustment는 최소 사용료 조정 항목입니다.",
              allowableValues = {
                "per_unit",
                "volume",
                "graduated_tier",
                "base_fee",
                "minimum_adjustment"
              })
          @JsonProperty("line_type")
          String lineType,
      @Schema(description = "graduated_tier 항목의 구간 순서(0부터)입니다. 다른 종류는 0입니다.")
          @JsonProperty("tier_index")
          int tierIndex,
      @Schema(description = "금액과 구간 판정에 쓴 과금 수량을 분수로 나타낸 분자") @JsonProperty("quantity_numerator")
          BigDecimal quantityNumerator,
      @Schema(description = "과금 수량을 분수로 나타낸 분모(양수)입니다. 분자를 분모로 나눈 값에 단가를 곱하고 청구 항목마다 10원 미만을 버립니다.")
          @JsonProperty("quantity_denominator")
          BigDecimal quantityDenominator) {}
}
