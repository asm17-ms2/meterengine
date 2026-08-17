package com.meterengine;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
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
        .containsOnlyKeys("/v1/events", "/v1/usage", "/v1/invoice");

    assertThat(json()).bodyJson().extractingPath("$.paths['/v1/events'].post.summary").isNotNull();
    assertThat(json()).bodyJson().extractingPath("$.paths['/v1/events'].get.summary").isNotNull();
    assertThat(json()).bodyJson().extractingPath("$.paths['/v1/usage'].get.summary").isNotNull();
    assertThat(json()).bodyJson().extractingPath("$.paths['/v1/invoice'].get.summary").isNotNull();
  }

  @Test
  void 쿼리_파라미터_이름이_실제_요청과_같다() {
    assertThat(json())
        .bodyJson()
        .extractingPath("$.paths['/v1/events'].get.parameters[*].name")
        .asArray()
        .contains("X-Organization-Id", "page", "size", "customer_id", "month", "event_type");
  }

  @Test
  void 스키마_필드_이름이_실제_JSON과_같다() {
    // 전역 SNAKE_CASE 대신 DTO의 @JsonProperty로 못박는 방식이라, 그 애노테이션을 빠뜨리면 문서만 자바
    // 필드명(camelCase)으로 나가고 프론트는 없는 필드를 읽는다.
    //
    // 문서 전체 문자열에서 찾으면 안 된다. @Operation(description)의 산문에 "transaction_id 내림차순"
    // 같은 문장이 있어서, @JsonProperty를 전부 지워도 통과한다 (실측). 스키마 안을 봐야 한다.
    assertSchemaHasField("EventEntry", "transaction_id");
    assertSchemaHasField("EventEntry", "customer_name");
    assertSchemaHasField("EventIngestRequest", "customer_id");
    assertSchemaHasField("EventIngestResponse", "transaction_id");
    assertSchemaHasField("MetricEntry", "target_property");
    assertSchemaHasField("CustomerEntry", "customer_id");
    assertSchemaHasField("DraftInvoiceResponse", "total_amount");

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
            "calculatedAt");
  }

  // ---------------------------------------------------------------------------
  // 손으로 잡아 준 스키마 (PR #24에서 실측으로 바로잡은 것들)
  // ---------------------------------------------------------------------------

  @Test
  void properties가_문자열이_아니라_객체다() {
    // @JsonRawValue를 붙인 String이라 자바 타입만 보면 type: string으로 나간다. @Schema로 덮어 뒀다.
    assertThat(json())
        .bodyJson()
        .extractingPath("$.components.schemas.EventEntry.properties.properties.type")
        .isEqualTo("object");
  }

  @Test
  void 모든_400이_200_스키마를_물려받지_않는다() {
    // @ApiResponse에 content를 안 주면 400 스키마가 그 오퍼레이션의 200 스키마로 나간다. 넷 중 하나만
    // 잡아 두면 나머지 셋이 조용히 틀린 채로 커밋된다 (MS2-140에서 실제로 그런 상태를 발견했다).
    assertProblemDetail("/v1/events", "get");
    assertProblemDetail("/v1/events", "post");
    assertProblemDetail("/v1/usage", "get");
    assertProblemDetail("/v1/invoice", "get");
  }

  // ---------------------------------------------------------------------------

  private void assertSchemaHasField(String schema, String field) {
    assertThat(json())
        .bodyJson()
        .extractingPath("$.components.schemas.%s.properties.%s".formatted(schema, field))
        .isNotNull();
  }

  /** 400 응답이 problem+json의 {@code ProblemDetail}을 가리키는지 본다. */
  private void assertProblemDetail(String path, String method) {
    assertThat(json())
        .bodyJson()
        .extractingPath(
            "$.paths['%s'].%s.responses['400'].content['application/problem+json'].schema.$ref"
                .formatted(path, method))
        .asString()
        .endsWith("ProblemDetail");
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
