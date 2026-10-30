package com.meterengine.apikey.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.apikey.entity.ApiKey;
import com.meterengine.apikey.service.IssuedApiKey;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ApiKeySecretResponse(
    UUID id,
    @Schema(description = "키를 알아보려고 붙인 이름") String label,
    @Schema(
            description = "키 모드. 원문 접두사가 me_live_나 me_test_다",
            allowableValues = {"live", "test"})
        String mode,
    @ArraySchema(
            arraySchema = @Schema(description = "키의 스코프. 중복을 없앤 값이다. admin은 ingest와 read를 포함한다"),
            schema = @Schema(allowableValues = {"ingest", "read", "admin"}))
        List<String> scopes,
    @Schema(description = "원문의 끝 네 글자. 키를 알아보는 데 쓴다") @JsonProperty("last_four") String lastFour,
    @JsonProperty("created_at") OffsetDateTime createdAt,
    @Schema(description = "키가 끝나는 시각. 새로 발급한 키는 null") @JsonProperty("expires_at")
        OffsetDateTime expiresAt,
    @Schema(description = "키를 폐기한 시각. 새로 발급한 키는 null") @JsonProperty("revoked_at")
        OffsetDateTime revokedAt,
    @Schema(description = "키 원문. 이 응답에서만 볼 수 있다. Authorization: Bearer 값으로 싣는다") String secret,
    @Schema(description = "인스턴스별 분당 요청 한도. null이면 무제한이다") @JsonProperty("rate_limit_per_minute")
        Integer rateLimitPerMinute,
    @JsonProperty("environment_id")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        UUID environmentId) {
  public ApiKeySecretResponse(
      UUID id,
      String label,
      String mode,
      List<String> scopes,
      String lastFour,
      OffsetDateTime createdAt,
      OffsetDateTime expiresAt,
      OffsetDateTime revokedAt,
      String secret) {
    this(id, label, mode, scopes, lastFour, createdAt, expiresAt, revokedAt, secret, null, null);
  }

  public static ApiKeySecretResponse from(IssuedApiKey issued) {
    ApiKey apiKey = issued.apiKey();
    return new ApiKeySecretResponse(
        apiKey.getId(),
        apiKey.getLabel(),
        apiKey.getMode(),
        apiKey.getScopes(),
        apiKey.getLastFour(),
        apiKey.getCreatedAt(),
        apiKey.getExpiresAt(),
        apiKey.getRevokedAt(),
        issued.secret(),
        apiKey.getRateLimitPerMinute(),
        apiKey.getEnvironmentId());
  }

  @Override
  public String toString() {
    return "ApiKeySecretResponse[id="
        + id
        + ", label="
        + label
        + ", mode="
        + mode
        + ", scopes="
        + scopes
        + ", secret=(redacted)]";
  }
}
