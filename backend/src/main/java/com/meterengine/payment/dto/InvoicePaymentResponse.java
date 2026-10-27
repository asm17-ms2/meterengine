package com.meterengine.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;

public record InvoicePaymentResponse(
    @JsonProperty("invoice_id") UUID invoiceId,
    @Schema(
            description =
                "현재 결제 모드의 가장 늦게 요청한 시도로 정한 결제 상태. total_amount가 0원이면 not_required, 시도가 없으면 unpaid, 시도가 pending이면 pending, failed면 failed, done이면 취소한 합에 따라 paid, partially_canceled, canceled다",
            allowableValues = {
              "not_required",
              "unpaid",
              "pending",
              "paid",
              "failed",
              "partially_canceled",
              "canceled"
            })
        String status,
    @Schema(
            description = "상태를 정한 결제 모드. 조회한 때 도입사의 결제 모드다",
            allowableValues = {"test", "live"})
        String mode,
    @Schema(description = "인보이스의 total_amount. 원 단위 정수") long amount,
    @Schema(description = "결제된 시도에서 취소를 마친 금액의 합. 결제된 시도가 아니면 0이다") @JsonProperty("canceled_amount")
        long canceledAmount,
    @Schema(description = "현재 결제 모드로 이 인보이스에 한 결제 시도 수") @JsonProperty("attempt_count")
        long attemptCount,
    @Schema(description = "가장 늦게 요청한 시도가 failed일 때만 있다. 그 시도의 failure_code다")
        @JsonProperty("last_failure_code")
        String lastFailureCode,
    @Schema(
            description =
                "가장 늦게 요청한 시도가 failed이고, 실패 코드가 다시 시도할 대상이며, 시도 수가 상한보다 적을 때만 있다. 카드 오류처럼 다시 시도하지 않는 코드여도 그 시도가 끝난 뒤 고객이 카드를 다시 등록했으면 다시 시도할 대상이고, ALREADY_REMOVED_BILLING_KEY는 지금 등록 카드가 있으면 대상이다. NOT_FOUND_PAYMENT는 그 시도가 끝난 지 7일 안일 때만 대상이다. 그 시도의 completed_at에 재시도 간격을 더한 시각이다")
        @JsonProperty("next_retry_at")
        OffsetDateTime nextRetryAt,
    @Schema(description = "가장 늦게 요청한 시도의 completed_at. 시도가 없거나 pending이면 null이다")
        @JsonProperty("completed_at")
        OffsetDateTime completedAt) {}
