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

  @Column(name = "environment_id", updatable = false)
  private UUID environmentId;

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
    this(
        id,
        occurredAt,
        organizationId,
        actorType,
        actorId,
        action,
        targetType,
        targetId,
        detail,
        null);
  }

  public AuditLog(
      UUID id,
      OffsetDateTime occurredAt,
      UUID organizationId,
      String actorType,
      UUID actorId,
      String action,
      String targetType,
      String targetId,
      String detail,
      UUID environmentId) {
    this.id = id;
    this.occurredAt = occurredAt;
    this.organizationId = organizationId;
    this.environmentId = environmentId;
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

  public UUID getEnvironmentId() {
    return environmentId;
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
