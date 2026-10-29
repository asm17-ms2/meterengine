package com.meterengine.dashboard.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.invoice.dto.DraftInvoiceResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record DashboardResponse(
    String month,
    @JsonProperty("calculated_at") OffsetDateTime calculatedAt,
    @JsonProperty("customer_count") long customerCount,
    @JsonProperty("billable_metric_count") long billableMetricCount,
    @JsonProperty("active_api_key_count") long activeApiKeyCount,
    @JsonProperty("event_count") long eventCount,
    @JsonProperty("supplier_configured") boolean supplierConfigured,
    @JsonProperty("payment_configured") boolean paymentConfigured,
    @JsonProperty("payment_mode") String paymentMode,
    @JsonProperty("draft_supply_amount") long draftSupplyAmount,
    @JsonProperty("finalized_total_amount") long finalizedTotalAmount,
    @JsonProperty("collected_amount") long collectedAmount,
    @JsonProperty("canceled_amount") long canceledAmount,
    @Schema(
            description =
                "선택한 달 인보이스 가운데 미납(unpaid), 결과 확인 중(pending), 결제 실패(failed)의 청구액 합계."
                    + " 인보이스 검색의 outstanding_amount는 결과 확인 중이 있으면 null이고 미납과 실패만 더해 뜻이 다르다")
        @JsonProperty("outstanding_amount")
        long outstandingAmount,
    @JsonProperty("failed_payment_count") long failedPaymentCount,
    @JsonProperty("unpriced_usage_count") long unpricedUsageCount,
    @JsonProperty("negative_usage_count") long negativeUsageCount,
    @JsonProperty("invoice_payments") List<DashboardInvoice> invoicePayments,
    @JsonProperty("unresolved_invoice_payments") List<DashboardInvoice> unresolvedInvoicePayments,
    @JsonProperty("draft_invoice") DraftInvoiceResponse draftInvoice) {

  public record DashboardInvoice(
      @JsonProperty("invoice_id") UUID invoiceId,
      @JsonProperty("customer_id") UUID customerId,
      @JsonProperty("customer_name") String customerName,
      String month,
      String status,
      String mode,
      long amount,
      @JsonProperty("canceled_amount") long canceledAmount,
      @JsonProperty("attempt_count") long attemptCount,
      @JsonProperty("last_failure_code") String lastFailureCode,
      @JsonProperty("next_retry_at") OffsetDateTime nextRetryAt,
      @JsonProperty("completed_at") OffsetDateTime completedAt) {}
}
