package com.meterengine.audit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

@Entity
public class AuditLog implements Persistable<UUID> {

  @Id private UUID id;

  @Column(name = "occurred_at", nullable = false, updatable = false)
  private OffsetDateTime occurredAt;

  @Column(name = "organization_id", updatable = false)
  private UUID organizationId;

  @Column(name = "actor_type", nullable = false, updatable = false)
  private String actorType;

  @Column(name = "actor_id", updatable = false)
  private UUID actorId;

  @Column(nullable = false, updatable = false)
  private String action;

  @Column(name = "target_type", updatable = false)
  private String targetType;

  @Column(name = "target_id", updatable = false)
  private String targetId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, updatable = false)
  private String detail;

  protected AuditLog() {}

  public AuditLog(
      UUID id,
      OffsetDateTime occurredAt,
      UUID organizationId,
      String actorType,
      UUID actorId,
      String action,
      String targetType,
      String targetId,
      String detail) {
    this.id = id;
    this.occurredAt = occurredAt;
    this.organizationId = organizationId;
    this.actorType = actorType;
    this.actorId = actorId;
    this.action = action;
    this.targetType = targetType;
    this.targetId = targetId;
    this.detail = detail;
  }

  @Override
  public UUID getId() {
    return id;
  }

  @Override
  public boolean isNew() {
    return true;
  }

  public OffsetDateTime getOccurredAt() {
    return occurredAt;
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public String getActorType() {
    return actorType;
  }

  public UUID getActorId() {
    return actorId;
  }

  public String getAction() {
    return action;
  }

  public String getTargetType() {
    return targetType;
  }

  public String getTargetId() {
    return targetId;
  }

  public String getDetail() {
    return detail;
  }
}
