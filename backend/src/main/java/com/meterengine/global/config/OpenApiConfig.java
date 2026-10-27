package com.meterengine.global.config;

import com.meterengine.global.security.AdminAccess;
import com.meterengine.global.security.CurrentOrganizationId;
import com.meterengine.global.security.IngestAccess;
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
      code=authentication_required: Authorization 헤더가 없다.
      code=invalid_credentials: 키나 토큰의 형식이 틀렸거나, 모르는 값이거나, 폐기 또는 만료됐다.
      """;

  private static final String FORBIDDEN_DESCRIPTION =
      "code=permission_denied: 키의 스코프나 계정의 역할로는 할 수 없는 요청이다.";

  private static final String ORGANIZATION_FORBIDDEN_DESCRIPTION =
      """
      code=permission_denied: 키의 스코프나 계정의 역할로는 할 수 없는 요청이거나, 도입사가 정해지지 않은 로그인 세션이다.
      code=organization_not_active: 도입사가 활성 상태가 아니다.
      """;

  private static final String TOO_MANY_REQUESTS_DESCRIPTION =
      "code=rate_limit_exceeded: 키의 분당 요청 한도를 넘었다. Retry-After 헤더의 초만큼 기다린 뒤 다시 보낸다.";

  private static final String RETRY_AFTER_DESCRIPTION = "다시 보내기 전에 기다릴 초";

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
                    사용량 기반 과금 플랫폼의 API. 사용량 이벤트를 수집하고, 고객별 월 사용량과 청구 예정액을 조회한다.

                    요청마다 Authorization 헤더에 Bearer 값으로 API 키(me_live_ 또는 me_test_로 시작)나
                    로그인 세션 토큰을 싣는다. 도입사는 키나 세션이 정한다. 헤더가 없으면 401이다.

                    X-MeterEngine-Environment: test 또는 live로 업무 환경을 지정한다. 새 키는 발급 환경에 고정된다.
                    기존 데이터와 키는 환경 미지정 범위로 보존하며 자동으로 test/live에 배정하지 않는다.
                    환경 분리가 지원되지 않는 업무는 명시 환경 요청에 409를 반환한다.
                    X-MeterEngine-Organization-Id: 화면의 도입사를 고정하는 선택 UUID 헤더다.
                    인증된 도입사와 다르면 403이며 이 헤더로 인증이나 도입사 전환을 대신할 수 없다.

                    오류 응답은 code, message, errors로 이뤄진다. 오류별 처리는 code로 분기한다. message는
                    code마다 하나인 한국어 문구이고, errors는 틀린 필드를 짚을 수 있는 400에만 실린다.
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
        || handlerMethod.hasMethodAnnotation(AdminAccess.class);
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
