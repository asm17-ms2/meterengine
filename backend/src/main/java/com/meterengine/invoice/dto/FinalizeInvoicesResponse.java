package com.meterengine.invoice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record FinalizeInvoicesResponse(
    @Schema(description = "확정한 달(yyyy-MM, KST)") String month,
    @Schema(description = "이번 호출로 새로 확정한 청구서 수입니다. 이미 확정돼 있던 고객은 세지 않습니다.")
        @JsonProperty("finalized_count")
        int finalizedCount,
    @Schema(description = "이번 호출에서 확정하지 못한 고객 수입니다. 0보다 크면 같은 달로 다시 부릅니다.")
        @JsonProperty("failed_count")
        int failedCount,
    @Schema(description = "이번 호출로 새로 확정한 청구서입니다. 각 원소는 GET /v1/invoices/{id} 응답과 같습니다.")
        List<InvoiceResponse> invoices,
    @Schema(description = "이번 호출로 새로 확정한 청구서의 공급가액 합(원)") @JsonProperty("supply_amount")
        long supplyAmount,
    @Schema(description = "이번 호출로 새로 확정한 청구서의 부가세 합(원)") @JsonProperty("tax_amount") long taxAmount,
    @Schema(description = "이번 호출로 새로 확정한 청구서의 부가세 포함 금액 합(원)") @JsonProperty("total_amount")
        long totalAmount) {
  public FinalizeInvoicesResponse(String month, int finalizedCount) {
    this(month, finalizedCount, 0, List.of(), 0, 0, 0);
  }

  public static FinalizeInvoicesResponse from(
      String month, List<InvoiceResponse> invoices, int failedCount) {
    return new FinalizeInvoicesResponse(
        month,
        invoices.size(),
        failedCount,
        invoices,
        invoices.stream().mapToLong(InvoiceResponse::supplyAmount).reduce(0L, Math::addExact),
        invoices.stream().mapToLong(InvoiceResponse::taxAmount).reduce(0L, Math::addExact),
        invoices.stream().mapToLong(InvoiceResponse::totalAmount).reduce(0L, Math::addExact));
  }
}
