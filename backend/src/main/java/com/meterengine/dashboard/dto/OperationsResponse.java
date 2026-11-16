package com.meterengine.dashboard.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;

public record OperationsResponse(
    @Schema(description = "현황을 계산한 시각(ISO 8601)") @JsonProperty("calculated_at")
        OffsetDateTime calculatedAt,
    @Schema(description = "확인 중인 라이브 환경 이용 신청 수")
        @JsonProperty("pending_live_environment_application_count")
        long pendingLiveEnvironmentApplicationCount,
    @Schema(description = "이용 중(active)인 조직 수") @JsonProperty("active_organization_count")
        long activeOrganizationCount,
    @Schema(description = "전체 사용자 계정 수") @JsonProperty("user_account_count") long userAccountCount,
    @Schema(description = "결과 확인 중(pending)인 라이브 결제 시도 수") @JsonProperty("pending_payment_count")
        long pendingPaymentCount,
    @Schema(description = "최근 24시간 동안 요청해 실패한 라이브 결제 시도 수") @JsonProperty("failed_payment_count")
        long failedPaymentCount,
    @Schema(description = "지금 failed 상태인 웹훅 전송 수입니다. 보관한 웹훅 URL과 테스트 환경의 전송은 세지 않습니다.")
        @JsonProperty("failed_webhook_count")
        long failedWebhookCount,
    @Schema(description = "예약 작업별 마지막 실행 상태(작업 이름순)") List<OperationsJob> jobs) {

  public record OperationsJob(
      @Schema(
              description =
                  "예약 작업 이름입니다. payment_attempt_confirmation, payment_retry, payment_reconciliation, webhook_delivery, unbilled_usage_report, invoice_auto_finalize 가운데 하나입니다.")
          @JsonProperty("job_name")
          String jobName,
      @Schema(description = "마지막으로 성공한 실행이 끝난 시각(ISO 8601)입니다. 성공한 적이 없으면 null입니다.")
          @JsonProperty("last_success_at")
          OffsetDateTime lastSuccessAt,
      @Schema(description = "가장 최근 실행을 시작한 시각(ISO 8601)입니다. 실행한 적이 없으면 null입니다.")
          @JsonProperty("last_started_at")
          OffsetDateTime lastStartedAt,
      @Schema(
              description =
                  "가장 최근 실행의 상태입니다. running, succeeded, failed 가운데 하나이며 실행한 적이 없으면 null입니다.")
          @JsonProperty("latest_status")
          String latestStatus) {}
}
