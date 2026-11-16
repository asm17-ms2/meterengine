package com.meterengine.invoice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record DraftInvoiceResponse(
    @Schema(description = "청구 예정액을 계산한 달(yyyy-MM, KST)") String month,
    @Schema(description = "청구 예정액을 계산한 시각(KST, +09:00)") @JsonProperty("calculated_at")
        OffsetDateTime calculatedAt,
    @Schema(description = "청구서 자동 확정이 켜져 있으면 true입니다. 자동 확정은 모든 조직에 켜져 있고 끌 수 없습니다.")
        @JsonProperty("auto_finalize_enabled")
        boolean autoFinalizeEnabled,
    @Schema(
            description =
                "이 달을 다음에 자동 확정하는 시각(KST, +09:00)입니다. 그 달의 확정 시각(다음 달 1일 14:00) 전이면 그 시각입니다. 지났으면 요청한 업무 환경에 아직 확정을 시도하지 않은 고객이나 마지막 시도가 실패인 고객이 있을 때만 다음 정시이고, 모든 고객을 시도했고 실패도 없거나 그 업무 환경의 첫 고객을 등록하기 전의 달이면 null입니다. 단가 없는 사용량으로 보류된 고객은 지난달 단가를 등록할 수 없어 다시 시도하지 않으므로 직접 확정합니다.")
        @JsonProperty("auto_finalize_at")
        OffsetDateTime autoFinalizeAt,
    @Schema(
            description =
                "자동 확정이 지금 돌고 있고, 요청한 업무 환경에서 확정 시각이 지난 이 달에 아직 확정을 시도하지 않은 고객이 있으면 true입니다. 이번 실행이 이 달에 닿기 전에도 true입니다. 모든 고객을 시도한 뒤에도 남은 고객은 보류나 실패라 false입니다.")
        @JsonProperty("auto_finalize_running")
        boolean autoFinalizeRunning,
    @Schema(description = "아직 이 달을 확정하지 않은 고객의 청구 예정액 합(원, 부가세 제외)") @JsonProperty("total_amount")
        long totalAmount,
    @Schema(description = "아직 이 달을 확정하지 않은 고객별 청구 예정액입니다. 고객 이름 오름차순입니다.")
        List<DraftInvoiceCustomer> customers) {

  public record DraftInvoiceCustomer(
      @Schema(description = "고객 ID(UUID)") @JsonProperty("customer_id") UUID customerId,
      @Schema(description = "지금 고객 이름") @JsonProperty("customer_name") String customerName,
      @Schema(description = "그 고객의 청구 항목 금액 합(원, 부가세 제외)") long amount,
      @Schema(
              description =
                  "청구 항목입니다. 미터와 구분 값, 구간마다 생기는 항목과 기본료, 최소 사용료 조정 항목이 있고 line_type으로 나눕니다. 이벤트가 없는 고객은 빈 배열입니다.")
          List<DraftInvoiceLine> lines,
      @Schema(
              description =
                  "단가가 없어 청구 항목에서 빠진 구분 값의 사용량입니다. 금액에 들지 않습니다. 수량이 0인 구분 값은 싣지 않고, 없으면 빈 배열입니다.")
          @JsonProperty("unpriced_usages")
          List<DraftInvoiceUnpricedUsage> unpricedUsages,
      @Schema(
              description =
                  "변환 전 집계량이 음수여서 금액을 0으로 보고 청구 항목에서 뺀 구분 값의 사용량입니다. 금액에 들지 않습니다. 없으면 빈 배열입니다.")
          @JsonProperty("negative_usages")
          List<DraftInvoiceNegativeUsage> negativeUsages,
      @Schema(
              description =
                  "금액이 0원이 아니면서 단가 없는 사용량이 있어 자동 확정이 이 고객을 보류하면 true입니다. 검색의 미터 조건과 관계없이 고객의 모든 미터로 정합니다.")
          @JsonProperty("auto_finalize_held")
          boolean autoFinalizeHeld) {}

  public record DraftInvoiceUnpricedUsage(
      @Schema(description = "미터 코드") @JsonProperty("billable_metric_code")
          String billableMetricCode,
      @Schema(description = "미터가 집계하는 이벤트 속성 키입니다. count 미터는 null입니다.")
          @JsonProperty("target_property")
          String targetProperty,
      @Schema(description = "단가가 없는 구분 값입니다. 미터에 dimension_properties가 없으면 빈 객체입니다.")
          @JsonProperty("dimension_values")
          Map<String, Object> dimensionValues,
      @Schema(description = "unit_size 나눗셈과 끝수 처리를 적용한 과금 수량") BigDecimal quantity) {}

  public record DraftInvoiceNegativeUsage(
      @Schema(description = "미터 코드") @JsonProperty("billable_metric_code")
          String billableMetricCode,
      @Schema(description = "미터가 집계하는 이벤트 속성 키입니다. count 미터는 null입니다.")
          @JsonProperty("target_property")
          String targetProperty,
      @Schema(description = "집계량이 음수인 구분 값입니다. 미터에 dimension_properties가 없으면 빈 객체입니다.")
          @JsonProperty("dimension_values")
          Map<String, Object> dimensionValues,
      @Schema(description = "단위 변환 전 집계량입니다. 음수입니다.") BigDecimal quantity) {}

  public record DraftInvoiceLine(
      @Schema(description = "미터 코드입니다. 기본료와 최소 사용료 조정 항목은 null입니다.")
          @JsonProperty("billable_metric_code")
          String billableMetricCode,
      @Schema(description = "미터가 집계하는 이벤트 속성 키입니다. count 미터와 기본료, 최소 사용료 조정 항목은 null입니다.")
          @JsonProperty("target_property")
          String targetProperty,
      @Schema(
              description =
                  "미터의 dimension_properties마다 이벤트 properties에서 뽑은 구분 값입니다. 같은 구분 값의 단가를 곱합니다."
                      + " 미터에 dimension_properties가 없거나 기본료, 최소 사용료 조정 항목이면 빈 객체입니다.")
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
