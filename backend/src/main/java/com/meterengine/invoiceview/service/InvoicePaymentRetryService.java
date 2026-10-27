package com.meterengine.invoiceview.service;

import com.meterengine.invoiceview.dto.PublicInvoiceResponse;
import com.meterengine.payment.service.PaymentAttemptService;
import org.springframework.stereotype.Service;

@Service
public class InvoicePaymentRetryService {
  private final InvoiceViewLinkService invoiceViewLinkService;
  private final PaymentAttemptService paymentAttemptService;

  InvoicePaymentRetryService(
      InvoiceViewLinkService invoiceViewLinkService, PaymentAttemptService paymentAttemptService) {
    this.invoiceViewLinkService = invoiceViewLinkService;
    this.paymentAttemptService = paymentAttemptService;
  }

  public PublicInvoiceResponse retry(String token, String version) {
    InvoicePaymentRetryAuthorization authorization = invoiceViewLinkService.get(token, version);
    paymentAttemptService.retry(
        authorization.organizationId(),
        authorization.invoiceId(),
        authorization.mode(),
        authorization.failedAttemptId(),
        authorization.attemptCount(),
        authorization.actor(),
        () -> invoiceViewLinkService.get(token, version));
    return invoiceViewLinkService.get(authorization);
  }
}
