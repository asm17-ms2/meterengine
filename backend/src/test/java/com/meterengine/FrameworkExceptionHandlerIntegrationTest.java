package com.meterengine;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/**
 * 프레임워크가 내는 4xx가 전부 {@code code}를 갖는지 실제 응답으로 확인한다 (MS2-150 4단계).
 *
 * <p><b>문서만 검사해서는 안 되는 이유.</b> {@code OpenApiDocumentTest}는 생성된 스키마가 어떤 모양인지만 본다. 400을 code 있는 스키마로
 * 문서화해 놓고 실제로는 code 없는 응답을 내도 통과한다. MS2-150 이전이 정확히 그 상태였다. 그래서 이쪽은 <b>본문을 직접 받아</b> 본다.
 *
 * <p><b>{@code errors[].field}가 와이어 이름인지도 여기서 본다</b> (MS2-150 5단계, 인수기준 9). 4단계까지는 단언하지 않았는데, 그때는
 * 본문 검증이 자바 이름을 내보내고 있어 이름을 박으면 5단계에서 다시 고쳐야 했기 때문이다. 이제 A-2가 코드에 들어갔으므로 그 유보를 거둔다.
 *
 * <p><b>문서 검사로 대신할 수 없다.</b> {@code OpenApiDocumentTest}가 보는 것은 {@code ProblemFieldError}의 example
 * 문자열이고, 그것은 사람이 적은 값이라 변환이 깨져도 그대로 있는다. 이름이 실제로 변환되는지는 응답을 받아 봐야 안다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class FrameworkExceptionHandlerIntegrationTest {

  private static final String ORGANIZATION = "d7cee55d-8c82-4afc-b996-6749d8b26a4e";

  /**
   * 자바 이름과 와이어 이름이 갈리는 세 필드를 한꺼번에 비운 본문.
   *
   * <p>{@code eventType -> event_type} 변환이 5단계의 핵심이고, 나머지 둘은 변환이 한 필드에만 걸리지 않았는지 본다. 6단계의 문구 단언도 같은
   * 응답을 쓴다.
   */
  private static final String INVALID_BODY =
      """
      {"transaction_id":"","customer_id":null,"event_type":"",
       "timestamp":"2026-08-17T12:00:00Z","properties":{"token":1}}
      """;

  /**
   * 형식은 멀쩡하고 가리킨 고객만 없는 본문. 도메인 advice({@code EventExceptionHandler})를 태우는 데 쓴다.
   *
   * <p>프레임워크 핸들러만 보면 advice가 만드는 응답 모양을 놓친다. 둘이 같은 규약을 지키는지 함께 봐야 한다.
   */
  private static final String UNKNOWN_CUSTOMER_BODY =
      """
      {"transaction_id":"probe-1","customer_id":"11111111-2222-3333-4444-555555555555",
       "event_type":"chat_completion","timestamp":"2026-08-17T12:00:00Z","properties":{"token":1}}
      """;

  @Autowired private WebApplicationContext webApplicationContext;

  private MockMvcTester mvc;

  @BeforeEach
  void setUp() {
    mvc = MockMvcTester.from(webApplicationContext);
  }

  // ---------------------------------------------------------------------------
  // code가 없던 400 세 건 (MS2-150 [1-2])
  // ---------------------------------------------------------------------------

  @Test
  void 깨진_JSON_본문은_400이고_code가_malformed_request_body다() {
    assertCode(post("{\"bad"), 400, ErrorCodes.MALFORMED_REQUEST_BODY);
  }

  @Test
  void 빈_본문은_400이고_code가_malformed_request_body다() {
    assertCode(post(""), 400, ErrorCodes.MALFORMED_REQUEST_BODY);
  }

  @Test
  void timestamp에_오프셋이_없으면_400이고_code가_malformed_request_body다() {
    // Jackson이 OffsetDateTime으로 못 바꿔서 파싱 단계에서 끊긴다. 필드를 짚을 수 없으므로
    // validation_error가 아니다 (ErrorCodes.MALFORMED_REQUEST_BODY javadoc 참조).
    String body =
        """
        {"transaction_id":"t","customer_id":"a728e7b6-d82b-4f3c-a960-a66a02794c1d",
         "event_type":"chat_completion","timestamp":"2026-08-17T12:00:00","properties":{"token":1}}
        """;
    assertCode(post(body), 400, ErrorCodes.MALFORMED_REQUEST_BODY);
  }

  // ---------------------------------------------------------------------------
  // 400이 아닌 4xx 네 건 (MS2-150 [1-3])
  // ---------------------------------------------------------------------------

  @Test
  void Content_Type이_지원되지_않으면_415이고_code가_붙는다() {
    MvcTestResult result =
        mvc.post()
            .uri("/v1/events")
            .header("X-Organization-Id", ORGANIZATION)
            .contentType(MediaType.TEXT_PLAIN)
            .content("{}")
            .exchange();
    assertCode(result, 415, ErrorCodes.REQUEST_TYPE_NOT_SUPPORTED);

    // 프레임워크가 붙이던 응답 헤더가 남아 있어야 한다. 이것이 전역 advice 대신 상속을 고른
    // 이유이고(MS2-150 D-1), 손으로 잡으면 잃는 정보다.
    assertThat(result.getResponse().getHeader("Accept")).contains("application/json");
  }

  @Test
  void 없는_경로는_404이고_code가_endpoint_not_found다() {
    MvcTestResult result =
        mvc.get().uri("/v1/nope").header("X-Organization-Id", ORGANIZATION).exchange();
    assertCode(result, 404, ErrorCodes.ENDPOINT_NOT_FOUND);
  }

  @Test
  void 허용되지_않은_메서드는_405이고_Allow_헤더가_남는다() {
    MvcTestResult result =
        mvc.method(HttpMethod.DELETE)
            .uri("/v1/events")
            .header("X-Organization-Id", ORGANIZATION)
            .exchange();
    assertCode(result, 405, ErrorCodes.METHOD_NOT_ALLOWED);
    assertThat(result.getResponse().getHeader("Allow")).contains("GET");
  }

  @Test
  void Accept로_만족시킬_표현이_없으면_406이고_code가_붙는다() {
    MvcTestResult result =
        mvc.get()
            .uri("/v1/events")
            .param("month", "2026-08")
            .header("X-Organization-Id", ORGANIZATION)
            .accept(MediaType.TEXT_PLAIN)
            .exchange();
    assertCode(result, 406, ErrorCodes.RESPONSE_TYPE_NOT_ACCEPTABLE);
  }

  // ---------------------------------------------------------------------------
  // 세 엔드포인트가 같은 모양으로 답한다 (MS2-150 B-1, B-4)
  // ---------------------------------------------------------------------------

  @Test
  void 세_엔드포인트의_400이_모두_같은_code를_갖는다() {
    for (String path : new String[] {"/v1/usage", "/v1/invoices/draft", "/v1/events"}) {
      MvcTestResult result =
          mvc.get()
              .uri(path)
              .param("month", "2026-13")
              .header("X-Organization-Id", ORGANIZATION)
              .exchange();
      assertCode(result, 400, ErrorCodes.VALIDATION_ERROR);
    }
  }

  @Test
  void 도입사가_보낸_값이_응답에_돌아오지_않는다() {
    // MS2-150 B-4. 예전에는 /v1/usage와 /v1/invoices/draft가 "Failed to convert 'month' with
    // value: '2026-13'"으로 값을 되돌려줬다. 전역 핸들러가 detail을 우리 문구로 덮어 막는다.
    for (String path : new String[] {"/v1/usage", "/v1/invoices/draft", "/v1/events"}) {
      MvcTestResult result =
          mvc.get()
              .uri(path)
              .param("month", "2026-13")
              .header("X-Organization-Id", "not-a-uuid")
              .exchange();
      assertThat(result).bodyText().doesNotContain("not-a-uuid").doesNotContain("2026-13");
    }
  }

  // ---------------------------------------------------------------------------
  // errors[].field는 와이어 이름이다 (MS2-150 A-2, 인수기준 9)
  // ---------------------------------------------------------------------------

  @Test
  void 본문_검증의_field가_자바_이름이_아니라_JSON_키다() {
    assertThat(post(INVALID_BODY))
        .bodyJson()
        .extractingPath("$.%s[*].%s".formatted(ProblemMembers.ERRORS, ProblemMembers.FIELD))
        .asArray()
        .contains("transaction_id", "customer_id", "event_type")
        .doesNotContain("transactionId", "customerId", "eventType");
  }

  @Test
  void 이름을_명시한_쿼리_파라미터의_field가_요청에_쓴_이름이다() {
    // @RequestParam(name = "customer_id")라 자바 파라미터 이름과 갈린다. UUID로 못 바꿔서
    // MethodArgumentTypeMismatchException 경로로 온다.
    MvcTestResult result =
        mvc.get()
            .uri("/v1/events")
            .param("customer_id", "not-a-uuid")
            .header("X-Organization-Id", ORGANIZATION)
            .exchange();

    assertFields(result, "customer_id");
  }

  @Test
  void 이름을_안_준_쿼리_파라미터의_field가_자바_파라미터_이름_그대로다() {
    // size는 @RequestParam에 이름을 안 줬고 자바 이름이 곧 요청 이름이다. @Max(100) 위반이라
    // HandlerMethodValidationException 경로로 온다. 위 경로와 다른 분기다.
    MvcTestResult result =
        mvc.get()
            .uri("/v1/events")
            .param("size", "101")
            .header("X-Organization-Id", ORGANIZATION)
            .exchange();

    assertFields(result, "size");
  }

  @Test
  void 누락된_헤더의_field가_헤더_이름이다() {
    MvcTestResult result = mvc.get().uri("/v1/events").exchange();

    assertFields(result, "X-Organization-Id");
  }

  @Test
  void 타입이_틀린_헤더의_field가_헤더_이름이다() {
    // 누락과 다른 경로다. 누락은 MissingRequestHeaderException, 이쪽은
    // MethodArgumentTypeMismatchException으로 온다. 헤더는 자바 파라미터 이름(organizationId)과
    // 와이어 이름이 갈리는 자리라, 경로가 갈리면 한쪽만 자바 이름을 낼 수 있다.
    MvcTestResult result =
        mvc.get().uri("/v1/events").header("X-Organization-Id", "not-a-uuid").exchange();

    assertFields(result, "X-Organization-Id");
  }

  @Test
  void 어느_경로로도_camelCase_이름이_새지_않는다() {
    // 위 네 건은 아는 이름을 짚어 본 것이고, 이것은 모르는 이름까지 거른다. 새 필드나 새 파라미터가
    // 늘었을 때 @JsonProperty나 @RequestParam(name=...)을 빠뜨리면 여기 걸린다.
    for (MvcTestResult result :
        new MvcTestResult[] {
          post(INVALID_BODY),
          mvc.get()
              .uri("/v1/events")
              .param("customer_id", "not-a-uuid")
              .param("size", "101")
              .header("X-Organization-Id", ORGANIZATION)
              .exchange(),
          mvc.get().uri("/v1/events").exchange(),
          mvc.get().uri("/v1/events").header("X-Organization-Id", "not-a-uuid").exchange()
        }) {
      assertThat(result)
          .bodyJson()
          .extractingPath("$.%s[*].%s".formatted(ProblemMembers.ERRORS, ProblemMembers.FIELD))
          .asArray()
          .allSatisfy(
              field ->
                  assertThat((String) field)
                      .as("errors[].field에 camelCase가 남았다")
                      .doesNotMatch(".*[a-z][A-Z].*"));
    }
  }

  // ---------------------------------------------------------------------------
  // errors[].message는 로케일과 무관하게 한국어다 (MS2-150 6단계, B-2)
  // ---------------------------------------------------------------------------

  @Test
  void 프레임워크가_준_문구가_한국어다() {
    // Bean Validation 제약 문구다. Hibernate Validator의 ko 번들에서 온다.
    assertMessages(post(INVALID_BODY), "공백일 수 없습니다");
  }

  @Test
  void 우리가_만든_문구도_한국어다() {
    // 이 둘은 제약 위반이 아니라 바인딩 실패라 Bean Validation이 문구를 만들지 않는다.
    // messages.properties에서 온다. 6단계 전에는 이 둘만 영어로 남아 한 계약 안에서 언어가 섞였다.
    assertMessages(mvc.get().uri("/v1/events").exchange(), "필수 항목입니다");
    assertMessages(
        mvc.get().uri("/v1/events").header("X-Organization-Id", "not-a-uuid").exchange(),
        "UUID 형식이어야 합니다");
  }

  @Test
  void Accept_Language를_보내도_문구가_바뀌지_않는다() {
    // 리졸버가 fixed라 Accept-Language를 무시한다 (application.properties 참조). 이 단언이 없으면
    // accept-header로 되돌아갔을 때 en 요청이 반만 번역된 응답을 받는 것을 아무도 모른다. 실제로
    // 6단계 구현 도중 그 상태가 났다.
    MvcTestResult english =
        mvc.post()
            .uri("/v1/events")
            .header("X-Organization-Id", ORGANIZATION)
            .header("Accept-Language", "en")
            .contentType(MediaType.APPLICATION_JSON)
            .content(INVALID_BODY)
            .exchange();

    assertMessages(english, "공백일 수 없습니다");
  }

  @Test
  void 어느_경로로도_영어_문구가_새지_않는다() {
    // 아는 문구를 짚는 위 셋과 달리 모르는 문구까지 거른다. 제약이나 파라미터가 늘었을 때 번역 없는
    // 자리가 생기면 여기 걸린다.
    for (MvcTestResult result :
        new MvcTestResult[] {
          post(INVALID_BODY),
          mvc.get()
              .uri("/v1/events")
              .param("size", "101")
              .header("X-Organization-Id", ORGANIZATION)
              .exchange(),
          mvc.get().uri("/v1/events").exchange(),
          mvc.get().uri("/v1/events").header("X-Organization-Id", "not-a-uuid").exchange()
        }) {
      assertThat(result)
          .bodyJson()
          .extractingPath("$.%s[*].%s".formatted(ProblemMembers.ERRORS, ProblemMembers.MESSAGE))
          .asArray()
          .allSatisfy(
              message ->
                  assertThat((String) message)
                      .as("errors[].message에 한글이 없다. 번역이 빠진 자리다")
                      .matches(".*[가-힣].*"));
    }
  }

  // ---------------------------------------------------------------------------
  // type은 응답에 나가지 않는다 (MS2-150 7단계 착수 결정)
  // ---------------------------------------------------------------------------

  @Test
  void 어느_4xx에도_type이_실리지_않는다() {
    // 이 단언이 ProblemResponse에서 type을 뺀 근거를 지킨다. 값이 기본값 about:blank면 Spring이
    // 직렬화에서 빼고 우리는 setType을 부르지 않는다. 누가 setType을 부르거나 프레임워크가 기본값을
    // 바꾸면 문서에 없는 필드가 응답에 생기므로(인수기준 4의 반대 방향) 여기서 걸린다.
    //
    // 코드 8종과 4xx 네 상태, 두 핸들러(프레임워크/도메인), 세 엔드포인트를 함께 태운다.
    MvcTestResult[] responses = {
      post(INVALID_BODY),
      post("{\"bad"),
      mvc.get().uri("/v1/events").exchange(),
      mvc.get().uri("/v1/events").header("X-Organization-Id", "not-a-uuid").exchange(),
      mvc.post()
          .uri("/v1/events")
          .header("X-Organization-Id", ORGANIZATION)
          .contentType(MediaType.TEXT_PLAIN)
          .content("{}")
          .exchange(),
      mvc.get().uri("/v1/nope").header("X-Organization-Id", ORGANIZATION).exchange(),
      mvc.method(HttpMethod.DELETE)
          .uri("/v1/events")
          .header("X-Organization-Id", ORGANIZATION)
          .exchange(),
      mvc.get()
          .uri("/v1/events")
          .param("month", "2026-08")
          .header("X-Organization-Id", ORGANIZATION)
          .accept(MediaType.TEXT_PLAIN)
          .exchange(),
      post(UNKNOWN_CUSTOMER_BODY),
      mvc.get()
          .uri("/v1/usage")
          .param("month", "2026-13")
          .header("X-Organization-Id", ORGANIZATION)
          .exchange(),
      mvc.get()
          .uri("/v1/invoices/draft")
          .param("month", "2026-13")
          .header("X-Organization-Id", ORGANIZATION)
          .exchange()
    };

    for (MvcTestResult result : responses) {
      assertThat(result).bodyJson().extractingPath("$").asMap().doesNotContainKey("type");
    }
  }

  // ---------------------------------------------------------------------------
  // 5xx는 건드리지 않는다 (MS2-150 B-3)
  // ---------------------------------------------------------------------------

  @Test
  void 정상_요청은_영향을_받지_않는다() {
    assertThat(
            mvc.get()
                .uri("/v1/usage")
                .param("month", "2026-08")
                .header("X-Organization-Id", ORGANIZATION)
                .exchange())
        .hasStatus(200);
  }

  // ---------------------------------------------------------------------------

  private MvcTestResult post(String body) {
    return mvc.post()
        .uri("/v1/events")
        .header("X-Organization-Id", ORGANIZATION)
        .contentType(MediaType.APPLICATION_JSON)
        .content(body)
        .exchange();
  }

  private void assertMessages(MvcTestResult result, String... messages) {
    assertThat(result).hasStatus(400);
    assertThat(result)
        .bodyJson()
        .extractingPath("$.%s[*].%s".formatted(ProblemMembers.ERRORS, ProblemMembers.MESSAGE))
        .asArray()
        .contains((Object[]) messages);
  }

  private void assertFields(MvcTestResult result, String... fields) {
    assertThat(result).hasStatus(400);
    assertThat(result)
        .bodyJson()
        .extractingPath("$.%s[*].%s".formatted(ProblemMembers.ERRORS, ProblemMembers.FIELD))
        .asArray()
        .contains((Object[]) fields);
  }

  private void assertCode(MvcTestResult result, int status, String code) {
    assertThat(result).hasStatus(status);
    assertThat(result)
        .bodyJson()
        .extractingPath("$." + ProblemMembers.CODE)
        .asString()
        .isEqualTo(code);
  }
}
