package com.meterengine.organization.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

@Entity
@IdClass(OrganizationMemberId.class)
public class OrganizationMember implements Persistable<OrganizationMemberId> {

  @Id
  @Column(name = "organization_id")
  private UUID organizationId;

  @Id
  @Column(name = "user_account_id")
  private UUID userAccountId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Transient private boolean isNew = true;

  protected OrganizationMember() {}

  public OrganizationMember(UUID organizationId, UUID userAccountId, OffsetDateTime createdAt) {
    this.organizationId = organizationId;
    this.userAccountId = userAccountId;
    this.createdAt = createdAt;
  }

  @PostLoad
  @PostPersist
  private void markNotNew() {
    this.isNew = false;
  }

  @Override
  public OrganizationMemberId getId() {
    return new OrganizationMemberId(organizationId, userAccountId);
  }

  @Override
  public boolean isNew() {
    return isNew;
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public UUID getUserAccountId() {
    return userAccountId;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }
}
