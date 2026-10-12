package com.meterengine.payment.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class MockPaymentGatewayTest {

  private final PaymentGateway gateway = new MockPaymentGateway();

  @Test
  void 같은_시도를_다시_보내도_같은_결제_키로_승인한다() {
    PaymentRequest request = request(UUID.randomUUID());

    assertThat(gateway.approve(request)).isEqualTo(gateway.approve(request));
  }

  @Test
  void 시도마다_다른_결제_키로_승인한다() {
    assertThat(gateway.approve(request(UUID.randomUUID())).paymentKey())
        .isNotEqualTo(gateway.approve(request(UUID.randomUUID())).paymentKey());
  }

  private static PaymentRequest request(UUID attemptId) {
    return new PaymentRequest(
        attemptId, UUID.randomUUID(), UUID.randomUUID(), 11_000L, "2026-09 사용료");
  }
}
