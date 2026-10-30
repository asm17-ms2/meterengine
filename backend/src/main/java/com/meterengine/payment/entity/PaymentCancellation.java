package com.meterengine.payment.entity;

import com.meterengine.global.security.BusinessEnvironmentContext;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

@Entity
public class PaymentCancellation implements Persistable<UUID> {

  public static final String PENDING = "pending";
  public static final String DONE = "done";
  public static final String FAILED = "failed";

  @Id private UUID id;

  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;

  @Column(name = "environment_id")
  private UUID environmentId;

  @Column(name = "payment_attempt_id", nullable = false)
  private UUID paymentAttemptId;

  @Column(nullable = false)
  private String mode;

  @Column(nullable = false)
  private long amount;

  @Column(nullable = false)
  private String reason;

  @Column(name = "idempotency_fingerprint")
  private String idempotencyFingerprint;

  @Column(nullable = false)
  private String status;

  @Column(name = "transaction_key")
  private String transactionKey;

  @Column(name = "failure_code")
  private String failureCode;

  @Column(name = "failure_message")
  private String failureMessage;

  @Column(name = "requested_at", nullable = false)
  private OffsetDateTime requestedAt;

  @Column(name = "completed_at")
  private OffsetDateTime completedAt;

  protected PaymentCancellation() {}

  private PaymentCancellation(
      UUID id,
      UUID organizationId,
      UUID paymentAttemptId,
      String mode,
      long amount,
      String reason,
      OffsetDateTime requestedAt,
      String idempotencyFingerprint) {
    this.id = id;
    this.organizationId = organizationId;
    this.environmentId = BusinessEnvironmentContext.environmentId();
    this.paymentAttemptId = paymentAttemptId;
    this.mode = mode;
    this.amount = amount;
    this.reason = reason;
    this.idempotencyFingerprint = idempotencyFingerprint;
    this.status = PENDING;
    this.requestedAt = requestedAt;
  }

  public static PaymentCancellation pending(
      UUID id,
      UUID organizationId,
      UUID paymentAttemptId,
      String mode,
      long amount,
      String reason) {
    return pending(id, organizationId, paymentAttemptId, mode, amount, reason, null);
  }

  public static PaymentCancellation pending(
      UUID id,
      UUID organizationId,
      UUID paymentAttemptId,
      String mode,
      long amount,
      String reason,
      String idempotencyFingerprint) {
    return new PaymentCancellation(
        id,
        organizationId,
        paymentAttemptId,
        mode,
        amount,
        reason,
        OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS),
        idempotencyFingerprint);
  }

  @Override
  public UUID getId() {
    return id;
  }

  @Override
  public boolean isNew() {
    return true;
  }

  public UUID getEnvironmentId() {
    return environmentId;
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public UUID getPaymentAttemptId() {
    return paymentAttemptId;
  }

  public String getMode() {
    return mode;
  }

  public long getAmount() {
    return amount;
  }

  public String getReason() {
    return reason;
  }

  public String getIdempotencyFingerprint() {
    return idempotencyFingerprint;
  }

  public String getStatus() {
    return status;
  }

  public String getTransactionKey() {
    return transactionKey;
  }

  public String getFailureCode() {
    return failureCode;
  }

  public String getFailureMessage() {
    return failureMessage;
  }

  public OffsetDateTime getRequestedAt() {
    return requestedAt;
  }

  public OffsetDateTime getCompletedAt() {
    return completedAt;
  }
}
