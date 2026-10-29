package com.meterengine.dashboard.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.List;

public record OperationsResponse(
    @JsonProperty("calculated_at") OffsetDateTime calculatedAt,
    @JsonProperty("pending_organization_count") long pendingOrganizationCount,
    @JsonProperty("active_organization_count") long activeOrganizationCount,
    @JsonProperty("user_account_count") long userAccountCount,
    @JsonProperty("pending_payment_count") long pendingPaymentCount,
    @JsonProperty("failed_payment_count") long failedPaymentCount,
    @JsonProperty("failed_webhook_count") long failedWebhookCount,
    List<OperationsJob> jobs) {

  public record OperationsJob(
      @JsonProperty("job_name") String jobName,
      @JsonProperty("last_success_at") OffsetDateTime lastSuccessAt,
      @JsonProperty("last_started_at") OffsetDateTime lastStartedAt,
      @JsonProperty("latest_status") String latestStatus) {}
}
