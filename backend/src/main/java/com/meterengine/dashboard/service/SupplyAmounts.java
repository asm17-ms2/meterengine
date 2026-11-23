package com.meterengine.dashboard.service;

import com.meterengine.payment.dto.InvoicePaymentResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 한 달 청구서들의 받은 금액과 받을 금액(원, 공급가액). */
record SupplyAmounts(long collected, long outstanding) {

  static SupplyAmounts from(
      List<InvoicePaymentResponse> payments, Map<UUID, Long> supplyAmountByInvoiceId) {
    return new SupplyAmounts(
        payments.stream()
            .filter(payment -> PaymentTotals.COLLECTED.contains(payment.status()))
            .mapToLong(
                payment ->
                    keptSupplyAmount(payment, supplyAmountByInvoiceId.get(payment.invoiceId())))
            .reduce(0L, Math::addExact),
        payments.stream()
            .filter(payment -> PaymentTotals.OUTSTANDING.contains(payment.status()))
            .mapToLong(payment -> supplyAmountByInvoiceId.get(payment.invoiceId()))
            .reduce(0L, Math::addExact));
  }

  // 취소액은 부가세 포함 금액으로만 남는다. 취소하지 않은 비율만큼 공급가액을 받은 것으로 본다.
  private static long keptSupplyAmount(InvoicePaymentResponse payment, long supplyAmount) {
    if (payment.amount() <= 0) return 0;
    return BigDecimal.valueOf(supplyAmount)
        .multiply(BigDecimal.valueOf(payment.amount() - payment.canceledAmount()))
        .divide(BigDecimal.valueOf(payment.amount()), 0, RoundingMode.HALF_UP)
        .longValueExact();
  }
}
