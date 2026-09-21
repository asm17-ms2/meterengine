package com.meterengine.global;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meterengine.TestcontainersConfiguration;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.context.WebApplicationContext;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class OpenApiDocumentTest {

  private static final String SNAPSHOT_PATH_PROPERTY = "meterengine.openapi.snapshot";

  private static final String JSON_DOCUMENT = "/v3/api-docs";
  private static final String YAML_DOCUMENT = "/v3/api-docs.yaml";

  @Autowired private WebApplicationContext webApplicationContext;

  private MockMvcTester mvc;

  @BeforeEach
  void setUp() {
    mvc = MockMvcTester.from(webApplicationContext);
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
  // 오퍼레이션 (컨트롤러 정의만으로 자동 반영되는지)
  // ---------------------------------------------------------------------------

  @Test
  void 컨트롤러의_모든_오퍼레이션이_들어_있다() {
    assertThat(json())
        .bodyJson()
        .extractingPath("$.paths")
        .asMap()
        .containsOnlyKeys(
            "/v1/events",
            "/v1/usage",
            "/v1/invoices/draft",
            "/v1/customers",
            "/v1/customers/{id}",
            "/v1/billable-metrics",
            "/v1/billable-metrics/{code}/price-policy",
            "/v1/billable-metric-prices");

    assertThat(json()).bodyJson().extractingPath("$.paths['/v1/events'].post.summary").isNotNull();
    assertThat(json()).bodyJson().extractingPath("$.paths['/v1/events'].get.summary").isNotNull();
    assertThat(json()).bodyJson().extractingPath("$.paths['/v1/usage'].get.summary").isNotNull();
    assertThat(json())
        .bodyJson()
        .extractingPath("$.paths['/v1/invoices/draft'].get.summary")
        .isNotNull();
    assertThat(json())
        .bodyJson()
        .extractingPath("$.paths['/v1/customers'].get.summary")
        .isNotNull();
    assertThat(json())
        .bodyJson()
        .extractingPath("$.paths['/v1/customers'].post.summary")
        .isNotNull();
    assertThat(json())
        .bodyJson()
        .extractingPath("$.paths['/v1/customers/{id}'].put.summary")
        .isNotNull();
    assertThat(json())
        .bodyJson()
        .extractingPath("$.paths['/v1/customers/{id}'].delete.summary")
        .isNotNull();
    assertThat(json())
        .bodyJson()
        .extractingPath("$.paths['/v1/billable-metrics'].post.summary")
        .isNotNull();
    assertThat(json())
        .bodyJson()
        .extractingPath("$.paths['/v1/billable-metrics'].get.summary")
        .isNotNull();
    assertThat(json())
        .bodyJson()
        .extractingPath("$.paths['/v1/billable-metrics/{code}/price-policy'].post.summary")
        .isNotNull();
    assertThat(json())
        .bodyJson()
        .extractingPath("$.paths['/v1/billable-metric-prices'].get.summary")
        .isNotNull();
  }

  @Test
  void 쿼리_파라미터_이름이_실제_요청과_같다() {
    assertThat(json())
        .bodyJson()
        .extractingPath("$.paths['/v1/events'].get.parameters[*].name")
        .asArray()
        .contains("X-Organization-Id", "page", "size", "customer_id", "month", "type");
  }

  @Test
  void 스키마_필드_이름이_실제_JSON과_같다() {
    assertSchemaHasField("EventResponse", "transaction_id");
    assertSchemaHasField("EventResponse", "customer_name");
    assertSchemaHasField("IngestEventRequest", "customer_id");
    assertSchemaHasField("IngestEventResponse", "transaction_id");
    assertSchemaHasField("BillableMetricUsageResponse", "target_property");
    assertSchemaHasField("DraftInvoiceCustomer", "customer_id");
    assertSchemaHasField("DraftInvoiceResponse", "total_amount");
    assertSchemaHasField("CustomerResponse", "id");
    assertSchemaHasField("CreatePricePolicyRequest", "dimension_properties");
    assertSchemaHasField("PricePolicyResponse", "billable_metric_code");
    assertSchemaHasField("CustomerResponse", "created_at");
    assertSchemaHasField("ListBillableMetricPricesResponse", "billable_metric_prices");
    assertSchemaHasField("BillableMetricPriceResponse", "billable_metric_code");
    assertSchemaHasField("BillableMetricPriceResponse", "dimension_properties");
    assertSchemaHasField("BillableMetricPriceResponse", "unit_price");
    assertThat(json())
        .bodyJson()
        .extractingPath(
            "$.components.schemas.BillableMetricPriceResponse.properties.dimension_properties.type")
        .asArray()
        .contains("null");
    assertThat(json())
        .bodyJson()
        .extractingPath(
            "$.components.schemas.BillableMetricPriceResponse.properties.unit_price.type")
        .asArray()
        .contains("null");

    assertThat(body(json()))
        .doesNotContain(
            "transactionId",
            "customerId",
            "customerName",
            "eventType",
            "occurredAt",
            "receivedAt",
            "targetProperty",
            "totalAmount",
            "calculatedAt",
            "dimensionProperties",
            "dimensionValues",
            "unitPrice",
            "billableMetricPrices",
            "createdAt");
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
  void 모든_400이_200_스키마를_물려받지_않는다() {
    assertErrorResponseSchema("/v1/events", "get", "ErrorResponse");
    assertErrorResponseSchema("/v1/events", "post", "ErrorResponse");
    assertErrorResponseSchema("/v1/usage", "get", "ErrorResponse");
    assertErrorResponseSchema("/v1/invoices/draft", "get", "ErrorResponse");
    assertErrorResponseSchema("/v1/customers", "get", "ErrorResponse");
    assertErrorResponseSchema("/v1/customers", "post", "ErrorResponse");
    assertErrorResponseSchema("/v1/customers/{id}", "put", "ErrorResponse");
    assertErrorResponseSchema("/v1/customers/{id}", "delete", "ErrorResponse");
    assertErrorResponseSchema("/v1/billable-metrics", "post", "ErrorResponse");
    assertErrorResponseSchema("/v1/billable-metrics", "get", "ErrorResponse");
    assertErrorResponseSchema("/v1/billable-metrics/{code}/price-policy", "post", "ErrorResponse");
    assertErrorResponseSchema("/v1/billable-metric-prices", "get", "ErrorResponse");
  }

  @Test
  void 다른_오류_상태도_200_스키마를_물려받지_않는다() {
    assertErrorResponseSchema("/v1/customers/{id}", "put", "404", "ErrorResponse");
    assertErrorResponseSchema("/v1/customers/{id}", "delete", "404", "ErrorResponse");
    assertErrorResponseSchema("/v1/customers/{id}", "delete", "409", "ErrorResponse");
    assertErrorResponseSchema("/v1/billable-metrics", "post", "409", "ErrorResponse");
    assertErrorResponseSchema(
        "/v1/billable-metrics/{code}/price-policy", "post", "404", "ErrorResponse");
    assertErrorResponseSchema(
        "/v1/billable-metrics/{code}/price-policy", "post", "409", "ErrorResponse");
  }

  @Test
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
    Set<String> actual = new TreeSet<>();
    actual.addAll(keysOf(mvc.get().uri("/v1/events").exchange(), "$"));
    actual.addAll(keysOf(mvc.get().uri("/v1/nope").exchange(), "$"));
    actual.addAll(keysOf(mvc.method(HttpMethod.DELETE).uri("/v1/events").exchange(), "$"));
    actual.addAll(
        keysOf(
            mvc.post().uri("/v1/events").contentType(MediaType.TEXT_PLAIN).content("{}").exchange(),
            "$"));
    actual.addAll(keysOf(mvc.get().uri("/v1/usage").exchange(), "$"));

    assertThat(actual)
        .as(
            "문서 프로퍼티와 실제 400 본문의 최상위 키가 갈렸다. 문서에만: %s / 응답에만: %s",
            difference(documented, actual), difference(actual, documented))
        .isEqualTo(documented);

    assertSchemaFieldHasDescription("ErrorResponse", "code");
    assertSchemaFieldHasDescription("ErrorResponse", "errors");
  }

  // ---------------------------------------------------------------------------

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

  /** 400 응답이 problem+json으로, 기대한 오류 스키마를 가리키는지 본다. */
  private void assertErrorResponseSchema(String path, String method, String schema) {
    assertErrorResponseSchema(path, method, "400", schema);
  }

  private void assertErrorResponseSchema(String path, String method, String status, String schema) {
    assertThat(json())
        .bodyJson()
        .extractingPath(
            "$.paths['%s'].%s.responses['%s'].content['application/json'].schema.$ref"
                .formatted(path, method, status))
        .asString()
        .isEqualTo("#/components/schemas/%s".formatted(schema));
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
