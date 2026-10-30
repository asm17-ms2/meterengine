package com.meterengine.payment.repository;

import java.time.Instant;
import java.util.UUID;

public interface InvoicePaymentProjection {

  UUID getInvoiceId();

  long getAmount();

  String getLatestStatus();

  String getLatestFailureCode();

  Instant getLatestCompletedAt();

  long getAttemptCount();

  long getCanceledAmount();

  Instant getBillingKeyAuthenticatedAt();
}
