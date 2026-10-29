package com.meterengine.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.payment.entity.PaymentReconciliationMismatch;
import java.util.List;

public record ListPaymentReconciliationMismatchesResponse(
    @JsonProperty("payment_reconciliation_mismatches")
        List<PaymentReconciliationMismatchResponse> paymentReconciliationMismatches) {

  public static ListPaymentReconciliationMismatchesResponse from(
      List<PaymentReconciliationMismatch> mismatches) {
    return new ListPaymentReconciliationMismatchesResponse(
        mismatches.stream().map(PaymentReconciliationMismatchResponse::from).toList());
  }
}
