package com.meterengine.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

@Entity
public class PaymentSetting implements Persistable<UUID> {

  public static final String TEST = "test";
  public static final String LIVE = "live";

  @Id
  @Column(name = "organization_id")
  private UUID organizationId;

  @Column(nullable = false)
  private String mode;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  @Transient private boolean isNew = true;

  protected PaymentSetting() {}

  public PaymentSetting(UUID organizationId, String mode, OffsetDateTime updatedAt) {
    this.organizationId = organizationId;
    this.mode = mode;
    this.updatedAt = updatedAt;
  }

  @PostLoad
  @PostPersist
  private void markNotNew() {
    this.isNew = false;
  }

  public void update(String mode, OffsetDateTime updatedAt) {
    this.mode = mode;
    this.updatedAt = updatedAt;
  }

  @Override
  public UUID getId() {
    return organizationId;
  }

  @Override
  public boolean isNew() {
    return isNew;
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public String getMode() {
    return mode;
  }

  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }
}
