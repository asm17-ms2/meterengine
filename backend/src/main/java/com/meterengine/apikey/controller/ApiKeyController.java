package com.meterengine.apikey.controller;

import com.meterengine.apikey.dto.ApiKeyResponse;
import com.meterengine.apikey.dto.ApiKeySecretResponse;
import com.meterengine.apikey.dto.CreateApiKeyRequest;
import com.meterengine.apikey.dto.ListApiKeysResponse;
import com.meterengine.apikey.dto.UpdateApiKeyScopesRequest;
import com.meterengine.apikey.service.ApiKeyService;
import com.meterengine.global.error.ErrorResponse;
import com.meterengine.global.security.CurrentOrganizationId;
import com.meterengine.global.security.EnvironmentReady;
import com.meterengine.global.security.MemberAdminAccess;
import com.meterengine.global.security.MemberReadAccess;
import com.meterengine.global.validation.StorableText;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@EnvironmentReady
@RequestMapping("/v1/api-keys")
public class ApiKeyController {

  private final ApiKeyService apiKeyService;

  ApiKeyController(ApiKeyService apiKeyService) {
    this.apiKeyService = apiKeyService;
  }

  @GetMapping
  @MemberReadAccess
  @Operation(
      summary = "API 키 목록 조회",
      description =
          """
          세션 조직의 현재 업무 환경에 속한 API 키를 만든 순서로 돌려줍니다. 기본은 전체이며 사용 중, 폐기됨, 만료됨 상태를 고를 수 있습니다.
          사용 중은 지금 인증에 쓸 수 있는 키입니다. 폐기한 키는 만료 시각과 관계없이 폐기됨으로 분류합니다. 키 원문은 싣지 않습니다.
          로그인 세션만 부를 수 있고 API 키로는 부를 수 없습니다. 페이지를 나누지 않습니다.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "API 키 목록입니다. 하나도 없으면 api_keys가 빈 배열입니다.")
  })
  public ListApiKeysResponse listApiKeys(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(
              description = "조회할 키 상태입니다. 생략하면 전체를 돌려줍니다.",
              schema = @Schema(allowableValues = {"active", "revoked", "expired", "all"}))
          @RequestParam(defaultValue = "all")
          @StorableText
          String status) {
    return ListApiKeysResponse.from(apiKeyService.list(organizationId, status));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @MemberAdminAccess
  @Operation(
      summary = "API 키 발급",
      description =
          """
          세션 조직의 현재 업무 환경에 API 키를 발급하고 원문을 돌려줍니다. 원문은 이 응답에서만 볼 수 있고 서버는 해시만 저장합니다.
          테스트 환경에서는 mode가 test, 라이브 환경에서는 live여야 합니다.
          replaced_api_key_id를 주면 그 키를 이 키로 교체합니다. 옛 키는 지금부터 24시간이 지나면 만료되고,
          그보다 이른 만료 시각이 이미 있으면 그대로 둡니다. 로그인 세션만 부를 수 있고 API 키로는 부를 수 없습니다.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "발급한 키와 원문"),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description =
            """
            code=validation_error: label이 비었거나 255자를 넘거나, scopes가 비었거나 ingest, read, admin, payments가 아닌 값을 담았거나,
            mode가 live나 test가 아니거나, rate_limit_per_minute가 양수가 아니거나, 문자열에 NUL 문자나 짝이 없는 UTF-16 서로게이트를 담았습니다.
            """),
    @ApiResponse(
        responseCode = "404",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description =
            "code=api_key_not_found: replaced_api_key_id에 해당하는 키가 세션 조직의 현재 업무 환경에 없거나 이미 폐기됐습니다.")
  })
  public ApiKeySecretResponse createApiKey(
      @CurrentOrganizationId UUID organizationId, @Valid @RequestBody CreateApiKeyRequest request) {
    return ApiKeySecretResponse.from(
        apiKeyService.create(
            organizationId,
            request.label(),
            request.mode(),
            request.scopes(),
            request.replacedApiKeyId(),
            request.rateLimitPerMinute()));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @MemberAdminAccess
  @Operation(
      summary = "API 키 폐기",
      description =
          """
          API 키를 폐기합니다. 폐기한 키로 요청하면 401입니다. 키를 지우지 않고 폐기 시각을 남깁니다.
          이미 폐기한 키면 폐기 시각을 그대로 두고 204를 돌려줍니다. 로그인 세션만 부를 수 있고 API 키로는 부를 수 없습니다.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "폐기했습니다. 본문은 없습니다."),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=validation_error: 경로의 UUID 형식이 틀렸습니다."),
    @ApiResponse(
        responseCode = "404",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=api_key_not_found: 세션 조직의 현재 업무 환경에 그런 키가 없습니다.")
  })
  public void deleteApiKey(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(description = "폐기할 키의 ID") @PathVariable UUID id) {
    apiKeyService.delete(organizationId, id);
  }

  @PutMapping("/{id}/scopes")
  @MemberAdminAccess
  @Operation(
      summary = "API 키 권한 변경",
      description =
          "세션 조직의 현재 업무 환경에서 사용 중인 API 키의 권한만 바꿉니다."
              + " 키 원문, API 키 구분(테스트 키, 라이브 키), 만료 시각은 그대로 두고 다음 요청부터 바뀐 권한을 적용합니다."
              + " 로그인 세션에 admin 권한이 있어야 하며 API 키로는 부를 수 없습니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "바뀐 키의 정보입니다. 원문과 해시는 싣지 않습니다."),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description =
            "code=validation_error: scopes가 비었거나 ingest, read, admin, payments가 아닌 값을 담았습니다."),
    @ApiResponse(
        responseCode = "403",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=permission_denied: 로그인 세션에 admin 권한이 없습니다."),
    @ApiResponse(
        responseCode = "404",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=api_key_not_found: 세션 조직의 현재 업무 환경에 그런 키가 없습니다."),
    @ApiResponse(
        responseCode = "409",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=api_key_unavailable: 폐기되거나 만료된 키입니다.")
  })
  public ApiKeyResponse updateApiKeyScopes(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(description = "권한을 바꿀 키의 ID") @PathVariable UUID id,
      @Valid @RequestBody UpdateApiKeyScopesRequest request) {
    return ApiKeyResponse.from(apiKeyService.update(organizationId, id, request.scopes()));
  }
}
