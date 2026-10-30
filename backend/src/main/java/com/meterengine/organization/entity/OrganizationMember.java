package com.meterengine.organization.entity;

import com.meterengine.global.security.AccessScopes;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
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

  @Column(nullable = false)
  private String role = "admin";

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(nullable = false)
  private List<String> scopes = AccessScopes.ADMIN;

  @Column(name = "revoked_at")
  private OffsetDateTime revokedAt;

  @Transient private boolean isNew = true;

  protected OrganizationMember() {}

  public OrganizationMember(UUID organizationId, UUID userAccountId, OffsetDateTime createdAt) {
    this.organizationId = organizationId;
    this.userAccountId = userAccountId;
    this.createdAt = createdAt;
  }

  public OrganizationMember(
      UUID organizationId, UUID userAccountId, OffsetDateTime createdAt, List<String> scopes) {
    this(organizationId, userAccountId, createdAt);
    changeScopes(scopes);
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

  public OffsetDateTime getRevokedAt() {
    return revokedAt;
  }

  public void revoke(OffsetDateTime now) {
    revokedAt = now;
  }

  public void restore() {
    revokedAt = null;
  }

  public void restore(List<String> scopes) {
    revokedAt = null;
    this.scopes = AccessScopes.normalize(scopes);
    role = this.scopes.contains("admin") ? "admin" : "member";
  }

  public String getRole() {
    return role;
  }

  public void changeRole(String role) {
    this.role = role;
    if ("owner".equals(role) || "admin".equals(role)) this.scopes = AccessScopes.ADMIN;
  }

  public List<String> getScopes() {
    return List.copyOf(scopes);
  }

  public void changeScopes(List<String> scopes) {
    this.scopes = AccessScopes.normalize(scopes);
    if (!"owner".equals(role)) this.role = this.scopes.contains("admin") ? "admin" : "member";
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }
}
