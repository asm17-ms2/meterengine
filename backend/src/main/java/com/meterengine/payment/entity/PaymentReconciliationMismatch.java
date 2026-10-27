package com.meterengine.payment.entity;

import com.meterengine.global.security.BusinessEnvironmentContext;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

@Entity
public class PaymentReconciliationMismatch implements Persistable<UUID> {

  public static final String MISSING_IN_METERENGINE = "missing_in_meterengine";
  public static final String MISSING_IN_TOSS_PAYMENTS = "missing_in_toss_payments";
  public static final String AMOUNT_MISMATCH = "amount_mismatch";
  public static final String STATUS_MISMATCH = "status_mismatch";

  @Id private UUID id;

  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;

  @Column(name = "environment_id")
  private UUID environmentId;

  @Column(nullable = false)
  private String mode;

  @Column(nullable = false)
  private String kind;

  @Column(name = "order_id", nullable = false)
  private String orderId;

  @Column(name = "payment_key")
  private String paymentKey;

  @Column(name = "transaction_key")
  private String transactionKey;

  @Column(name = "meterengine_amount")
  private Long meterengineAmount;

  @Column(name = "toss_payments_amount")
  private Long tossPaymentsAmount;

  @Column(name = "transacted_at")
  private OffsetDateTime transactedAt;

  @Column(name = "detected_at", nullable = false)
  private OffsetDateTime detectedAt;

  protected PaymentReconciliationMismatch() {}

  public PaymentReconciliationMismatch(
      UUID id,
      UUID organizationId,
      String mode,
      String kind,
      String orderId,
      String paymentKey,
      String transactionKey,
      Long meterengineAmount,
      Long tossPaymentsAmount,
      OffsetDateTime transactedAt,
      OffsetDateTime detectedAt) {
    this.id = id;
    this.organizationId = organizationId;
    this.environmentId = BusinessEnvironmentContext.environmentId();
    this.mode = mode;
    this.kind = kind;
    this.orderId = orderId;
    this.paymentKey = paymentKey;
    this.transactionKey = transactionKey;
    this.meterengineAmount = meterengineAmount;
    this.tossPaymentsAmount = tossPaymentsAmount;
    this.transactedAt = transactedAt;
    this.detectedAt = detectedAt;
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

  public String getMode() {
    return mode;
  }

  public String getKind() {
    return kind;
  }

  public String getOrderId() {
    return orderId;
  }

  public String getPaymentKey() {
    return paymentKey;
  }

  public String getTransactionKey() {
    return transactionKey;
  }

  public Long getMeterengineAmount() {
    return meterengineAmount;
  }

  public Long getTossPaymentsAmount() {
    return tossPaymentsAmount;
  }

  public OffsetDateTime getTransactedAt() {
    return transactedAt;
  }

  public OffsetDateTime getDetectedAt() {
    return detectedAt;
  }
}
