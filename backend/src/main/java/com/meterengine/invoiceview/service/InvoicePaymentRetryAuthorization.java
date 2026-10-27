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
    String organizationName) {}
