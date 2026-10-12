package com.meterengine.payment.client;

public interface PaymentGateway {

  PaymentApproval approve(PaymentRequest request);
}
