package com.meterengine.payment.service;

import com.meterengine.customer.repository.CustomerRepository;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.NotFoundException;
import com.meterengine.metric.service.BillableMetricUsageService;
import com.meterengine.payment.dto.InvoicePaymentResponse;
import com.meterengine.payment.dto.ListInvoicePaymentsResponse;
import com.meterengine.payment.entity.PaymentAttempt;
import com.meterengine.payment.repository.InvoicePaymentProjection;
import com.meterengine.payment.repository.PaymentAttemptRepository;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvoicePaymentService {

  private static final String NOT_REQUIRED = "not_required";
  private static final String UNPAID = "unpaid";
  private static final String PENDING = "pending";
  private static final String PAID = "paid";
  private static final String FAILED = "failed";
  private static final String PARTIALLY_CANCELED = "partially_canceled";
  private static final String CANCELED = "canceled";

  private final PaymentAttemptRepository paymentAttemptRepository;
  private final CustomerRepository customerRepository;
  private final PaymentRetryPolicy paymentRetryPolicy;

  InvoicePaymentService(
      PaymentAttemptRepository paymentAttemptRepository,
      CustomerRepository customerRepository,
      PaymentRetryPolicy paymentRetryPolicy) {
    this.paymentAttemptRepository = paymentAttemptRepository;
    this.customerRepository = customerRepository;
    this.paymentRetryPolicy = paymentRetryPolicy;
  }

  @Transactional(readOnly = true)
  public ListInvoicePaymentsResponse list(
      UUID organizationId, YearMonth month, UUID customerId, String mode) {
    if (customerId != null
        && !customerRepository.existsByOrganizationIdAndId(organizationId, customerId)) {
      throw new NotFoundException(ErrorCode.CUSTOMER_NOT_FOUND);
    }
    String period = month == null ? null : month.toString();
    return new ListInvoicePaymentsResponse(
        paymentAttemptRepository
            .findInvoicePayments(organizationId, customerId, period, mode)
            .stream()
            .map(invoicePayment -> toInvoicePaymentResponse(invoicePayment, mode))
            .toList());
  }

  private InvoicePaymentResponse toInvoicePaymentResponse(
      InvoicePaymentProjection invoicePayment, String mode) {
    String latestStatus = invoicePayment.getLatestStatus();
    boolean done = PaymentAttempt.DONE.equals(latestStatus);
    boolean failed = PaymentAttempt.FAILED.equals(latestStatus);
    String latestFailureCode = invoicePayment.getLatestFailureCode();
    OffsetDateTime latestCompletedAt = inBillingZone(invoicePayment.getLatestCompletedAt());
    return new InvoicePaymentResponse(
        invoicePayment.getInvoiceId(),
        status(invoicePayment),
        mode,
        invoicePayment.getAmount(),
        done ? invoicePayment.getCanceledAmount() : 0,
        invoicePayment.getAttemptCount(),
        failed ? latestFailureCode : null,
        failed
            ? paymentRetryPolicy.nextRetryAt(
                latestFailureCode,
                invoicePayment.getAttemptCount(),
                latestCompletedAt,
                inBillingZone(invoicePayment.getBillingKeyAuthenticatedAt()))
            : null,
        latestCompletedAt);
  }

  private static String status(InvoicePaymentProjection invoicePayment) {
    if (invoicePayment.getAmount() <= 0) {
      return NOT_REQUIRED;
    }
    String latestStatus = invoicePayment.getLatestStatus();
    if (latestStatus == null) {
      return UNPAID;
    }
    return switch (latestStatus) {
      case PaymentAttempt.PENDING -> PENDING;
      case PaymentAttempt.FAILED -> FAILED;
      default -> canceledStatus(invoicePayment.getCanceledAmount(), invoicePayment.getAmount());
    };
  }

  private static String canceledStatus(long canceledAmount, long amount) {
    if (canceledAmount <= 0) {
      return PAID;
    }
    return canceledAmount < amount ? PARTIALLY_CANCELED : CANCELED;
  }

  private static OffsetDateTime inBillingZone(Instant time) {
    return time == null
        ? null
        : time.atZone(BillableMetricUsageService.BILLING_ZONE).toOffsetDateTime();
  }
}
