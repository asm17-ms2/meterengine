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
    @Schema(description = "API 키 ID(UUID)") UUID id,
    @Schema(description = "키를 알아보려고 붙인 이름") String label,
    @Schema(
            description = "테스트 키인지 라이브 키인지 나타냅니다. live는 원문이 me_live_로, test는 me_test_로 시작합니다.",
            allowableValues = {"live", "test"})
        String mode,
    @ArraySchema(
            arraySchema =
                @Schema(
                    description =
                        "키의 권한입니다. 중복을 없앤 값입니다. admin은 ingest와 read를 포함하고 payments는 포함하지 않습니다."),
            schema = @Schema(allowableValues = {"ingest", "read", "admin", "payments"}))
        List<String> scopes,
    @Schema(description = "원문의 끝 네 글자입니다. 키를 알아보는 데 씁니다.") @JsonProperty("last_four")
        String lastFour,
    @Schema(description = "키를 발급한 시각(ISO 8601)") @JsonProperty("created_at")
        OffsetDateTime createdAt,
    @Schema(description = "키가 만료되는 시각(ISO 8601)입니다. 새로 발급한 키는 null입니다.") @JsonProperty("expires_at")
        OffsetDateTime expiresAt,
    @Schema(description = "키를 폐기한 시각(ISO 8601)입니다. 새로 발급한 키는 null입니다.") @JsonProperty("revoked_at")
        OffsetDateTime revokedAt,
    @Schema(description = "키 원문입니다. 이 응답에서만 볼 수 있습니다. Authorization 헤더에 Bearer 값으로 싣습니다.")
        String secret,
    @Schema(
            description =
                "분당 요청 한도입니다. null이면 라이브 키는 무제한이고, 테스트 키에는 테스트 환경 한도가 걸립니다."
                    + " 값이 있으면 테스트 키는 이 값과 테스트 환경 한도 가운데 작은 쪽이 적용됩니다. 1분 단위로 끊어 세지 않습니다. 처음에는 한도만큼 연달아 보낼 수 있고, 그 뒤에는 분당 한도의 속도로 다시 채워집니다.")
        @JsonProperty("rate_limit_per_minute")
        Integer rateLimitPerMinute,
    @JsonProperty("environment_id")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(
            description = "키를 발급한 업무 환경의 ID(UUID)입니다. 업무 환경에 속하지 않은 키에는 이 필드가 없습니다.",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
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
