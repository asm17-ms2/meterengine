package com.meterengine.global;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meterengine.TestCredentials;
import com.meterengine.TestcontainersConfiguration;
import com.meterengine.webhook.entity.WebhookEventType;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class OpenApiDocumentTest {

  private static final String SNAPSHOT_PATH_PROPERTY = "meterengine.openapi.snapshot";

  private static final String JSON_DOCUMENT = "/v3/api-docs";
  private static final String YAML_DOCUMENT = "/v3/api-docs.yaml";

  private static final String SNAKE_CASE = "^[a-z0-9]+(_[a-z0-9]+)*$";
  private static final Set<String> HTTP_METHODS =
      Set.of("get", "put", "post", "delete", "patch", "head", "options", "trace");

  private static final List<String> WORKSPACE_LOOKUPS =
      List.of(
          "/v1/customers/search",
          "/v1/billable-metrics/search",
          "/v1/billable-metric-prices/search",
          "/v1/usage/search",
          "/v1/invoices/draft/search",
          "/v1/invoices/search",
          "/v1/customers/options",
          "/v1/billable-metrics/options",
          "/v1/usage/months",
          "/v1/invoices/months",
          "/v1/billable-metric-prices/months");
  private static final List<String> WORKSPACE_EXPORTS =
      List.of(
          "/v1/customers/export",
          "/v1/billable-metrics/export",
          "/v1/billable-metric-prices/export",
          "/v1/usage/export",
          "/v1/invoices/draft/export",
          "/v1/invoices/export");
  private static final List<String> CUSTOMER_CHECKED_SEARCHES =
      List.of(
          "/v1/billable-metric-prices/search",
          "/v1/billable-metric-prices/export",
          "/v1/usage/search",
          "/v1/usage/export",
          "/v1/invoices/draft/search",
          "/v1/invoices/draft/export",
          "/v1/invoices/search",
          "/v1/invoices/export");

  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private JdbcTemplate jdbcTemplate;

  private MockMvcTester mvc;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcTester.from(
            webApplicationContext, builder -> builder.apply(springSecurity()).build());
  }

  // ---------------------------------------------------------------------------
  // 생성물 파일
  // ---------------------------------------------------------------------------

  @Test
  void 빌드가_생성물을_파일로_쓴다() throws Exception {
    String path = System.getProperty(SNAPSHOT_PATH_PROPERTY);
    assertThat(path).as("생성물 경로가 안 넘어왔다. `./gradlew test`로 돌려라 (IDE라면 Gradle 위임 설정)").isNotBlank();

    MvcTestResult result = mvc.get().uri(YAML_DOCUMENT).exchange();
    assertThat(result).hasStatusOk();

    Path target = Path.of(path);
    Files.createDirectories(target.getParent());
    Files.writeString(target, body(result).stripTrailing() + "\n", StandardCharsets.UTF_8);
  }

  @Test
  void 같은_코드에서_두_번_뽑으면_같은_바이트다() {
    assertThat(System.getProperty("springdoc.cache.disabled"))
        .as("이 검사는 springdoc 캐시가 꺼져 있어야 의미가 있다")
        .isEqualTo("true");

    String first = body(mvc.get().uri(YAML_DOCUMENT).exchange());
    String second = body(mvc.get().uri(YAML_DOCUMENT).exchange());

    assertThat(second).isEqualTo(first);
  }

  // ---------------------------------------------------------------------------
  // 문서 메타
  // ---------------------------------------------------------------------------

  @Test
  void info가_springdoc_기본값이_아니다() {
    assertThat(json())
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.info.title")
        .isEqualTo("MeterEngine API");
    assertThat(json()).hasStatusOk().bodyJson().extractingPath("$.info.version").isEqualTo("v1");
  }

  @Test
  void 서버_URL이_문서를_뽑은_환경에_따라_달라지지_않는다() {
    assertThat(json()).bodyJson().extractingPath("$.servers[*].url").asArray().containsExactly("/");
  }

  // ---------------------------------------------------------------------------
  // 오퍼레이션
  // ---------------------------------------------------------------------------

  @Test
  void 모든_오퍼레이션에_summary가_있다() {
    assertThat(operations())
        .isNotEmpty()
        .allSatisfy(
            operation ->
                assertThat(operation.path("summary").asText())
                    .as(operation.path("operationId").asText())
                    .isNotBlank());
  }

  @Test
  void 공개_경로인_로그인은_보안_요구가_빈_배열이다() {
    JsonNode security = root().path("paths").path("/v1/sessions").path("post").path("security");

    assertThat(security.isArray())
        .as("POST /v1/sessions의 security가 배열이 아니다: %s", security)
        .isTrue();
    assertThat(security.isEmpty()).as("POST /v1/sessions의 security: %s", security).isTrue();
    assertThat(root().path("security").isEmpty()).isFalse();
  }

  @Test
  void 공개_경로인_가입_신청은_보안_요구가_빈_배열이다() {
    JsonNode security = root().path("paths").path("/v1/signups").path("post").path("security");

    assertThat(security.isArray()).as("POST /v1/signups의 security가 배열이 아니다: %s", security).isTrue();
    assertThat(security.isEmpty()).as("POST /v1/signups의 security: %s", security).isTrue();
  }

  @Test
  void 가입과_사용자_응답의_중첩_원소는_자기_응답_이름의_스키마다() {
    JsonNode schemas = root().path("components").path("schemas");

    assertThat(
            schemas
                .path("SignupResponse")
                .path("properties")
                .path("organization")
                .path("$ref")
                .asText())
        .isEqualTo("#/components/schemas/SignupOrganization");
    assertThat(
            schemas
                .path("SignupResponse")
                .path("properties")
                .path("user_account")
                .path("$ref")
                .asText())
        .isEqualTo("#/components/schemas/SignupUserAccount");
    assertThat(
            schemas
                .path("UserAccountResponse")
                .path("properties")
                .path("organizations")
                .path("items")
                .path("$ref")
                .asText())
        .isEqualTo("#/components/schemas/UserAccountOrganization");
  }

  @Test
  void 웹훅_엔드포인트_요청의_통보_종류는_하나_이상이고_서버가_받는_값만_적는다() {
    JsonNode schemas = root().path("components").path("schemas");
    List<String> accepted =
        Arrays.stream(WebhookEventType.values()).map(WebhookEventType::getValue).toList();
    for (String request : List.of("CreateWebhookEndpointRequest", "UpdateWebhookEndpointRequest")) {
      JsonNode eventTypes = schemas.path(request).path("properties").path("event_types");
      assertThat(eventTypes.path("minItems").asInt()).as(request).isOne();
      List<String> documented = new ArrayList<>();
      eventTypes.path("items").path("enum").forEach(value -> documented.add(value.asText()));
      assertThat(documented).as(request).containsExactlyInAnyOrderElementsOf(accepted);
    }
  }

  @Test
  void 고객_등록은_보관한_외부_ID의_409와_조회_권한_없는_재등록의_빈_값을_문서화한다() {
    JsonNode responses = root().path("paths").path("/v1/customers").path("post").path("responses");
    assertErrorResponse(responses, "409", "code=customer_archived");
    assertThat(responses.path("200").path("description").asText()).contains("read");
  }

  @Test
  void 검색_결과_CSV는_바이트가_아닌_문자열로_문서화한다() {
    for (String path : WORKSPACE_EXPORTS) {
      JsonNode schema =
          getResponses(path).path("200").path("content").path("text/csv").path("schema");
      assertThat(schema.path("type").asText()).as(path).isEqualTo("string");
      assertThat(schema.has("format")).as(path).isFalse();
    }
  }

  @Test
  void 작업_공간_조회는_잘못된_조건의_400을_문서화한다() {
    List<String> paths = new ArrayList<>(WORKSPACE_LOOKUPS);
    paths.addAll(WORKSPACE_EXPORTS);
    for (String path : paths)
      assertErrorResponse(getResponses(path), "400", "code=validation_error");
  }

  @Test
  void 검색_결과_CSV의_400은_10000행_상한을_적는다() {
    for (String path : WORKSPACE_EXPORTS)
      assertThat(getResponses(path).path("400").path("description").asText())
          .as(path)
          .contains("10000");
  }

  @Test
  void 고객을_확인하는_검색과_CSV는_없는_고객의_404를_문서화한다() {
    for (String path : CUSTOMER_CHECKED_SEARCHES)
      assertErrorResponse(getResponses(path), "404", "code=customer_not_found");
  }

  @Test
  void 고객과_미터_수정은_버전_충돌_409와_If_Match_형식_400을_문서화한다() {
    for (String path : List.of("/v1/customers/{id}", "/v1/billable-metrics/{code}")) {
      for (String method : List.of("put", "patch")) {
        JsonNode responses = root().path("paths").path(path).path(method).path("responses");
        assertErrorResponse(responses, "409", "code=resource_version_conflict");
        assertErrorResponse(responses, "400", "If-Match");
        assertThat(responses.has("404")).as("%s %s 404", method, path).isTrue();
      }
    }
  }

  @Test
  void 멱등_생성은_재시도_키_충돌_409와_재생_헤더를_문서화한다() {
    for (String path : List.of("/v1/customers", "/v1/billable-metrics")) {
      JsonNode responses = root().path("paths").path(path).path("post").path("responses");
      assertErrorResponse(responses, "409", "code=idempotency_conflict");
      assertErrorResponse(responses, "400", "Idempotency-Key");
      assertThat(responses.path("201").path("headers").has("Idempotency-Replayed"))
          .as(path)
          .isTrue();
    }
  }

  @Test
  void 단가_응답의_tiers는_구간_객체의_배열이다() {
    JsonNode schemas = root().path("components").path("schemas");
    for (String schema : List.of("PriceRateResponse", "BillableMetricPricePriceRate")) {
      JsonNode tiers = schemas.path(schema).path("properties").path("tiers");
      assertThat(tiers.path("type").asText()).as(schema).isEqualTo("array");
      assertThat(tiers.path("items").path("$ref").asText())
          .as(schema)
          .isEqualTo("#/components/schemas/PriceRateTier");
    }
  }

  @Test
  void 단가_모델과_청구_라인_종류는_서버가_쓰는_값_목록을_적는다() {
    JsonNode schemas = root().path("components").path("schemas");
    for (String schema :
        List.of("PriceRateResponse", "BillableMetricPricePriceRate", "CreatePriceRateRequest")) {
      List<String> models = new ArrayList<>();
      schemas
          .path(schema)
          .path("properties")
          .path("model")
          .path("enum")
          .forEach(value -> models.add(value.asText()));
      assertThat(models).as(schema).containsExactlyInAnyOrder("per_unit", "graduated", "volume");
    }
    for (String schema : List.of("DraftInvoiceLine", "InvoiceLineResponse")) {
      List<String> lineTypes = new ArrayList<>();
      schemas
          .path(schema)
          .path("properties")
          .path("line_type")
          .path("enum")
          .forEach(value -> lineTypes.add(value.asText()));
      assertThat(lineTypes)
          .as(schema)
          .containsExactlyInAnyOrder(
              "per_unit", "volume", "graduated_tier", "base_fee", "minimum_adjustment");
    }
  }

  @Test
  void 요금제_이력_조회는_생략한_파라미터의_뜻과_400_404를_적는다() {
    JsonNode operation = root().path("paths").path("/v1/price-plans").path("get");
    assertThat(operation.path("description").asText()).isNotBlank();
    assertThat(operation.path("responses").has("400")).isTrue();
    assertThat(operation.path("responses").has("404")).isTrue();
    operation
        .path("parameters")
        .forEach(
            parameter ->
                assertThat(parameter.path("description").asText())
                    .as(parameter.path("name").asText())
                    .isNotBlank());
  }

  @Test
  void 운영자_경로는_보안_요구를_물려받고_401과_403을_문서화한다() {
    JsonNode paths = root().path("paths");
    List<JsonNode> operatorOperations =
        List.of(
            paths.path("/v1/organizations").path("get"),
            paths.path("/v1/organizations/{id}/status").path("put"),
            paths.path("/v1/organizations/{id}/members").path("post"),
            paths.path("/v1/user-accounts").path("get"),
            paths.path("/v1/user-accounts/{id}/role").path("put"));

    assertThat(operatorOperations)
        .allSatisfy(
            operation -> {
              assertThat(operation.isMissingNode()).as("운영자 경로가 문서에 없다").isFalse();
              assertThat(operation.has("security"))
                  .as(operation.path("operationId").asText())
                  .isFalse();
              assertThat(operation.path("responses").has("401")).isTrue();
              assertThat(operation.path("responses").has("403")).isTrue();
            });
  }

  @Test
  void 세션으로_부르는_경로의_403은_동의_대기_code를_문서화한다() {
    JsonNode paths = root().path("paths");
    List<JsonNode> sessionOperations =
        List.of(
            paths.path("/v1/user-accounts").path("get"),
            paths.path("/v1/organization/members").path("get"));

    assertThat(sessionOperations)
        .allSatisfy(
            operation ->
                assertThat(operation.path("responses").path("403").path("description").asText())
                    .as(operation.path("operationId").asText())
                    .contains("code=consent_required"));
  }

  @Test
  void 공개_경로인_초대_수락은_403_404_409_오류_code를_ErrorResponse로_문서화한다() {
    JsonNode responses =
        root()
            .path("paths")
            .path("/v1/organization-invitations/{token}/accept")
            .path("post")
            .path("responses");

    assertThat(responses.has("201")).as("초대 수락 응답: %s", responses).isTrue();
    assertErrorResponse(responses, "403", "code=permission_denied", "code=consent_required");
    assertErrorResponse(responses, "404", "code=invitation_not_found");
    assertErrorResponse(
        responses, "409", "code=invitation_unavailable", "code=organization_member_already_exists");
  }

  @Test
  void 소속_제거는_404와_409_오류_code를_ErrorResponse로_문서화한다() {
    JsonNode responses =
        root().path("paths").path("/v1/organization/members/{id}").path("delete").path("responses");

    assertErrorResponse(responses, "404", "code=user_account_not_found");
    assertErrorResponse(responses, "409", "code=last_owner_required");
  }

  @Test
  void 보관한_자원을_쓰는_경로는_409_보관_code를_ErrorResponse로_문서화한다() {
    JsonNode paths = root().path("paths");

    assertErrorResponse(
        paths.path("/v1/events").path("post").path("responses"), "409", "code=customer_archived");
    assertErrorResponse(
        paths.path("/v1/customers/{id}").path("put").path("responses"),
        "409",
        "code=customer_archived");
    assertErrorResponse(
        paths.path("/v1/billable-metrics/{code}").path("put").path("responses"),
        "409",
        "code=resource_archived");
    assertErrorResponse(
        paths.path("/v1/billable-metrics/{code}/price-rates").path("post").path("responses"),
        "409",
        "code=resource_archived",
        "code=customer_archived",
        "code=archive_restoration_conflict");
    assertErrorResponse(
        paths
            .path("/v1/billable-metrics/{code}/price-rates/{id}/restorations")
            .path("post")
            .path("responses"),
        "409",
        "code=archive_restoration_conflict");
    assertErrorResponse(
        paths.path("/v1/price-plans").path("post").path("responses"),
        "409",
        "code=customer_archived",
        "code=archive_restoration_conflict");
    assertErrorResponse(
        paths.path("/v1/price-plans/{id}/restorations").path("post").path("responses"),
        "409",
        "code=archive_restoration_conflict");
    assertErrorResponse(
        paths.path("/v1/webhook-endpoints/{id}").path("put").path("responses"),
        "409",
        "code=resource_archived");
    assertErrorResponse(
        paths
            .path("/v1/customers/{id}/payment-method-registrations")
            .path("post")
            .path("responses"),
        "409",
        "code=customer_archived");
    assertErrorResponse(
        paths.path("/v1/payment-method-registrations/{token}").path("get").path("responses"),
        "409",
        "code=customer_archived");
    assertErrorResponse(
        paths
            .path("/v1/payment-method-registrations/{token}/billing-keys")
            .path("post")
            .path("responses"),
        "409",
        "code=customer_archived");
  }

  @Test
  void 보관과_복원_경로는_없는_대상의_404_code를_ErrorResponse로_문서화한다() {
    JsonNode paths = root().path("paths");

    for (String method : List.of("get", "delete")) {
      assertErrorResponse(
          paths.path("/v1/customers/{id}").path(method).path("responses"),
          "404",
          "code=customer_not_found");
    }
    assertErrorResponse(
        paths.path("/v1/customers/{id}/restorations").path("post").path("responses"),
        "404",
        "code=customer_not_found");
    assertErrorResponse(
        paths.path("/v1/billable-metrics/{code}").path("delete").path("responses"),
        "404",
        "code=billable_metric_not_found");
    assertErrorResponse(
        paths.path("/v1/billable-metrics/{code}/restorations").path("post").path("responses"),
        "404",
        "code=billable_metric_not_found");
    assertErrorResponse(
        paths
            .path("/v1/billable-metrics/{code}/price-rates/{id}/restorations")
            .path("post")
            .path("responses"),
        "404",
        "code=price_rate_not_found");
    assertErrorResponse(
        paths.path("/v1/price-plans/{id}/restorations").path("post").path("responses"),
        "404",
        "code=price_plan_not_found");
  }

  @Test
  void 보관_경로의_설명은_지우거나_숨긴다고_하지_않는다() {
    JsonNode paths = root().path("paths");
    JsonNode webhookDelete = paths.path("/v1/webhook-endpoints/{id}").path("delete");

    assertThat(
            paths
                .path("/v1/billable-metrics/{code}/price-rates")
                .path("delete")
                .path("responses")
                .path("204")
                .path("description")
                .asText())
        .isEqualTo("이번 달 적용 중인 버전을 보관했다. 본문 없음");
    assertThat(webhookDelete.path("summary").asText()).isEqualTo("웹훅 엔드포인트 보관");
    assertThat(webhookDelete.path("description").asText())
        .contains("status=archived")
        .doesNotContain("숨긴다");
  }

  // ---------------------------------------------------------------------------
  // 손으로 잡아 준 스키마
  // ---------------------------------------------------------------------------

  @Test
  void properties가_문자열이_아니라_객체다() {
    assertThat(json())
        .bodyJson()
        .extractingPath("$.components.schemas.EventResponse.properties.properties.type")
        .isEqualTo("object");
  }

  @Test
  void 미터별_가격의_조합별_단가가_배열이다() {
    assertThat(json())
        .bodyJson()
        .extractingPath(
            "$.components.schemas.BillableMetricPriceResponse.properties.price_rates.type")
        .isEqualTo("array");
    assertThat(json())
        .bodyJson()
        .extractingPath(
            "$.components.schemas.BillableMetricPricePriceRate.properties.dimension_values.type")
        .isEqualTo("object");
  }

  @Test
  void 단가_등록_경로가_있고_오류_응답이_ErrorResponse다() {
    String priceRates = "$.paths['/v1/billable-metrics/{code}/price-rates']";
    assertThat(json()).bodyJson().extractingPath(priceRates).asMap().containsKeys("post", "delete");
    assertThat(json())
        .bodyJson()
        .extractingPath(
            priceRates + ".delete.responses.404.content['application/json'].schema['$ref']")
        .isEqualTo("#/components/schemas/ErrorResponse");
    assertThat(json())
        .bodyJson()
        .extractingPath(
            priceRates + ".post.responses.404.content['application/json'].schema['$ref']")
        .isEqualTo("#/components/schemas/ErrorResponse");
  }

  @Test
  void 결제_생성의_409는_활성_중복과_이전_결제_결과_미확정을_구분한다() {
    JsonNode response =
        root()
            .path("paths")
            .path("/v1/invoices/{id}/payment-attempts")
            .path("post")
            .path("responses")
            .path("409");

    assertThat(response.path("description").asText())
        .contains("code=invoice_already_paid: 이 결제 모드로 결제 중이거나 결제된 시도가 이미 있다.")
        .contains(
            "code=payment_result_unconfirmed: 이전 결제 시도가 미청구로 확인되지 않았다. 이전 결제 결과를 확인한 뒤 다시 요청한다.");
    assertThat(
            response.path("content").path("application/json").path("schema").path("$ref").asText())
        .isEqualTo("#/components/schemas/ErrorResponse");
    assertThat(json())
        .bodyJson()
        .extractingPath("$.components.schemas.ErrorResponse.properties.code.enum")
        .asArray()
        .contains("invoice_already_paid", "payment_result_unconfirmed");
  }

  @Test
  void 오류_응답이_200_스키마를_물려받지_않는다() {
    Map<String, List<String>> schemasByErrorResponse = new TreeMap<>();
    operations()
        .forEach(
            operation ->
                operation
                    .path("responses")
                    .fieldNames()
                    .forEachRemaining(
                        status -> {
                          if (status.startsWith("4") || status.startsWith("5")) {
                            schemasByErrorResponse.put(
                                operation.path("operationId").asText() + " " + status,
                                schemasByMediaType(
                                    operation.path("responses").path(status).path("content")));
                          }
                        }));

    assertThat(schemasByErrorResponse)
        .isNotEmpty()
        .allSatisfy(
            (response, schemas) ->
                assertThat(schemas)
                    .as(response)
                    .containsExactly("application/json #/components/schemas/ErrorResponse"));
  }

  @Test
  @Transactional
  void 오류_스키마가_실제_본문과_같은_모양이다() {
    assertSchemaHasField("ErrorResponse", "code");
    assertSchemaHasField("ErrorResponse", "errors");
    assertSchemaHasField("FieldError", "field");
    assertSchemaHasField("FieldError", "message");

    assertThat(json())
        .bodyJson()
        .extractingPath("$.components.schemas")
        .asMap()
        .doesNotContainKey("ProblemDetail");

    Set<String> documented = keysOf(json(), "$.components.schemas.ErrorResponse.properties");
    String authorization = TestCredentials.issueApiKey(jdbcTemplate, UUID.randomUUID());
    Set<String> actual = new TreeSet<>();
    actual.addAll(keysOf(mvc.get().uri("/v1/events").exchange(), "$"));
    actual.addAll(
        keysOf(
            mvc.get()
                .uri("/v1/events")
                .param("size", "101")
                .header(AUTHORIZATION, authorization)
                .exchange(),
            "$"));
    actual.addAll(
        keysOf(mvc.get().uri("/v1/nope").header(AUTHORIZATION, authorization).exchange(), "$"));
    actual.addAll(
        keysOf(
            mvc.method(HttpMethod.DELETE)
                .uri("/v1/events")
                .header(AUTHORIZATION, authorization)
                .exchange(),
            "$"));
    actual.addAll(
        keysOf(
            mvc.post()
                .uri("/v1/events")
                .header(AUTHORIZATION, authorization)
                .contentType(MediaType.TEXT_PLAIN)
                .content("{}")
                .exchange(),
            "$"));
    actual.addAll(
        keysOf(
            mvc.get()
                .uri("/v1/usage")
                .param("month", "2026-13")
                .header(AUTHORIZATION, authorization)
                .exchange(),
            "$"));

    assertThat(actual)
        .as(
            "문서 프로퍼티와 실제 400 본문의 최상위 키가 갈렸다. 문서에만: %s / 응답에만: %s",
            difference(documented, actual), difference(actual, documented))
        .isEqualTo(documented);

    assertSchemaFieldHasDescription("ErrorResponse", "code");
    assertSchemaFieldHasDescription("ErrorResponse", "errors");
  }

  // ---------------------------------------------------------------------------
  // 이름 규칙
  // ---------------------------------------------------------------------------

  @Test
  void operationId는_전역에서_유일하고_springdoc이_접미사를_붙이지_않는다() {
    List<String> operationIds = new ArrayList<>();
    root()
        .path("paths")
        .forEach(
            path ->
                operationsOf(path)
                    .forEach(
                        operation -> operationIds.add(operation.path("operationId").asText())));
    assertThat(operationIds)
        .isNotEmpty()
        .doesNotContain("")
        .doesNotHaveDuplicates()
        .noneMatch(id -> id.matches(".*_\\d+$"));
  }

  @Test
  void 경로는_v1_아래_kebab_case이고_경로_변수는_snake_case다() {
    assertThat(keysOf(json(), "$.paths"))
        .allMatch(
            path -> path.matches("^/v1(/([a-z0-9]+(-[a-z0-9]+)*|\\{[a-z0-9]+(_[a-z0-9]+)*}))+$"));
  }

  @Test
  void 쿼리와_경로_파라미터_이름은_snake_case다() {
    List<String> names = new ArrayList<>();
    root()
        .path("paths")
        .forEach(
            path ->
                operationsOf(path)
                    .forEach(
                        operation ->
                            operation
                                .path("parameters")
                                .forEach(
                                    parameter -> {
                                      String in = parameter.path("in").asText();
                                      if (in.equals("query") || in.equals("path")) {
                                        names.add(parameter.path("name").asText());
                                      }
                                    })));
    assertThat(names).isNotEmpty().allMatch(name -> name.matches(SNAKE_CASE));
  }

  @Test
  void 보관_상태로_거르는_목록의_status는_active_archived_all만_받고_기본이_active다() {
    List<String> operationIds =
        List.of(
            "listCustomers",
            "listBillableMetrics",
            "listPriceRates",
            "listPricePlans",
            "listPaymentMethods",
            "listTossPaymentsCredentials",
            "listWebhookEndpoints");

    for (String operationId : operationIds) {
      JsonNode operation =
          operations().stream()
              .filter(candidate -> candidate.path("operationId").asText().equals(operationId))
              .findFirst()
              .orElseThrow(() -> new AssertionError(operationId + " 오퍼레이션이 문서에 없다"));
      JsonNode schema = null;
      for (JsonNode parameter : operation.path("parameters")) {
        if (parameter.path("in").asText().equals("query")
            && parameter.path("name").asText().equals("status")) {
          schema = parameter.path("schema");
        }
      }
      assertThat(schema).as("%s의 status 파라미터", operationId).isNotNull();
      List<String> values = new ArrayList<>();
      schema.path("enum").forEach(value -> values.add(value.asText()));
      assertThat(values).as(operationId).containsExactly("active", "archived", "all");
      assertThat(schema.path("default").asText()).as(operationId).isEqualTo("active");
    }
  }

  @Test
  void 스키마_속성_이름과_열거값은_snake_case다() {
    List<String> names = new ArrayList<>();
    List<String> enumValues = new ArrayList<>();
    root()
        .path("components")
        .path("schemas")
        .forEach(
            schema ->
                schema
                    .path("properties")
                    .fieldNames()
                    .forEachRemaining(
                        name -> {
                          names.add(name);
                          schema
                              .path("properties")
                              .path(name)
                              .path("enum")
                              .forEach(value -> enumValues.add(value.asText()));
                        }));
    assertThat(names).isNotEmpty().allMatch(name -> name.matches(SNAKE_CASE));
    assertThat(enumValues).isNotEmpty().allMatch(value -> value.matches(SNAKE_CASE));
  }

  // ---------------------------------------------------------------------------

  private JsonNode getResponses(String path) {
    return root().path("paths").path(path).path("get").path("responses");
  }

  private static void assertErrorResponse(JsonNode responses, String status, String... codes) {
    JsonNode response = responses.path(status);
    assertThat(
            response.path("content").path("application/json").path("schema").path("$ref").asText())
        .as("%s 응답: %s", status, responses)
        .isEqualTo("#/components/schemas/ErrorResponse");
    assertThat(response.path("description").asText()).as("%s 응답 설명", status).contains(codes);
  }

  private void assertSchemaHasField(String schema, String field) {
    assertThat(json())
        .bodyJson()
        .extractingPath("$.components.schemas.%s.properties.%s".formatted(schema, field))
        .isNotNull();
  }

  private void assertSchemaFieldHasDescription(String schema, String field) {
    assertThat(json())
        .bodyJson()
        .extractingPath(
            "$.components.schemas.%s.properties.%s.description".formatted(schema, field))
        .asString()
        .as("%s.%s에 description이 없다. 사유가 생성물에 실려야 한다 (인수기준 4)", schema, field)
        .isNotBlank();
  }

  private Set<String> keysOf(MvcTestResult result, String path) {
    try {
      JsonNode node = new ObjectMapper().readTree(body(result)).at(toPointer(path));
      Set<String> keys = new TreeSet<>();
      node.fieldNames().forEachRemaining(keys::add);
      assertThat(keys).as("%s가 비었다. 경로가 틀렸거나 응답이 JSON이 아니다", path).isNotEmpty();
      return keys;
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  private static String toPointer(String path) {
    if (path.indexOf('[') >= 0) {
      throw new IllegalArgumentException("대괄호 표기는 지원하지 않는다. 점 표기로 적어라: " + path);
    }
    return "$".equals(path) ? "" : "/" + path.substring(2).replace('.', '/');
  }

  private static Set<String> difference(Set<String> left, Set<String> right) {
    Set<String> only = new TreeSet<>(left);
    only.removeAll(right);
    return only;
  }

  private static List<String> schemasByMediaType(JsonNode content) {
    List<String> schemas = new ArrayList<>();
    content
        .fieldNames()
        .forEachRemaining(
            mediaType ->
                schemas.add(
                    mediaType
                        + " "
                        + content.path(mediaType).path("schema").path("$ref").asText()));
    return schemas;
  }

  private List<JsonNode> operations() {
    List<JsonNode> operations = new ArrayList<>();
    root().path("paths").forEach(path -> operations.addAll(operationsOf(path)));
    return operations;
  }

  private static List<JsonNode> operationsOf(JsonNode path) {
    List<JsonNode> operations = new ArrayList<>();
    path.fieldNames()
        .forEachRemaining(
            method -> {
              if (HTTP_METHODS.contains(method)) {
                operations.add(path.path(method));
              }
            });
    return operations;
  }

  private JsonNode root() {
    try {
      return new ObjectMapper().readTree(body(json()));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  private MvcTestResult json() {
    return mvc.get().uri(JSON_DOCUMENT).exchange();
  }

  private String body(MvcTestResult result) {
    try {
      return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    } catch (UnsupportedEncodingException e) {
      throw new IllegalStateException(e);
    }
  }
}
