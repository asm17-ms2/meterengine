package com.meterengine.global.config;

import com.meterengine.global.security.AdminAccess;
import com.meterengine.global.security.CurrentOrganizationId;
import com.meterengine.global.security.IngestAccess;
import com.meterengine.global.security.PaymentsAccess;
import com.meterengine.global.security.PublicAccess;
import com.meterengine.global.security.ReadAccess;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.Arrays;
import java.util.List;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.method.HandlerMethod;

@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

  private static final String API_VERSION = "v1";

  private static final String SECURITY_SCHEME_NAME = "bearerAuth";

  private static final String ERROR_RESPONSE_REF = "#/components/schemas/ErrorResponse";

  private static final String JSON_MEDIA_TYPE = "application/json";

  private static final String UNAUTHORIZED_DESCRIPTION =
      """
      code=authentication_required: Authorization 헤더가 없습니다.
      code=invalid_credentials: 키나 토큰의 형식이 틀렸거나, 등록되지 않은 값이거나, 폐기되거나 만료됐습니다.
      """;

  private static final String FORBIDDEN_DESCRIPTION =
      """
      code=permission_denied: 키의 권한이나 계정의 역할로는 할 수 없는 요청입니다.
      code=consent_required: 로그인 세션의 사용자가 약관과 개인정보 처리방침에 아직 동의하지 않았습니다. POST /v1/sessions/current/consents로 동의합니다.
      """;

  private static final String ORGANIZATION_FORBIDDEN_DESCRIPTION =
      """
      code=permission_denied: 키의 권한이나 계정의 역할로는 할 수 없는 요청이거나, 조직이 정해지지 않은 로그인 세션입니다.
      code=organization_not_active: 조직의 이용이 정지됐습니다.
      code=consent_required: 로그인 세션의 사용자가 약관과 개인정보 처리방침에 아직 동의하지 않았습니다. POST /v1/sessions/current/consents로 동의합니다.
      """;

  private static final String TOO_MANY_REQUESTS_DESCRIPTION =
      "code=rate_limit_exceeded: 키의 분당 요청 한도를 넘었습니다. Retry-After 헤더의 초만큼 기다린 뒤 다시 보냅니다.";

  private static final String RETRY_AFTER_DESCRIPTION = "다시 보내기 전에 기다릴 시간(초)";

  static {
    SpringDocUtils.getConfig().addAnnotationsToIgnore(CurrentOrganizationId.class);
  }

  @Bean
  OpenAPI meterEngineOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("MeterEngine API")
                .version(API_VERSION)
                .description(
                    """
                    사용량 기반 과금 서비스 MeterEngine의 API입니다. 사용량 이벤트를 수집하고, 고객별 월 사용량과 청구 예정액을 조회합니다.

                    요청마다 Authorization 헤더에 Bearer 값으로 API 키(me_live_ 또는 me_test_로 시작)나
                    로그인 세션 토큰을 싣습니다. 조직은 키나 세션이 정합니다. 헤더가 없으면 401입니다.

                    X-MeterEngine-Environment: test 또는 live로 업무 환경을 지정합니다. 새 키는 발급한 업무 환경에 고정됩니다.
                    기존 데이터와 키는 환경 미지정 범위로 보존하며, test나 live에 자동으로 배정하지 않습니다.
                    업무 환경을 나누지 않는 기능에 테스트 환경이나 라이브 환경으로 요청하면(그 환경의 키를 쓰거나
                    X-MeterEngine-Environment를 보내면) 409 environment_not_ready를 돌려줍니다.
                    X-MeterEngine-Organization-Id: 요청할 조직을 확인하는 선택 헤더(UUID)입니다.
                    인증된 조직과 다르면 403이며, 이 헤더로 인증이나 조직 전환을 대신할 수 없습니다.

                    오류 응답은 code, message, errors로 이뤄집니다. 오류별 처리는 code로 분기합니다. message는
                    code마다 하나인 한국어 문구이고, errors는 틀린 필드를 짚을 수 있는 400에만 실립니다.
                    """))
        .servers(List.of(new Server().url("/").description("이 문서를 제공한 호스트")))
        .components(
            new Components()
                .addSecuritySchemes(
                    SECURITY_SCHEME_NAME,
                    new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer")))
        .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME));
  }

  @Bean
  OperationCustomizer accessResponseCustomizer() {
    return OpenApiConfig::documentAccess;
  }

  private static Operation documentAccess(Operation operation, HandlerMethod handlerMethod) {
    if (handlerMethod.hasMethodAnnotation(PublicAccess.class)) {
      return operation.security(List.of());
    }
    if (operation.getResponses() == null) {
      operation.setResponses(new ApiResponses());
    }
    addResponse(operation, "401", errorResponse(UNAUTHORIZED_DESCRIPTION));
    addResponse(
        operation,
        "403",
        errorResponse(
            isOrganizationScoped(handlerMethod)
                ? ORGANIZATION_FORBIDDEN_DESCRIPTION
                : FORBIDDEN_DESCRIPTION));
    if (isRateLimited(handlerMethod)) {
      addResponse(
          operation,
          "429",
          errorResponse(TOO_MANY_REQUESTS_DESCRIPTION)
              .addHeaderObject(
                  HttpHeaders.RETRY_AFTER,
                  new Header()
                      .description(RETRY_AFTER_DESCRIPTION)
                      .required(true)
                      .schema(new IntegerSchema())));
    }
    return operation;
  }

  private static boolean isOrganizationScoped(HandlerMethod handlerMethod) {
    return Arrays.stream(handlerMethod.getMethodParameters())
        .anyMatch(parameter -> parameter.hasParameterAnnotation(CurrentOrganizationId.class));
  }

  private static boolean isRateLimited(HandlerMethod handlerMethod) {
    return handlerMethod.hasMethodAnnotation(ReadAccess.class)
        || handlerMethod.hasMethodAnnotation(IngestAccess.class)
        || handlerMethod.hasMethodAnnotation(AdminAccess.class)
        || handlerMethod.hasMethodAnnotation(PaymentsAccess.class);
  }

  private static void addResponse(Operation operation, String status, ApiResponse response) {
    if (operation.getResponses().containsKey(status)) {
      return;
    }
    operation.getResponses().addApiResponse(status, response);
  }

  private static ApiResponse errorResponse(String description) {
    return new ApiResponse()
        .description(description)
        .content(
            new Content()
                .addMediaType(
                    JSON_MEDIA_TYPE,
                    new MediaType().schema(new Schema<>().$ref(ERROR_RESPONSE_REF))));
  }
}
