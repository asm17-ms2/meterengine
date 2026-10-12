package com.meterengine.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
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
      UUID id,
      UUID organizationId,
      UUID customerId,
      UUID invoiceId,
      long amount,
      String orderName,
      String status,
      String paymentKey,
      OffsetDateTime requestedAt,
      OffsetDateTime completedAt) {
    this.id = id;
    this.organizationId = organizationId;
    this.customerId = customerId;
    this.invoiceId = invoiceId;
    this.amount = amount;
    this.orderName = orderName;
    this.status = status;
    this.paymentKey = paymentKey;
    this.requestedAt = requestedAt;
    this.completedAt = completedAt;
  }

  public static PaymentAttempt done(
      UUID id,
      UUID organizationId,
      UUID customerId,
      UUID invoiceId,
      long amount,
      String orderName,
      String paymentKey) {
    OffsetDateTime now = OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS);
    return new PaymentAttempt(
        id, organizationId, customerId, invoiceId, amount, orderName, DONE, paymentKey, now, now);
  }

  @Override
  public UUID getId() {
    return id;
  }

  @Override
  public boolean isNew() {
    return true;
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
