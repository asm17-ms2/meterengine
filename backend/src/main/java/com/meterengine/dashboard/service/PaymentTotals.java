package com.meterengine.dashboard.service;

import com.meterengine.payment.dto.InvoicePaymentResponse;
import java.util.List;
import java.util.Set;

/** 한 달 청구서들의 결제 상태별 금액 합계(원, 부가세 포함). */
record PaymentTotals(
    long finalizedTotalAmount, long collectedAmount, long canceledAmount, long outstandingAmount) {
  static final Set<String> COLLECTED = Set.of("paid", "partially_canceled", "canceled");
  static final Set<String> OUTSTANDING = Set.of("unpaid", "pending", "failed");

  static PaymentTotals from(List<InvoicePaymentResponse> payments) {
    return new PaymentTotals(
        payments.stream().mapToLong(InvoicePaymentResponse::amount).reduce(0L, Math::addExact),
        payments.stream()
            .filter(payment -> COLLECTED.contains(payment.status()))
            .mapToLong(payment -> payment.amount() - payment.canceledAmount())
            .reduce(0L, Math::addExact),
        payments.stream()
            .mapToLong(InvoicePaymentResponse::canceledAmount)
            .reduce(0L, Math::addExact),
        payments.stream()
            .filter(payment -> OUTSTANDING.contains(payment.status()))
            .mapToLong(InvoicePaymentResponse::amount)
            .reduce(0L, Math::addExact));
  }
}
