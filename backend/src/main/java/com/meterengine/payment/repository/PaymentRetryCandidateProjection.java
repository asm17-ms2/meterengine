package com.meterengine.payment.repository;

import java.time.Instant;
import java.util.UUID;

public interface PaymentRetryCandidateProjection {

  UUID getId();

  UUID getOrganizationId();

  UUID getInvoiceId();

  String getMode();

  String getFailureCode();

  Instant getRequestedAt();

  Instant getCompletedAt();

  long getAttemptCount();

  Instant getBillingKeyAuthenticatedAt();
}
