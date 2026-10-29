package com.meterengine.apikey.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.apikey.entity.ApiKey;
import com.meterengine.global.validation.StorableText;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record CreateApiKeyRequest(
    @Schema(description = "키를 알아보려고 붙이는 이름")
        @NotBlank @Size(max = ApiKey.LABEL_MAX_LENGTH) @StorableText
        String label,
    @ArraySchema(
            arraySchema =
                @Schema(description = "키의 스코프. 하나 이상이고 중복은 없애고 저장한다. admin은 ingest와 read를 포함한다"),
            schema = @Schema(allowableValues = {"ingest", "read", "admin"}))
        @NotEmpty List<@NotNull @StorableText @Pattern(regexp = ApiKey.SCOPE_PATTERN) String> scopes,
    @Schema(
            description = "키 모드. test도 실제 데이터를 다루므로 샌드박스가 아니다. 결제 요청은 도입사의 현재 PG 모드와 같아야 한다",
            allowableValues = {"live", "test"})
        @NotBlank @Pattern(regexp = ApiKey.MODE_PATTERN) @StorableText
        String mode,
    @Schema(description = "이 키로 바꿀 옛 키의 ID. 주면 옛 키는 유예 시간이 지나면 끝난다. 없으면 빼거나 null로 보낸다")
        @JsonProperty("replaced_api_key_id")
        UUID replacedApiKeyId,
    @Schema(description = "이 키의 인스턴스별 분당 요청 한도. 양수이며 생략하거나 null이면 무제한이다")
        @Positive @JsonProperty("rate_limit_per_minute")
        Integer rateLimitPerMinute) {
  public CreateApiKeyRequest(
      String label, List<String> scopes, String mode, UUID replacedApiKeyId) {
    this(label, scopes, mode, replacedApiKeyId, null);
  }
}
