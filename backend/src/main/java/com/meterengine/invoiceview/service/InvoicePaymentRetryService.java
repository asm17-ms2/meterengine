package com.meterengine.invoiceview.service;

import com.meterengine.global.security.BusinessEnvironment;
import com.meterengine.global.security.BusinessEnvironmentContext;
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
    return BusinessEnvironmentContext.run(
        new BusinessEnvironment(
            authorization.organizationId(),
            authorization.environmentId(),
            authorization.environmentId() == null ? null : authorization.mode()),
        () -> {
          paymentAttemptService.retry(
              authorization.organizationId(),
              authorization.invoiceId(),
              authorization.mode(),
              authorization.failedAttemptId(),
              authorization.attemptCount(),
              authorization.actor(),
              () -> invoiceViewLinkService.get(token, version));
          return invoiceViewLinkService.get(authorization);
        });
  }
}
