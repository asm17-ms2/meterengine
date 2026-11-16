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
                @Schema(
                    description =
                        "키의 권한입니다. 하나 이상 고르며 중복은 없애고 저장합니다. admin은 ingest와 read를 포함하고 payments는 포함하지 않습니다."),
            schema = @Schema(allowableValues = {"ingest", "read", "admin", "payments"}))
        @NotEmpty List<@NotNull @StorableText @Pattern(regexp = ApiKey.SCOPE_PATTERN) String> scopes,
    @Schema(
            description =
                "발급할 키가 테스트 키(test)인지 라이브 키(live)인지 정합니다. 테스트 키도 데이터를 실제로 만들고 바꿉니다."
                    + " 결제를 요청하는 키는 업무 환경의 결제 모드와 같아야 합니다.",
            allowableValues = {"live", "test"})
        @NotBlank @Pattern(regexp = ApiKey.MODE_PATTERN) @StorableText
        String mode,
    @Schema(description = "이 키로 바꿀 옛 키 ID(UUID)입니다. 주면 옛 키는 24시간이 지나면 만료됩니다. 없으면 빼거나 null로 보냅니다.")
        @JsonProperty("replaced_api_key_id")
        UUID replacedApiKeyId,
    @Schema(
            description =
                "이 키의 분당 요청 한도입니다. 양수여야 하며, 빼거나 null로 보내면 라이브 키는 무제한이고 테스트 키에는 테스트 환경 한도가 걸립니다."
                    + " 값을 주면 테스트 키는 이 값과 테스트 환경 한도 가운데 작은 쪽이 적용됩니다. 1분 단위로 끊어 세지 않습니다. 처음에는 한도만큼 연달아 보낼 수 있고, 그 뒤에는 분당 한도의 속도로 다시 채워집니다.")
        @Positive @JsonProperty("rate_limit_per_minute")
        Integer rateLimitPerMinute) {
  public CreateApiKeyRequest(
      String label, List<String> scopes, String mode, UUID replacedApiKeyId) {
    this(label, scopes, mode, replacedApiKeyId, null);
  }
}
