package com.meterengine.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.OffsetDateTime;
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

  @Column(name = "customer_id", nullable = false)
  private UUID customerId;

  @Column(name = "invoice_id", nullable = false)
  private UUID invoiceId;

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

  protected PaymentAttempt() {}

  private PaymentAttempt(
      UUID organizationId,
      UUID customerId,
      UUID invoiceId,
      long amount,
      String orderName,
      String status,
      String failureCode,
      String failureMessage,
      OffsetDateTime requestedAt,
      OffsetDateTime completedAt) {
    this.id = UUID.randomUUID();
    this.organizationId = organizationId;
    this.customerId = customerId;
    this.invoiceId = invoiceId;
    this.amount = amount;
    this.orderName = orderName;
    this.status = status;
    this.failureCode = failureCode;
    this.failureMessage = failureMessage;
    this.requestedAt = requestedAt;
    this.completedAt = completedAt;
  }

  // 토스에 보내기 전의 시도를 만든다.
  public static PaymentAttempt pending(
      UUID organizationId, UUID customerId, UUID invoiceId, long amount, String orderName) {
    return new PaymentAttempt(
        organizationId,
        customerId,
        invoiceId,
        amount,
        orderName,
        PENDING,
        null,
        null,
        OffsetDateTime.now(),
        null);
  }

  // 토스에 보내지 못하고 끝난 시도를 만든다.
  public static PaymentAttempt failedBeforeRequest(
      UUID organizationId,
      UUID customerId,
      UUID invoiceId,
      long amount,
      String orderName,
      String failureCode,
      String failureMessage) {
    OffsetDateTime now = OffsetDateTime.now();
    return new PaymentAttempt(
        organizationId,
        customerId,
        invoiceId,
        amount,
        orderName,
        FAILED,
        failureCode,
        failureMessage,
        now,
        now);
  }

  @Override
  public UUID getId() {
    return id;
  }

  @Override
  public boolean isNew() {
    return true;
  }

  public boolean isDone() {
    return DONE.equals(status);
  }

  public boolean isPending() {
    return PENDING.equals(status);
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
