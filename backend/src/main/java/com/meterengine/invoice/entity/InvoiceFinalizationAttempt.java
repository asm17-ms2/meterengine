package com.meterengine.invoice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

@Entity
public class InvoiceFinalizationAttempt implements Persistable<UUID> {

  public static final String FINALIZED = "finalized";
  public static final String NO_EVENTS = "no_events";
  public static final String SKIPPED = "skipped";
  public static final String ZERO_AMOUNT = "zero_amount";
  public static final String HELD = "held";
  public static final String FAILED = "failed";

  private static final int MAX_REASON_LENGTH = 2000;

  @Id private UUID id;

  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;

  @Column(name = "environment_id", updatable = false)
  private UUID environmentId;

  @Column(name = "customer_id", nullable = false)
  private UUID customerId;

  @Column(nullable = false)
  private String period;

  @Column(nullable = false)
  private String result;

  private String reason;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "unpriced_usages")
  private String unpricedUsages;

  @Column(name = "attempted_at", nullable = false)
  private OffsetDateTime attemptedAt;

  protected InvoiceFinalizationAttempt() {}

  public InvoiceFinalizationAttempt(
      UUID organizationId,
      UUID environmentId,
      UUID customerId,
      String period,
      String result,
      String reason,
      String unpricedUsages,
      OffsetDateTime attemptedAt) {
    this.id = UUID.randomUUID();
    this.organizationId = organizationId;
    this.environmentId = environmentId;
    this.customerId = customerId;
    this.period = period;
    this.result = result;
    this.reason =
        reason == null || reason.length() <= MAX_REASON_LENGTH
            ? reason
            : reason.substring(0, MAX_REASON_LENGTH);
    this.unpricedUsages = unpricedUsages;
    this.attemptedAt = attemptedAt;
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

  public UUID getEnvironmentId() {
    return environmentId;
  }

  public UUID getCustomerId() {
    return customerId;
  }

  public String getPeriod() {
    return period;
  }

  public String getResult() {
    return result;
  }

  public String getReason() {
    return reason;
  }

  public String getUnpricedUsages() {
    return unpricedUsages;
  }

  public OffsetDateTime getAttemptedAt() {
    return attemptedAt;
  }
}
