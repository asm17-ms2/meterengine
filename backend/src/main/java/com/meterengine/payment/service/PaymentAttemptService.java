package com.meterengine.payment.service;

import com.meterengine.global.error.ConflictException;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.NotFoundException;
import com.meterengine.invoice.entity.Invoice;
import com.meterengine.invoice.repository.InvoiceRepository;
import com.meterengine.metric.service.BillableMetricUsageService;
import com.meterengine.payment.client.PaymentApproval;
import com.meterengine.payment.client.PaymentGateway;
import com.meterengine.payment.client.PaymentRequest;
import com.meterengine.payment.dto.PaymentAttemptResponse;
import com.meterengine.payment.entity.PaymentAttempt;
import com.meterengine.payment.repository.PaymentAttemptRepository;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentAttemptService {

  private final PaymentAttemptRepository paymentAttemptRepository;
  private final InvoiceRepository invoiceRepository;
  private final PaymentGateway paymentGateway;

  PaymentAttemptService(
      PaymentAttemptRepository paymentAttemptRepository,
      InvoiceRepository invoiceRepository,
      PaymentGateway paymentGateway) {
    this.paymentAttemptRepository = paymentAttemptRepository;
    this.invoiceRepository = invoiceRepository;
    this.paymentGateway = paymentGateway;
  }

  @Transactional
  public PaymentAttemptResponse create(UUID organizationId, UUID invoiceId) {
    Invoice invoice =
        invoiceRepository
            .findByOrganizationIdAndId(organizationId, invoiceId)
            .orElseThrow(() -> new NotFoundException(ErrorCode.INVOICE_NOT_FOUND));
    if (invoice.getTotalAmount() <= 0) {
      throw new ConflictException(ErrorCode.INVOICE_AMOUNT_ZERO);
    }
    if (paymentAttemptRepository.findActive(organizationId, invoiceId).isPresent()) {
      throw new ConflictException(ErrorCode.INVOICE_ALREADY_PAID);
    }

    UUID attemptId = UUID.randomUUID();
    String orderName = invoice.getPeriod() + " 사용료";
    PaymentApproval approval =
        paymentGateway.approve(
            new PaymentRequest(
                attemptId,
                organizationId,
                invoice.getCustomerId(),
                invoice.getTotalAmount(),
                orderName));
    PaymentAttempt attempt =
        paymentAttemptRepository.saveAndFlush(
            PaymentAttempt.done(
                attemptId,
                organizationId,
                invoice.getCustomerId(),
                invoiceId,
                invoice.getTotalAmount(),
                orderName,
                approval.paymentKey()));
    return toPaymentAttemptResponse(attempt);
  }

  private static PaymentAttemptResponse toPaymentAttemptResponse(PaymentAttempt attempt) {
    return new PaymentAttemptResponse(
        attempt.getId(),
        attempt.getInvoiceId(),
        attempt.getStatus(),
        attempt.getAmount(),
        attempt.getPaymentKey(),
        inBillingZone(attempt.getRequestedAt()),
        inBillingZone(attempt.getCompletedAt()));
  }

  private static OffsetDateTime inBillingZone(OffsetDateTime time) {
    return time.atZoneSameInstant(BillableMetricUsageService.BILLING_ZONE).toOffsetDateTime();
  }
}
