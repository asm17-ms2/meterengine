package com.meterengine;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

/**
 * OpenAPI 문서를 파일로 뽑고, 뽑힌 내용이 실제 계약과 맞는지 본다 (MS2-140).
 *
 * <p><b>왜 테스트가 파일을 쓰나.</b> springdoc은 코드를 정적으로 분석하지 않는다. 스프링 컨텍스트가 떠 있어야 문서를 만들 수 있어서, 파일을 만들려면 앱을
 * 한 번 띄우는 수밖에 없다. 그 자리로 테스트를 골랐기 때문에 {@code ./gradlew build}가 곧 생성 명령이 된다. 컨트롤러를 고치고 빌드하면 생성물이 다시
 * 써지고, 달라졌으면 {@code git status}에 뜬다.
 *
 * <p><b>CI는 커밋된 파일이 낡았는지 검사하지 않는다.</b> 비교해 실패시키는 스텝은 두지 않기로 했다. 문서를 바꾸는 변경 중에 {@code @Parameter} 문구
 * 수정처럼 알아채기 어려운 것이 많아, 검사를 넣으면 백엔드를 만지는 PR이 납득하기 어려운 이유로 빨개진다. 대신 갱신을 잊지 않도록 생성을 빌드에 붙였다.
 *
 * <p>다만 이 테스트 자체는 CI에서 돈다. backend job이 {@code ./gradlew build}를 돌리기 때문이다. 그래서 애노테이션이 잘못돼 문서 생성이
 * 깨지는 경우는 CI가 잡는다. 재검토 조건은 {@code backend/README.md}에 있다.
 *
 * <p><b>프로퍼티를 애노테이션에 붙이지 않는다.</b> {@code @SpringBootTest(properties = ...)}로 설정을 주면 컨텍스트 캐시 키가 달라져
 * Postgres 컨테이너를 하나 더 띄운다. 생성 전용 설정({@code springdoc.writer-with-order-by-keys}와 {@code
 * springdoc.cache.disabled})은 {@code build.gradle.kts}의 test 태스크가 시스템 프로퍼티로 준다. 시스템 프로퍼티는 컨텍스트 캐시
 * 키에 들어가지 않아 다른 통합 테스트와 같은 컨텍스트를 쓴다. {@code application.properties}에 넣지 않는 이유는 그쪽에 적어 뒀다.
 *
 * <p>{@code @AutoConfigureMockMvc} 대신 {@link WebApplicationContext}에서 직접 만드는 것도 같은 이유다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class OpenApiDocumentTest {

  /**
   * 생성물을 쓸 절대 경로. Gradle이 넘긴다 (build.gradle.kts).
   *
   * <p>가리키는 곳은 {@code build/} 안이다. 커밋 대상인 {@code backend/openapi.yaml}로 옮기는 것은 별도 Gradle 태스크의 몫이라,
   * 이 테스트가 실패하면 커밋된 파일은 손대지 않은 채로 남는다.
   */
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
    // 스킵하지 않고 실패시킨다. CI가 최신성을 안 보기로 한 이상 이 테스트가 유일한 안전망인데, 프로퍼티가
    // 어떤 이유로든 안 넘어왔을 때 조용히 통과하면 그 안전망이 소리 없이 꺼진다.
    assertThat(path).as("생성물 경로가 안 넘어왔다. `./gradlew test`로 돌려라 (IDE라면 Gradle 위임 설정)").isNotBlank();

    MvcTestResult result = mvc.get().uri(YAML_DOCUMENT).exchange();
    assertThat(result).hasStatusOk();

    Path target = Path.of(path);
    Files.createDirectories(target.getParent());
    // 마지막 줄에 개행을 붙인다. springdoc 출력은 개행 없이 끝나 diff가 "\ No newline at end of file"로 어지럽다.
    Files.writeString(target, body(result).stripTrailing() + "\n", StandardCharsets.UTF_8);
  }

  @Test
  void 같은_코드에서_두_번_뽑으면_같은_바이트다() {
    // 이 성질이 깨지면 컨트롤러를 고치지 않아도 생성물이 매번 달라져, git status에 뜨는 것이 신호 구실을 못 한다.
    // CI가 최신성을 잡아 주지 않으므로 그 신호가 유일한 안전망이다.
    //
    // 이 검사가 성립하려면 springdoc 캐시가 꺼져 있어야 한다. 켜져 있으면 두 번째 요청이 같은 OpenAPI 객체를
    // 다시 직렬화할 뿐이라 Jackson의 안정성만 보게 되고, 정작 흔들릴 수 있는 모델 구축 단계(핸들러 순회,
    // 리플렉션 필드 순서, 스키마 해석)를 한 번도 안 지난다. 끄는 것은 build.gradle.kts의 test 태스크가 한다.
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
    // 비워 두면 springdoc이 요청 URL에서 만들어 내서 로컬과 CI의 생성물이 갈린다.
    assertThat(json()).bodyJson().extractingPath("$.servers[*].url").asArray().containsExactly("/");
  }

  // ---------------------------------------------------------------------------
  // 오퍼레이션 (컨트롤러 정의만으로 자동 반영되는지)
  // ---------------------------------------------------------------------------

  @Test
  void 컨트롤러의_모든_오퍼레이션이_들어_있다() {
    // 정확히 이 셋이어야 한다. 빠진 것뿐 아니라 의도치 않게 늘어난 것(actuator 노출 같은)도 잡는다.
    // 엔드포인트를 추가하면 이 줄도 같이 고친다.
    assertThat(json())
        .bodyJson()
        .extractingPath("$.paths")
        .asMap()
        .containsOnlyKeys(
            "/v1/events",
            "/v1/usage",
            "/v1/invoice",
            "/v1/customers",
            "/v1/customers/{id}",
            "/v1/billable-metrics",
            "/v1/billable-metrics/{code}/price-policy",
            "/v1/price-policies");

    assertThat(json()).bodyJson().extractingPath("$.paths['/v1/events'].post.summary").isNotNull();
    assertThat(json()).bodyJson().extractingPath("$.paths['/v1/events'].get.summary").isNotNull();
    assertThat(json()).bodyJson().extractingPath("$.paths['/v1/usage'].get.summary").isNotNull();
    assertThat(json()).bodyJson().extractingPath("$.paths['/v1/invoice'].get.summary").isNotNull();
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
        .extractingPath("$.paths['/v1/price-policies'].get.summary")
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
    // 전역 SNAKE_CASE 대신 DTO의 @JsonProperty로 못박는 방식이라, 그 애노테이션을 빠뜨리면 문서만 자바
    // 필드명(camelCase)으로 나가고 프론트는 없는 필드를 읽는다.
    //
    // 문서 전체 문자열에서 찾으면 안 된다. @Operation(description)의 산문에 "transaction_id 내림차순"
    // 같은 문장이 있어서, @JsonProperty를 전부 지워도 통과한다 (실측). 스키마 안을 봐야 한다.
    assertSchemaHasField("EventResponse", "transaction_id");
    assertSchemaHasField("EventResponse", "customer_name");
    assertSchemaHasField("IngestEventRequest", "customer_id");
    assertSchemaHasField("IngestEventResponse", "transaction_id");
    assertSchemaHasField("BillableMetricUsageResponse", "target_property");
    assertSchemaHasField("DraftInvoiceCustomerEntry", "customer_id");
    assertSchemaHasField("DraftInvoiceResponse", "total_amount");
    assertSchemaHasField("CustomerResponse", "customer_id");
    assertSchemaHasField("SavePricePolicyRequest", "dimension_properties");
    assertSchemaHasField("PricePolicyResponse", "billable_metric_code");
    assertSchemaHasField("CustomerResponse", "created_at");
    assertSchemaHasField("PricePolicyListResponse", "price_policies");
    assertSchemaHasField("MetricPricePolicyResponse", "billable_metric_code");
    assertSchemaHasField("MetricPricePolicyResponse", "dimension_properties");
    assertSchemaHasField("MetricPricePolicyResponse", "unit_price");
    assertThat(json())
        .bodyJson()
        .extractingPath(
            "$.components.schemas.MetricPricePolicyResponse.properties.dimension_properties.type")
        .asArray()
        .contains("null");
    assertThat(json())
        .bodyJson()
        .extractingPath("$.components.schemas.MetricPricePolicyResponse.properties.unit_price.type")
        .asArray()
        .contains("null");

    // 자바 필드명이 문서 어디로도 새지 않는다.
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
            "pricePolicies",
            "createdAt");
  }

  // ---------------------------------------------------------------------------
  // 손으로 잡아 준 스키마 (PR #24에서 실측으로 바로잡은 것들)
  // ---------------------------------------------------------------------------

  @Test
  void properties가_문자열이_아니라_객체다() {
    // @JsonRawValue를 붙인 String이라 자바 타입만 보면 type: string으로 나간다. @Schema로 덮어 뒀다.
    assertThat(json())
        .bodyJson()
        .extractingPath("$.components.schemas.EventResponse.properties.properties.type")
        .isEqualTo("object");
  }

  @Test
  void 모든_400이_200_스키마를_물려받지_않는다() {
    // @ApiResponse에 content를 안 주면 400 스키마가 그 오퍼레이션의 200 스키마로 나간다. 넷 중 하나만
    // 잡아 두면 나머지 셋이 조용히 틀린 채로 커밋된다 (MS2-140에서 실제로 그런 상태를 발견했다).
    //
    // 어느 스키마를 가리키는지도 함께 본다. 넷이 같아야 한다.
    //
    // [2026-08-17, MS2-150 7단계] 예전에는 "code가 붙는 쪽(/v1/events)과 안 붙는 쪽이 갈린다"고 적혀
    // 있었다. 4단계가 프레임워크 4xx 전부에 code를 붙여 그 구분이 없어졌고, 7단계가 스키마를 하나로
    // 합쳤다. 다시 갈리면 그것은 회귀이므로 넷을 같은 이름으로 못박아 둔다.
    assertProblemSchema("/v1/events", "get", "ProblemResponse");
    assertProblemSchema("/v1/events", "post", "ProblemResponse");
    assertProblemSchema("/v1/usage", "get", "ProblemResponse");
    assertProblemSchema("/v1/invoice", "get", "ProblemResponse");
    assertProblemSchema("/v1/customers", "get", "ProblemResponse");
    assertProblemSchema("/v1/customers", "post", "ProblemResponse");
    assertProblemSchema("/v1/customers/{id}", "put", "ProblemResponse");
    assertProblemSchema("/v1/customers/{id}", "delete", "ProblemResponse");
    assertProblemSchema("/v1/billable-metrics", "post", "ProblemResponse");
    assertProblemSchema("/v1/billable-metrics", "get", "ProblemResponse");
    assertProblemSchema("/v1/billable-metrics/{code}/price-policy", "post", "ProblemResponse");
    assertProblemSchema("/v1/price-policies", "get", "ProblemResponse");
  }

  /**
   * 400 말고 다른 오류 상태도 마찬가지다 (MS2-155, 가격 정책은 MS2-157).
   *
   * <p>고객 API가 처음으로 404와 409를 쓴다. 위 테스트가 400만 보므로, content를 빠뜨린 404가 CustomerResponse 스키마를 물려받아도
   * 아무도 알아채지 못한다. 삭제의 204는 본문이 없어 볼 것이 없다.
   */
  @Test
  void 다른_오류_상태도_200_스키마를_물려받지_않는다() {
    assertProblemSchema("/v1/customers/{id}", "put", "404", "ProblemResponse");
    assertProblemSchema("/v1/customers/{id}", "delete", "404", "ProblemResponse");
    assertProblemSchema("/v1/customers/{id}", "delete", "409", "ProblemResponse");
    assertProblemSchema("/v1/billable-metrics", "post", "409", "ProblemResponse");
    assertProblemSchema(
        "/v1/billable-metrics/{code}/price-policy", "post", "404", "ProblemResponse");
    assertProblemSchema(
        "/v1/billable-metrics/{code}/price-policy", "post", "409", "ProblemResponse");
  }

  @Test
  void 오류_스키마가_실제_본문과_같은_모양이다() {
    // ProblemDetail을 그대로 물리면 springdoc이 확장 멤버를 담는 Map 필드를 그대로 읽어, 응답에 없는
    // properties 객체가 스키마에 생기고 정작 최상위로 나가는 code와 errors는 빠진다 (PR #31 리뷰).
    // Jackson이 @JsonAnyGetter로 맵을 펼치는 것을 springdoc이 모르기 때문이다.
    assertSchemaHasField("ProblemResponse", ProblemMembers.CODE);
    assertSchemaHasField("ProblemResponse", ProblemMembers.ERRORS);
    assertSchemaHasField("ProblemFieldError", ProblemMembers.FIELD);
    assertSchemaHasField("ProblemFieldError", ProblemMembers.MESSAGE);

    // [2026-08-17, 8단계] assertSchemaHasNoField(..., "properties") 두 줄을 지웠다 (MS2-150 [0-B] 21).
    // ProblemResponse에 properties 컴포넌트가 없으니 그 단언은 어떤 변경으로도 빨개지지 않는다. 잡으려던
    // 회귀(ProblemDetail을 그대로 스키마로 물리는 것)는 바로 아래 doesNotContainKey와 이 아래 키 집합
    // 대조가 이미 양방향으로 잡는다. 절대 실패할 수 없는 단언은 검사 개수만 늘리고 통과를 근거로 쓰게 만든다.

    // 문서 전용 타입으로 갈아탄 뒤에도 프레임워크 타입이 문서에 남아 있으면, 어느 쪽이 계약인지 갈린다.
    assertThat(json())
        .bodyJson()
        .extractingPath("$.components.schemas")
        .asMap()
        .doesNotContainKey("ProblemDetail");

    // ---- 여기부터가 이름값을 하게 만드는 부분 (MS2-150 인수기준 6) ----
    //
    // 2026-08-17까지 이 테스트는 이름과 달리 실제 본문을 한 번도 보지 않았다. 문서가 어떤 모양인지만 보므로
    // 400을 code 있는 스키마로 문서화해 놓고 실제로는 code 없는 응답을 내도 통과했고, MS2-150 이전이 정확히
    // 그 상태였다. 보증하지 않는 것을 보증한다고 읽히는 이름이라 없는 것보다 위험했다.
    //
    // 그래서 문서 프로퍼티 키 집합과 실제 본문 최상위 키 집합을 맞댄다. 한 줄이 양방향을 잡는다.
    // 문서에만 있는 필드(과거의 type)와 응답에만 있는 필드(누가 setProperty를 새로 추가한 경우)가 모두 걸린다.
    //
    // errors는 code=validation_error일 때만 실리므로 한 응답으로는 집합이 안 찬다. 실리는 응답과 안 실리는
    // 응답의 합집합을 쓴다. 그 조건 자체는 ProblemResponse javadoc과 스키마 description에 적혀 있다.
    //
    // 상태와 핸들러를 갈라서 태운다. 합집합이라 경로를 늘리면 잡을 수 있는 것만 늘고 놓치는 것은 줄어든다.
    // 400(프레임워크), 404, 405, 415, 그리고 다른 컨트롤러의 400까지 넣는다.
    //
    // [정본과 다른 자리에 뒀다] MS2-150 인수기준 6은 이 대조를 EventIngestIntegrationTest에 넣으라고 적었다.
    // 이유가 "컨텍스트 재사용"이었는데, 두 테스트가 같은 @SpringBootTest 컨텍스트를 쓰므로 그 이득은 어느
    // 쪽에 둬도 같다. 반면 대조하려면 문서와 응답이 <b>둘 다</b> 필요하고 문서를 꺼내는 json()이 여기 있다.
    // 저쪽에 두면 문서 조회 코드를 복제하게 된다.
    Set<String> documented = keysOf(json(), "$.components.schemas.ProblemResponse.properties");
    Set<String> actual = new TreeSet<>();
    actual.addAll(keysOf(mvc.get().uri("/v1/events").exchange(), "$")); // 400, errors 있음
    actual.addAll(keysOf(mvc.get().uri("/v1/nope").exchange(), "$")); // 404, errors 없음
    actual.addAll(keysOf(mvc.method(HttpMethod.DELETE).uri("/v1/events").exchange(), "$")); // 405
    actual.addAll(
        keysOf(
            mvc.post().uri("/v1/events").contentType(MediaType.TEXT_PLAIN).content("{}").exchange(),
            "$")); // 415
    actual.addAll(keysOf(mvc.get().uri("/v1/usage").exchange(), "$")); // 다른 컨트롤러의 400

    assertThat(actual)
        .as(
            "문서 프로퍼티와 실제 400 본문의 최상위 키가 갈렸다. 문서에만: %s / 응답에만: %s",
            difference(documented, actual), difference(actual, documented))
        .isEqualTo(documented);

    // 경로에 따라 갈리는 두 필드는 그 사유가 생성물에 실려야 한다 (MS2-150 인수기준 4).
    //
    // javadoc에 적는 것으로는 안 된다. 계약을 읽는 쪽은 openapi.yaml만 보고 javadoc은 거기 실리지 않는다.
    // code와 errors는 응답마다 있고 없고가 갈리므로, 사유가 없으면 FE가 "가끔 없는 필드"를 만나 놓고
    // 그것이 규약인지 버그인지 판단할 근거가 없다.
    assertSchemaFieldHasDescription("ProblemResponse", ProblemMembers.CODE);
    assertSchemaFieldHasDescription("ProblemResponse", ProblemMembers.ERRORS);
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
  private void assertProblemSchema(String path, String method, String schema) {
    assertProblemSchema(path, method, "400", schema);
  }

  /** 400 말고 다른 오류 상태를 볼 때 쓴다 (MS2-155의 404, 409). */
  private void assertProblemSchema(String path, String method, String status, String schema) {
    assertThat(json())
        .bodyJson()
        .extractingPath(
            "$.paths['%s'].%s.responses['%s'].content['application/problem+json'].schema.$ref"
                .formatted(path, method, status))
        .asString()
        .isEqualTo("#/components/schemas/%s".formatted(schema));
  }

  /**
   * 응답 본문에서 주어진 경로가 가리키는 객체의 키 집합을 꺼낸다.
   *
   * <p>기대 키를 테스트에 손으로 적지 않으려고 양쪽을 같은 방법으로 꺼낸다. 손으로 적으면 스키마의 사본이 하나 더 생겨서, 필드가 늘 때 사본이 갈리거나(유형 I)
   * 사본만 고치고 통과하는 일이 난다.
   */
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

  /**
   * {@code $.a.b} 형태를 Jackson JSON Pointer({@code /a/b})로 바꾼다. {@code $}는 루트다.
   *
   * <p><b>점 표기만 다룬다.</b> 이 파일의 다른 단언이 쓰는 {@code $.paths['/v1/events']} 같은 대괄호 표기를 넣으면 조용히 엉뚱한 포인터가
   * 만들어져 "키가 비었다"는 엉뚱한 실패로 나타난다. 그래서 대괄호를 만나면 그 자리에서 세운다. 검사기가 틀린 답을 내는 것보다 안 도는 편이 낫다(유형 F).
   */
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
