package com.meterengine.webhook.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record WebhookDeliveryResponse(
    @Schema(description = "전송 ID(UUID)") UUID id,
    @Schema(description = "보낸 웹훅의 ID(UUID)입니다. 웹훅 요청 헤더 webhook-id 값과 같습니다.")
        @JsonProperty("webhook_event_id")
        UUID webhookEventId,
    @Schema(
            description =
                "웹훅 종류(webhook_test(테스트 웹훅), unbilled_usage_detected(과금 제외 사용량), payment_succeeded(결제 성공), payment_failed(결제 실패), payment_canceled(결제 취소))")
        String type,
    @Schema(
            description =
                "전송 상태입니다. pending(전송 대기 또는 재시도 대기), done(전송 완료), failed(전송 실패) 가운데 하나입니다.")
        String status,
    @Schema(description = "보낸 시도 수입니다. 재시도를 요청하면 처음부터 다시 셉니다.") @JsonProperty("attempt_count")
        int attemptCount,
    @Schema(description = "다음 시도 예정 시각(ISO 8601)입니다. done이나 failed면 null입니다.")
        @JsonProperty("next_attempt_at")
        OffsetDateTime nextAttemptAt,
    @Schema(description = "마지막으로 보낸 시각(ISO 8601)입니다. 아직 보내지 않았으면 null입니다.")
        @JsonProperty("last_attempted_at")
        OffsetDateTime lastAttemptedAt,
    @Schema(description = "마지막 시도에서 받은 HTTP 상태 코드입니다. 응답을 받지 못했으면 null입니다.")
        @JsonProperty("response_status")
        Integer responseStatus,
    @Schema(
            description =
                "마지막 시도의 실패 사유입니다. timeout, connection_error, unexpected_status, blocked_address, endpoint_disabled, secret_unavailable, lease_expired 가운데 하나이며 성공했으면 null입니다.")
        @JsonProperty("failure_reason")
        String failureReason,
    @Schema(description = "전송을 만든 시각(ISO 8601)") @JsonProperty("created_at")
        OffsetDateTime createdAt,
    @Schema(description = "시도 기록입니다. 오래된 시도부터 싣습니다.") List<Attempt> attempts) {
  public record Attempt(
      @Schema(description = "시도한 시각(ISO 8601)") @JsonProperty("attempted_at")
          OffsetDateTime attemptedAt,
      @Schema(description = "받은 HTTP 상태 코드입니다. 응답을 받지 못했으면 null입니다.")
          @JsonProperty("response_status")
          Integer responseStatus,
      @Schema(description = "실패 사유입니다. 성공했으면 null입니다.") @JsonProperty("failure_reason")
          String failureReason,
      @Schema(description = "시도에 걸린 시간(밀리초)") @JsonProperty("duration_ms") long durationMs) {}
}
