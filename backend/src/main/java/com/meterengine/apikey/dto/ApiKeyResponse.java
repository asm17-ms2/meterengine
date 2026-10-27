package com.meterengine.apikey.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.apikey.entity.ApiKey;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ApiKeyResponse(
    UUID id,
    @Schema(description = "키를 알아보려고 붙인 이름") String label,
    @Schema(
            description = "키 모드. 원문 접두사가 me_live_나 me_test_다",
            allowableValues = {"live", "test"})
        String mode,
    @ArraySchema(
            arraySchema = @Schema(description = "키의 스코프. admin은 ingest와 read를 포함한다"),
            schema = @Schema(allowableValues = {"ingest", "read", "admin"}))
        List<String> scopes,
    @Schema(description = "원문의 끝 네 글자. 키를 알아보는 데 쓴다") @JsonProperty("last_four") String lastFour,
    @JsonProperty("created_at") OffsetDateTime createdAt,
    @Schema(description = "키가 끝나는 시각. 교체된 키면 유예가 끝나는 시각이고, 정하지 않았으면 null")
        @JsonProperty("expires_at")
        OffsetDateTime expiresAt,
    @Schema(description = "키를 폐기한 시각. 폐기하지 않았으면 null") @JsonProperty("revoked_at")
        OffsetDateTime revokedAt,
    @Schema(description = "인스턴스별 분당 요청 한도. null이면 무제한이다") @JsonProperty("rate_limit_per_minute")
        Integer rateLimitPerMinute,
    @Schema(
            description = "서버 조회 시점의 키 상태. 폐기된 키는 만료 시각과 관계없이 revoked다",
            allowableValues = {"active", "revoked", "expired"})
        String status) {

  public static ApiKeyResponse from(ApiKey apiKey) {
    return new ApiKeyResponse(
        apiKey.getId(),
        apiKey.getLabel(),
        apiKey.getMode(),
        apiKey.getScopes(),
        apiKey.getLastFour(),
        apiKey.getCreatedAt(),
        apiKey.getExpiresAt(),
        apiKey.getRevokedAt(),
        apiKey.getRateLimitPerMinute(),
        apiKey.statusAt(OffsetDateTime.now()));
  }
}
