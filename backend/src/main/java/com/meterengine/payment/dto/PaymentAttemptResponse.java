package com.meterengine.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.metric.service.BillableMetricUsageService;
import com.meterengine.payment.entity.PaymentAttempt;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PaymentAttemptResponse(
    @Schema(description = "결제 시도 ID(UUID)입니다. 취소를 요청할 때 경로의 id로 씁니다.") UUID id,
    @Schema(description = "결제한 청구서 ID(UUID)") @JsonProperty("invoice_id") UUID invoiceId,
    @Schema(
            description = "결제에 쓴 결제 모드(test 또는 live)",
            allowableValues = {"test", "live"})
        String mode,
    @Schema(
            description = "결제 시도 상태입니다. pending은 토스페이먼츠 결과를 아직 확인하지 못한 시도입니다.",
            allowableValues = {"pending", "done", "failed"})
        String status,
    @Schema(description = "결제를 요청한 금액(원)입니다. 요청할 때 청구서의 total_amount입니다.") long amount,
    @Schema(description = "토스페이먼츠가 승인한 결제의 키입니다. done일 때만 있습니다.") @JsonProperty("payment_key")
        String paymentKey,
    @Schema(
            description =
                """
                failed일 때만 있습니다.
                토스페이먼츠가 준 값: 승인 거절 코드(예: REJECT_CARD_COMPANY), 또는 주문 조회로 확인한 결제 상태 ABORTED, EXPIRED.
                MeterEngine이 정한 값: HTTP_<상태>(토스페이먼츠 거절 응답에 코드가 없을 때 그 HTTP 상태), NOT_FOUND_PAYMENT(결제를 요청하고 10분이 지나도 토스페이먼츠 주문 조회가 404 NOT_FOUND_PAYMENT일 때. 토스페이먼츠 조회 오류 코드와 이름이 같지만 결제 거절이 아니라 MeterEngine의 판정입니다), toss_payments_credential_missing(토스페이먼츠를 부르기 전에 그 결제 모드의 토스페이먼츠 키가 없었습니다), payment_method_missing(토스페이먼츠를 부르기 전에 그 결제 모드의 카드가 없었습니다).
                """)
        @JsonProperty("failure_code")
        String failureCode,
    @Schema(
            description =
                "failed일 때만 있습니다. 토스페이먼츠가 준 거절 문구이거나, failure_code가 MeterEngine 코드면 그 오류 문구입니다.")
        @JsonProperty("failure_message")
        String failureMessage,
    @Schema(description = "결제를 요청한 시각(KST, +09:00)") @JsonProperty("requested_at")
        OffsetDateTime requestedAt,
    @Schema(description = "결과가 done이나 failed로 정해진 시각(KST, +09:00)입니다. pending이면 null입니다.")
        @JsonProperty("completed_at")
        OffsetDateTime completedAt) {

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
