package com.meterengine.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.payment.entity.PaymentReconciliationMismatch;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record ListPaymentReconciliationMismatchesResponse(
    @Schema(description = "결제 대사 불일치 목록(detected_at 내림차순)")
        @JsonProperty("payment_reconciliation_mismatches")
        List<PaymentReconciliationMismatchResponse> paymentReconciliationMismatches) {

  public static ListPaymentReconciliationMismatchesResponse from(
      List<PaymentReconciliationMismatch> mismatches) {
    return new ListPaymentReconciliationMismatchesResponse(
        mismatches.stream().map(PaymentReconciliationMismatchResponse::from).toList());
  }
}
