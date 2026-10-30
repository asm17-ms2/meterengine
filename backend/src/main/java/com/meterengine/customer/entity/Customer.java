package com.meterengine.customer.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

@Entity
public class Customer {

  public static final int NAME_MAX_LENGTH = 255;
  public static final int EXTERNAL_ID_MAX_LENGTH = 255;
  public static final int BILLING_EMAIL_MAX_LENGTH = 254;
  public static final String BILLING_EMAIL_PATTERN = ".+@.+\\..+";
  public static final String BILLING_EMAIL_FORMAT_MESSAGE = "올바른 형식의 이메일 주소여야 합니다";

  @Id private UUID id;

  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;

  @Column(name = "environment_id", updatable = false)
  private UUID environmentId;

  @Version
  @Column(nullable = false)
  private long version;

  public long getVersion() {
    return version;
  }

  @Column(nullable = false)
  private String name;

  @Column(name = "billing_email")
  private String billingEmail;

  @Column(name = "external_id")
  private String externalId;

  @Generated(event = EventType.INSERT)
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "archived_at")
  private OffsetDateTime archivedAt;

  public OffsetDateTime getArchivedAt() {
    return archivedAt;
  }

  public void archive() {
    if (archivedAt == null) archivedAt = OffsetDateTime.now();
  }

  public void restore() {
    archivedAt = null;
  }

  protected Customer() {}

  public Customer(UUID id, UUID organizationId, String name, String billingEmail) {
    this(id, organizationId, name, billingEmail, null);
  }

  public Customer(
      UUID id, UUID organizationId, String name, String billingEmail, UUID environmentId) {
    this.id = id;
    this.organizationId = organizationId;
    this.environmentId = environmentId;
    this.name = name;
    this.billingEmail = billingEmail;
  }

  public void update(String name, String billingEmail) {
    this.name = name;
    this.billingEmail = billingEmail;
  }

  public UUID getId() {
    return id;
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public UUID getEnvironmentId() {
    return environmentId;
  }

  public String getName() {
    return name;
  }

  public String getBillingEmail() {
    return billingEmail;
  }

  public String getExternalId() {
    return externalId;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }
}
