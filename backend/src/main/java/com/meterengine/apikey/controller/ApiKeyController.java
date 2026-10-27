package com.meterengine.apikey.controller;

import com.meterengine.apikey.dto.ApiKeyResponse;
import com.meterengine.apikey.dto.ApiKeySecretResponse;
import com.meterengine.apikey.dto.CreateApiKeyRequest;
import com.meterengine.apikey.dto.ListApiKeysResponse;
import com.meterengine.apikey.dto.UpdateApiKeyScopesRequest;
import com.meterengine.apikey.service.ApiKeyService;
import com.meterengine.global.error.ErrorResponse;
import com.meterengine.global.security.CurrentOrganizationId;
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
          세션 도입사의 API 키를 만든 순서로 돌려준다. 기본은 전체이며 사용 중, 폐기, 만료 상태를 고를 수 있다.
          사용 중은 현재 인증 가능한 키다. 폐기된 키는 만료 시각과 관계없이 폐기로 분류한다. 키 원문은 싣지 않는다.
          로그인 세션만 부를 수 있고 API 키로는 부를 수 없다. 페이지를 나누지 않는다.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "API 키 목록. 하나도 없으면 api_keys가 빈 배열이다")
  })
  public ListApiKeysResponse listApiKeys(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(
              description = "조회할 키 상태. 생략하면 전체",
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
          세션 도입사에 API 키를 발급하고 원문을 돌려준다. 원문은 이 응답에서만 볼 수 있고 서버는 해시만 저장한다.
          replaced_api_key_id를 주면 그 키를 이 키로 교체한다. 옛 키는 지금부터 유예 시간(기본 24시간)이 지나면 끝나고,
          이미 그보다 이른 만료 시각이 있으면 그대로 둔다. 로그인 세션만 부를 수 있고 API 키로는 부를 수 없다.
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
            code=validation_error: label이 비었거나 255자를 넘거나, scopes가 비었거나 ingest, read, admin이 아닌 값을 담았거나,
            mode가 live나 test가 아니거나, 문자열에 NUL 문자나 짝이 없는 UTF-16 서로게이트를 담았다.
            """),
    @ApiResponse(
        responseCode = "404",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=api_key_not_found: replaced_api_key_id가 세션 도입사의 폐기하지 않은 키가 아니다")
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
          API 키를 폐기한다. 폐기한 키로 요청하면 401이다. 행은 지우지 않고 폐기 시각을 남긴다.
          이미 폐기한 키면 폐기 시각을 그대로 두고 204다. 로그인 세션만 부를 수 있고 API 키로는 부를 수 없다.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "폐기했다. 본문 없음"),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=validation_error: 경로의 UUID 형식이 틀렸다"),
    @ApiResponse(
        responseCode = "404",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=api_key_not_found: 세션 도입사에 그런 키가 없다")
  })
  public void deleteApiKey(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(description = "폐기할 키의 ID.") @PathVariable UUID id) {
    apiKeyService.delete(organizationId, id);
  }

  @PutMapping("/{id}/scopes")
  @MemberAdminAccess
  @Operation(
      summary = "API 키 권한 변경",
      description =
          "현재 도입사의 사용 중인 API 키의 스코프만 바꾼다."
              + " 키 원문, 모드와 만료 시각을 유지하며 다음 요청부터 바뀐 권한을 적용한다."
              + " 로그인 사용자의 관리 권한이 필요하며 API 키로는 부를 수 없다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "바뀐 키의 메타데이터. 원문과 해시는 제공하지 않는다"),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=validation_error: 스코프가 비었거나 지원하지 않는 값이다"),
    @ApiResponse(
        responseCode = "403",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=permission_denied: 로그인 관리 권한이 없다"),
    @ApiResponse(
        responseCode = "404",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=api_key_not_found: 현재 도입사에 그런 키가 없다"),
    @ApiResponse(
        responseCode = "409",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=api_key_unavailable: 폐기되거나 만료된 키다")
  })
  public ApiKeyResponse updateApiKeyScopes(
      @CurrentOrganizationId UUID organizationId,
      @PathVariable UUID id,
      @Valid @RequestBody UpdateApiKeyScopesRequest request) {
    return ApiKeyResponse.from(apiKeyService.update(organizationId, id, request.scopes()));
  }
}
