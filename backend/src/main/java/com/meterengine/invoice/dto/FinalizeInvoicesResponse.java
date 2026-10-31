package com.meterengine.invoice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record FinalizeInvoicesResponse(
    String month,
    @JsonProperty("finalized_count") int finalizedCount,
    @JsonProperty("failed_count") int failedCount,
    List<InvoiceResponse> invoices,
    @JsonProperty("supply_amount") long supplyAmount,
    @JsonProperty("tax_amount") long taxAmount,
    @JsonProperty("total_amount") long totalAmount) {
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
