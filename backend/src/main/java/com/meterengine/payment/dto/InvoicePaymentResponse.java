package com.meterengine.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;

public record InvoicePaymentResponse(
    @Schema(description = "청구서 ID(UUID)") @JsonProperty("invoice_id") UUID invoiceId,
    @Schema(
            description =
                "지금 결제 모드에서 가장 늦게 요청한 시도로 정한 결제 상태입니다. total_amount가 0원이면 not_required, 시도가 없으면 unpaid, 시도가 pending이면 pending, failed면 failed, done이면 취소한 합에 따라 paid, partially_canceled, canceled입니다.",
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
            description = "status를 계산한 결제 모드입니다. 조회한 때 업무 환경의 결제 모드입니다.",
            allowableValues = {"test", "live"})
        String mode,
    @Schema(description = "청구서의 부가세 포함 금액(원)") long amount,
    @Schema(description = "취소를 마친 금액의 합(원)입니다. 결제된 청구서가 아니면 0입니다.") @JsonProperty("canceled_amount")
        long canceledAmount,
    @Schema(description = "지금 결제 모드로 이 청구서에 한 결제 시도 수") @JsonProperty("attempt_count")
        long attemptCount,
    @Schema(description = "가장 늦게 요청한 시도의 failure_code입니다. 그 시도가 failed일 때만 있습니다.")
        @JsonProperty("last_failure_code")
        String lastFailureCode,
    @Schema(
            description =
                "다음 자동 재시도 시각(KST, +09:00)입니다. 그 시도의 completed_at에 재시도 간격을 더한 시각입니다. 가장 늦게 요청한 시도가 failed이고, 실패 코드가 다시 시도할 대상이며, 시도 수가 상한보다 적을 때만 있습니다. 카드 오류처럼 다시 시도하지 않는 코드여도 그 시도가 끝난 뒤 고객이 카드를 다시 등록했으면 다시 시도할 대상이고, ALREADY_REMOVED_BILLING_KEY는 지금 등록 카드가 있으면 대상입니다. NOT_FOUND_PAYMENT는 그 시도가 끝난 지 7일 안일 때만 대상입니다.")
        @JsonProperty("next_retry_at")
        OffsetDateTime nextRetryAt,
    @Schema(description = "가장 늦게 요청한 시도의 completed_at(KST, +09:00)입니다. 시도가 없거나 pending이면 null입니다.")
        @JsonProperty("completed_at")
        OffsetDateTime completedAt) {}
