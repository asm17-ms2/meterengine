package com.meterengine.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.metric.service.BillableMetricUsageService;
import com.meterengine.payment.entity.PaymentAttempt;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PaymentAttemptResponse(
    UUID id,
    @JsonProperty("invoice_id") UUID invoiceId,
    @Schema(
            description = "결제한 결제 모드. test 또는 live",
            allowableValues = {"test", "live"})
        String mode,
    @Schema(
            description = "pending은 토스페이먼츠 결과를 아직 확인하지 못한 시도다",
            allowableValues = {"pending", "done", "failed"})
        String status,
    long amount,
    @Schema(description = "토스페이먼츠가 승인한 결제의 키. done일 때만 있다") @JsonProperty("payment_key")
        String paymentKey,
    @Schema(
            description =
                """
                failed일 때만 있다.
                토스페이먼츠가 준 값: 승인 거절 코드(예: REJECT_CARD_COMPANY), 또는 주문 조회로 확인한 결제 상태 ABORTED, EXPIRED.
                MeterEngine이 정한 값: HTTP_<상태>(토스페이먼츠 거절 응답에 코드가 없을 때 그 HTTP 상태), NOT_FOUND_PAYMENT(결제를 요청하고 payment.not-found-failure-delay(기본 10분)가 지나도 토스페이먼츠 주문 조회가 404 NOT_FOUND_PAYMENT일 때. 토스페이먼츠 조회 오류 코드와 이름이 같지만 결제 거절이 아니라 MeterEngine의 판정이다), toss_payments_credential_missing(토스페이먼츠를 부르기 전에 결제 모드의 키가 없었다), payment_method_missing(토스페이먼츠를 부르기 전에 결제 모드의 카드가 없었다).
                """)
        @JsonProperty("failure_code")
        String failureCode,
    @Schema(
            description =
                "failed일 때만 있다. 토스페이먼츠가 준 거절 문구이거나, failure_code가 MeterEngine 코드면 그 오류 문구다")
        @JsonProperty("failure_message")
        String failureMessage,
    @JsonProperty("requested_at") OffsetDateTime requestedAt,
    @JsonProperty("completed_at") OffsetDateTime completedAt) {

  public static PaymentAttemptResponse from(PaymentAttempt attempt) {
    return new PaymentAttemptResponse(
        attempt.getId(),
        attempt.getInvoiceId(),
        attempt.getMode(),
        attempt.getStatus(),
        attempt.getAmount(),
        attempt.getPaymentKey(),
        attempt.getFailureCode(),
        attempt.getFailureMessage(),
        inBillingZone(attempt.getRequestedAt()),
        attempt.getCompletedAt() == null ? null : inBillingZone(attempt.getCompletedAt()));
  }

  private static OffsetDateTime inBillingZone(OffsetDateTime time) {
    return time.atZoneSameInstant(BillableMetricUsageService.BILLING_ZONE).toOffsetDateTime();
  }
}
