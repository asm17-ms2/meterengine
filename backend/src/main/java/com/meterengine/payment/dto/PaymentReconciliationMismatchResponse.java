package com.meterengine.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.metric.service.BillableMetricUsageService;
import com.meterengine.payment.entity.PaymentReconciliationMismatch;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PaymentReconciliationMismatchResponse(
    UUID id,
    @Schema(
            description = "대사한 결제 모드. test 또는 live",
            allowableValues = {"test", "live"})
        String mode,
    @Schema(
            description =
                "missing_in_meterengine은 토스페이먼츠에만 있는 거래, missing_in_toss_payments는 MeterEngine에만 있는 done 결제나 취소, amount_mismatch는 금액이 다른 거래, status_mismatch는 결제 상태가 다른 거래다. 토스페이먼츠는 승인했는데 MeterEngine 시도가 done이 아니거나, MeterEngine은 done인데 토스페이먼츠 거래가 승인 상태가 아니다",
            allowableValues = {
              "missing_in_meterengine",
              "missing_in_toss_payments",
              "amount_mismatch",
              "status_mismatch"
            })
        String kind,
    @Schema(
            description =
                "토스페이먼츠 거래에서 찾았으면 토스페이먼츠가 준 orderId 원문이고, missing_in_toss_payments면 MeterEngine 결제 시도 id다")
        @JsonProperty("order_id")
        String orderId,
    @Schema(description = "결제 키. 모르면 null") @JsonProperty("payment_key") String paymentKey,
    @Schema(description = "거래 키. 취소 거래나 토스페이먼츠 거래에서 찾았을 때만 있다") @JsonProperty("transaction_key")
        String transactionKey,
    @Schema(description = "MeterEngine 쪽 금액. 원 단위 정수이고, MeterEngine에 기록이 없으면 null")
        @JsonProperty("meterengine_amount")
        Long meterengineAmount,
    @Schema(description = "토스페이먼츠 쪽 금액. 원 단위 정수이고, 토스페이먼츠에 거래가 없으면 null")
        @JsonProperty("toss_payments_amount")
        Long tossPaymentsAmount,
    @Schema(description = "토스페이먼츠 거래 시각이거나, missing_in_toss_payments면 MeterEngine이 결제나 취소를 마친 시각")
        @JsonProperty("transacted_at")
        OffsetDateTime transactedAt,
    @Schema(description = "대사 작업이 불일치를 찾은 시각") @JsonProperty("detected_at")
        OffsetDateTime detectedAt) {

  public static PaymentReconciliationMismatchResponse from(PaymentReconciliationMismatch mismatch) {
    return new PaymentReconciliationMismatchResponse(
        mismatch.getId(),
        mismatch.getMode(),
        mismatch.getKind(),
        mismatch.getOrderId(),
        mismatch.getPaymentKey(),
        mismatch.getTransactionKey(),
        mismatch.getMeterengineAmount(),
        mismatch.getTossPaymentsAmount(),
        mismatch.getTransactedAt() == null ? null : inBillingZone(mismatch.getTransactedAt()),
        inBillingZone(mismatch.getDetectedAt()));
  }

  private static OffsetDateTime inBillingZone(OffsetDateTime time) {
    return time.atZoneSameInstant(BillableMetricUsageService.BILLING_ZONE).toOffsetDateTime();
  }
}
