package com.meterengine.payment.entity;

import com.meterengine.global.security.BusinessEnvironmentContext;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Transient;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

@Entity
public class PaymentAttempt implements Persistable<UUID> {

  public static final String PENDING = "pending";
  public static final String DONE = "done";
  public static final String FAILED = "failed";

  @Id private UUID id;

  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;

  @Column(name = "environment_id")
  private UUID environmentId;

  @Column(name = "credential_updated_at")
  private OffsetDateTime credentialUpdatedAt;

  @Column(name = "customer_id", nullable = false)
  private UUID customerId;

  @Column(name = "invoice_id", nullable = false)
  private UUID invoiceId;

  @Column(nullable = false)
  private String mode;

  @Column(nullable = false)
  private long amount;

  @Column(name = "order_name", nullable = false)
  private String orderName;

  @Column(nullable = false)
  private String status;

  @Column(name = "payment_key")
  private String paymentKey;

  @Column(name = "failure_code")
  private String failureCode;

  @Column(name = "failure_message")
  private String failureMessage;

  @Column(name = "requested_at", nullable = false)
  private OffsetDateTime requestedAt;

  @Column(name = "completed_at")
  private OffsetDateTime completedAt;

  @Transient private String billingKeySnapshot;

  public PaymentAttempt pinBillingKey(String billingKey) {
    this.billingKeySnapshot = billingKey;
    return this;
  }

  public String getBillingKeySnapshot() {
    return billingKeySnapshot;
  }

  protected PaymentAttempt() {}

  private PaymentAttempt(
      UUID id,
      UUID organizationId,
      UUID customerId,
      UUID invoiceId,
      String mode,
      long amount,
      String orderName,
      OffsetDateTime requestedAt) {
    this.id = id;
    this.organizationId = organizationId;
    this.environmentId = BusinessEnvironmentContext.environmentId();
    this.customerId = customerId;
    this.invoiceId = invoiceId;
    this.mode = mode;
    this.amount = amount;
    this.orderName = orderName;
    this.status = PENDING;
    this.requestedAt = requestedAt;
  }

  public static PaymentAttempt pending(
      UUID id,
      UUID organizationId,
      UUID customerId,
      UUID invoiceId,
      String mode,
      long amount,
      String orderName) {
    return new PaymentAttempt(
        id,
        organizationId,
        customerId,
        invoiceId,
        mode,
        amount,
        orderName,
        OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS));
  }

  @Override
  public UUID getId() {
    return id;
  }

  @Override
  public boolean isNew() {
    return true;
  }

  public OffsetDateTime getCredentialUpdatedAt() {
    return credentialUpdatedAt;
  }

  public PaymentAttempt pinCredential(OffsetDateTime credentialUpdatedAt) {
    this.credentialUpdatedAt = credentialUpdatedAt;
    return this;
  }

  public UUID getEnvironmentId() {
    return environmentId;
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public UUID getCustomerId() {
    return customerId;
  }

  public UUID getInvoiceId() {
    return invoiceId;
  }

  public String getMode() {
    return mode;
  }

  public long getAmount() {
    return amount;
  }

  public String getOrderName() {
    return orderName;
  }

  public String getStatus() {
    return status;
  }

  public String getPaymentKey() {
    return paymentKey;
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
