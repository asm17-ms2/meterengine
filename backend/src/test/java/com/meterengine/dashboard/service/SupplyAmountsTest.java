package com.meterengine.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meterengine.payment.dto.InvoicePaymentResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SupplyAmountsTest {

  @Test
  void 결제를_마친_청구서는_받은_금액에_결제_전과_실패와_확인_중은_받을_금액에_공급가액으로_더한다() {
    InvoicePaymentResponse paid = payment("paid", 1100, 0);
    InvoicePaymentResponse unpaid = payment("unpaid", 2200, 0);
    InvoicePaymentResponse pending = payment("pending", 3300, 0);
    InvoicePaymentResponse failed = payment("failed", 4400, 0);

    SupplyAmounts amounts =
        SupplyAmounts.from(
            List.of(paid, unpaid, pending, failed),
            Map.of(
                paid.invoiceId(), 1000L,
                unpaid.invoiceId(), 2000L,
                pending.invoiceId(), 3000L,
                failed.invoiceId(), 4000L));

    assertThat(amounts.collected()).isEqualTo(1000);
    assertThat(amounts.outstanding()).isEqualTo(9000);
  }

  @Test
  void 부분_취소한_청구서는_취소하지_않은_비율만큼만_받은_금액으로_센다() {
    InvoicePaymentResponse partiallyCanceled = payment("partially_canceled", 4400, 1100);

    SupplyAmounts amounts =
        SupplyAmounts.from(
            List.of(partiallyCanceled), Map.of(partiallyCanceled.invoiceId(), 4000L));

    assertThat(amounts.collected()).isEqualTo(3000);
    assertThat(amounts.outstanding()).isZero();
  }

  @Test
  void 전액_취소한_청구서는_받은_금액에도_받을_금액에도_없다() {
    InvoicePaymentResponse canceled = payment("canceled", 1100, 1100);

    SupplyAmounts amounts =
        SupplyAmounts.from(List.of(canceled), Map.of(canceled.invoiceId(), 1000L));

    assertThat(amounts.collected()).isZero();
    assertThat(amounts.outstanding()).isZero();
  }

  private static InvoicePaymentResponse payment(String status, long amount, long canceledAmount) {
    return new InvoicePaymentResponse(
        UUID.randomUUID(), status, "test", amount, canceledAmount, 0, null, null, null);
  }
}
