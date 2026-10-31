package com.meterengine.invoiceview.service;

import com.meterengine.audit.service.AuditActor;
import java.util.UUID;

public record InvoicePaymentRetryAuthorization(
    UUID organizationId,
    UUID invoiceId,
    String mode,
    UUID failedAttemptId,
    long attemptCount,
    AuditActor actor,
    String organizationName,
    UUID environmentId) {
  public com.meterengine.global.database.DatabaseScope databaseScope() {
    return com.meterengine.global.database.DatabaseScope.valueOf(
        mode.toUpperCase(java.util.Locale.ROOT));
  }

  public InvoicePaymentRetryAuthorization(
      UUID organizationId,
      UUID invoiceId,
      String mode,
      UUID failedAttemptId,
      long attemptCount,
      AuditActor actor,
      String organizationName) {
    this(
        organizationId,
        invoiceId,
        mode,
        failedAttemptId,
        attemptCount,
        actor,
        organizationName,
        null);
  }
}
