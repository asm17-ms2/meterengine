package com.meterengine.audit.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.audit.entity.AuditLog;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

public record AuditLogResponse(
    UUID id,
    @JsonProperty("occurred_at") OffsetDateTime occurredAt,
    @JsonProperty("organization_id") UUID organizationId,
    @JsonProperty("actor_type") String actorType,
    @JsonProperty("actor_id") UUID actorId,
    String action,
    @JsonProperty("target_type") String targetType,
    @JsonProperty("target_id") String targetId,
    Map<String, Object> detail) {
  private static final TypeReference<Map<String, Object>> DETAIL_TYPE = new TypeReference<>() {};

  public static AuditLogResponse from(AuditLog log, JsonMapper mapper) {
    return new AuditLogResponse(
        log.getId(),
        log.getOccurredAt(),
        log.getOrganizationId(),
        log.getActorType(),
        log.getActorId(),
        log.getAction(),
        log.getTargetType(),
        log.getTargetId(),
        mapper.readValue(log.getDetail(), DETAIL_TYPE));
  }
}
