package com.meterengine.invoiceview.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.invoice.dto.InvoiceResponse.InvoiceLineResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;

public record PublicInvoiceResponse(
    @JsonProperty("organization_name") String organizationName,
    @JsonProperty("customer_name") String customerName,
    @JsonProperty("invoice_number") String invoiceNumber,
    String month,
    @JsonProperty("finalized_at") OffsetDateTime finalizedAt,
    @JsonProperty("supply_amount") long supplyAmount,
    @JsonProperty("tax_amount") long taxAmount,
    @JsonProperty("total_amount") long totalAmount,
    List<InvoiceLineResponse> lines,
    PublicInvoicePayment payment,
    @JsonProperty("payment_retry") PublicInvoicePaymentRetry paymentRetry,
    @JsonProperty("expires_at") OffsetDateTime expiresAt) {
  public record PublicInvoicePaymentRetry(
      boolean allowed,
      String reason,
      @Schema(description = "현재 실패에 대한 재시도 버전. 허용되지 않으면 null이다", nullable = true) String version) {}

  public record PublicInvoicePayment(
      String status,
      String mode,
      long amount,
      @Schema(description = "취소액을 제외한 납부액. pending이면 확인 전이므로 null이다", nullable = true)
          @JsonProperty("paid_amount")
          Long paidAmount,
      @JsonProperty("canceled_amount") long canceledAmount,
      @Schema(description = "미납 또는 실패 시 원 청구액. 취소액은 포함하지 않는다. pending이면 null이다", nullable = true)
          @JsonProperty("outstanding_amount")
          Long outstandingAmount,
      @JsonProperty("completed_at") OffsetDateTime completedAt) {}
}
