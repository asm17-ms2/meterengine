package com.meterengine.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.metric.service.BillableMetricUsageService;
import com.meterengine.payment.entity.PaymentCancellation;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PaymentCancellationResponse(
    @Schema(description = "결제 취소 ID(UUID)") UUID id,
    @Schema(description = "취소한 결제 시도 ID(UUID)") @JsonProperty("payment_attempt_id")
        UUID paymentAttemptId,
    @Schema(
            description = "취소한 결제 시도의 결제 모드(test 또는 live)",
            allowableValues = {"test", "live"})
        String mode,
    @Schema(description = "취소한 금액(원)") long amount,
    @Schema(description = "취소 사유") String reason,
    @Schema(
            description = "취소 상태입니다. pending은 토스페이먼츠 결과를 아직 확인하지 못한 취소입니다.",
            allowableValues = {"pending", "done", "failed"})
        String status,
    @Schema(
            description =
                """
                failed일 때만 있습니다.
                토스페이먼츠가 준 값: 취소 거절 코드(예: NOT_CANCELABLE_AMOUNT).
                MeterEngine이 정한 값: HTTP_<상태>(토스페이먼츠 거절 응답에 코드가 없을 때 그 HTTP 상태), NOT_FOUND_CANCELLATION(취소를 요청하고 10분이 지나도 토스페이먼츠 결제 조회에 이 취소에 맞는 취소 거래가 없을 때. 토스페이먼츠 코드가 아닙니다), toss_payments_credential_missing(토스페이먼츠를 부르기 전에 그 결제 모드의 토스페이먼츠 키가 없었습니다).
                """)
        @JsonProperty("failure_code")
        String failureCode,
    @Schema(
            description =
                "failed일 때만 있습니다. 토스페이먼츠가 준 거절 문구이거나, failure_code가 MeterEngine 코드면 그 오류 문구입니다.")
        @JsonProperty("failure_message")
        String failureMessage,
    @Schema(description = "취소를 요청한 시각(KST, +09:00)") @JsonProperty("requested_at")
        OffsetDateTime requestedAt,
    @Schema(description = "결과가 done이나 failed로 정해진 시각(KST, +09:00)입니다. pending이면 null입니다.")
        @JsonProperty("completed_at")
        OffsetDateTime completedAt) {

  public static PaymentCancellationResponse from(PaymentCancellation cancellation) {
    return new PaymentCancellationResponse(
        cancellation.getId(),
        cancellation.getPaymentAttemptId(),
        cancellation.getMode(),
        cancellation.getAmount(),
        cancellation.getReason(),
        cancellation.getStatus(),
        cancellation.getFailureCode(),
        cancellation.getFailureMessage(),
        inBillingZone(cancellation.getRequestedAt()),
        cancellation.getCompletedAt() == null
            ? null
            : inBillingZone(cancellation.getCompletedAt()));
  }

  private static OffsetDateTime inBillingZone(OffsetDateTime time) {
    return time.atZoneSameInstant(BillableMetricUsageService.BILLING_ZONE).toOffsetDateTime();
  }
}
