package com.meterengine.payment.client;

import org.springframework.stereotype.Component;

@Component
public class MockPaymentGateway implements PaymentGateway {

  @Override
  public PaymentApproval approve(PaymentRequest request) {
    return new PaymentApproval("mock_" + request.attemptId());
  }
}
