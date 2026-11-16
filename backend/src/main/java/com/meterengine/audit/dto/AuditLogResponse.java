package com.meterengine.audit.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.audit.entity.AuditLog;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

public record AuditLogResponse(
    @Schema(description = "감사 기록 ID(UUID)") UUID id,
    @Schema(description = "기록한 시각(ISO 8601)") @JsonProperty("occurred_at")
        OffsetDateTime occurredAt,
    @Schema(description = "기록이 속한 조직 ID(UUID)입니다. 조직 없는 기록이면 null입니다.")
        @JsonProperty("organization_id")
        UUID organizationId,
    @Schema(
            description =
                "실행 주체 종류입니다. user_account(로그인 사용자), api_key(API 키), system(시스템), anonymous(미인증 요청),"
                    + " invoice_view_link(청구서 조회 링크) 가운데 하나입니다.")
        @JsonProperty("actor_type")
        String actorType,
    @Schema(
            description =
                "실행 주체의 ID(UUID)입니다. user_account면 계정 ID, api_key면 API 키 ID, invoice_view_link면 청구서 조회 링크 ID이고,"
                    + " system과 anonymous면 null입니다.")
        @JsonProperty("actor_id")
        UUID actorId,
    @Schema(description = "활동 코드(예: api_key_created, api_request_completed)") String action,
    @Schema(description = "대상 종류(예: api_key, invoice, http_request)") @JsonProperty("target_type")
        String targetType,
    @Schema(description = "대상의 식별자(문자열)입니다. 대상이 없으면 null입니다.") @JsonProperty("target_id")
        String targetId,
    @Schema(
            description =
                "활동마다 다른 상세 값(JSON 객체)입니다. API 요청 기록(api_request_completed)에는 method, route, status,"
                    + " duration_ms, request_id가 들어갑니다.")
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
