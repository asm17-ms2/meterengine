package com.meterengine.global;

import static org.assertj.core.api.Assertions.assertThat;

import com.meterengine.TestcontainersConfiguration;
import com.meterengine.global.error.ErrorCode;
import java.util.UUID;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

@Import({
  TestcontainersConfiguration.class,
  GlobalExceptionHandlerIntegrationTest.ThrowingController.class
})
@SpringBootTest
@Transactional
class GlobalExceptionHandlerIntegrationTest {

  private static final String ORGANIZATION = "d7cee55d-8c82-4afc-b996-6749d8b26a4e";

  private static final String INVALID_BODY =
      """
      {"transaction_id":"","customer_id":null,"type":"",
       "timestamp":null,"properties":{"token":1}}
      """;

  private static final String UNKNOWN_CUSTOMER_BODY =
      """
      {"transaction_id":"probe-1","customer_id":"11111111-2222-3333-4444-555555555555",
       "type":"chat_completion","timestamp":"2026-08-17T12:00:00Z","properties":{"token":1}}
      """;

  @Autowired private WebApplicationContext webApplicationContext;

  private MockMvcTester mvc;

  @BeforeEach
  void setUp() {
    mvc = MockMvcTester.from(webApplicationContext);
  }

  @Test
  void 깨진_JSON_본문은_400이고_code가_malformed_request_body다() {
    assertCode(post("{\"bad"), 400, ErrorCode.MALFORMED_REQUEST_BODY.getCode());
  }

  @Test
  void 빈_본문은_400이고_code가_malformed_request_body다() {
    assertCode(post(""), 400, ErrorCode.MALFORMED_REQUEST_BODY.getCode());
  }

  @Test
  void timestamp에_오프셋이_없으면_400이고_code가_malformed_request_body다() {
    String body =
        """
        {"transaction_id":"t","customer_id":"a728e7b6-d82b-4f3c-a960-a66a02794c1d",
         "type":"chat_completion","timestamp":"2026-08-17T12:00:00","properties":{"token":1}}
        """;
    assertCode(post(body), 400, ErrorCode.MALFORMED_REQUEST_BODY.getCode());
  }

  @Test
  void Content_Type이_지원되지_않으면_415이고_code가_붙는다() {
    MvcTestResult result =
        mvc.post()
            .uri("/v1/events")
            .header("X-Organization-Id", ORGANIZATION)
            .contentType(MediaType.TEXT_PLAIN)
            .content("{}")
            .exchange();
    assertCode(result, 415, ErrorCode.REQUEST_TYPE_NOT_SUPPORTED.getCode());
  }

  @Test
  void 없는_경로는_404이고_code가_endpoint_not_found다() {
    MvcTestResult result =
        mvc.get().uri("/v1/nope").header("X-Organization-Id", ORGANIZATION).exchange();
    assertCode(result, 404, ErrorCode.ENDPOINT_NOT_FOUND.getCode());
  }

  @Test
  void 허용되지_않은_메서드는_405이고_code가_붙는다() {
    MvcTestResult result =
        mvc.method(HttpMethod.DELETE)
            .uri("/v1/events")
            .header("X-Organization-Id", ORGANIZATION)
            .exchange();
    assertCode(result, 405, ErrorCode.METHOD_NOT_ALLOWED.getCode());
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
    assertCode(result, 406, ErrorCode.RESPONSE_TYPE_NOT_ACCEPTABLE.getCode());
  }

  @Test
  void 세_엔드포인트의_400이_모두_같은_code를_갖는다() {
    for (String path : new String[] {"/v1/usage", "/v1/invoices/draft", "/v1/events"}) {
      MvcTestResult result =
          mvc.get()
              .uri(path)
              .param("month", "2026-13")
              .header("X-Organization-Id", ORGANIZATION)
              .exchange();
      assertCode(result, 400, ErrorCode.VALIDATION_ERROR.getCode());
    }
  }

  @Test
  void 도입사가_보낸_값이_응답에_돌아오지_않는다() {
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

  @Test
  void 본문_검증의_field가_자바_이름이_아니라_JSON_키다() {
    assertThat(post(INVALID_BODY))
        .bodyJson()
        .extractingPath("$.errors[*].field")
        .asArray()
        .contains("transaction_id", "customer_id", "timestamp")
        .doesNotContain("transactionId", "customerId", "occurredAt");
  }

  @Test
  void 이름을_명시한_쿼리_파라미터의_field가_요청에_쓴_이름이다() {
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
    MvcTestResult result =
        mvc.get().uri("/v1/events").header("X-Organization-Id", "not-a-uuid").exchange();

    assertFields(result, "X-Organization-Id");
  }

  @Test
  void 어느_경로로도_camelCase_이름이_새지_않는다() {
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
          .extractingPath("$.errors[*].field")
          .asArray()
          .allSatisfy(
              field ->
                  assertThat((String) field)
                      .as("errors[].field에 camelCase가 남았다")
                      .doesNotMatch(".*[a-z][A-Z].*"));
    }
  }

  @Test
  void 프레임워크가_준_문구가_한국어다() {
    assertMessages(post(INVALID_BODY), "공백일 수 없습니다");
  }

  @Test
  void 우리가_만든_문구도_한국어다() {
    assertMessages(mvc.get().uri("/v1/events").exchange(), "필수 항목입니다");
    assertMessages(
        mvc.get().uri("/v1/events").header("X-Organization-Id", "not-a-uuid").exchange(),
        "UUID 형식이어야 합니다");
  }

  @Test
  void Accept_Language를_보내도_문구가_바뀌지_않는다() {
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
          .extractingPath("$.errors[*].message")
          .asArray()
          .allSatisfy(
              message ->
                  assertThat((String) message)
                      .as("errors[].message에 한글이 없다. 번역이 빠진 자리다")
                      .matches(".*[가-힣].*"));
    }
  }

  @Test
  void 어느_4xx에도_problem_json_멤버가_실리지_않는다() {
    MvcTestResult[] responses = {
      post(INVALID_BODY),
      post(UNKNOWN_CUSTOMER_BODY),
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
      mvc.delete()
          .uri("/v1/customers/" + UUID.randomUUID())
          .header("X-Organization-Id", ORGANIZATION)
          .exchange(),
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
      assertThat(result).hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON);
      assertThat(result)
          .bodyJson()
          .extractingPath("$")
          .asMap()
          .containsKey("code")
          .containsKey("message")
          .doesNotContainKeys("type", "title", "status", "detail", "instance");
    }
  }

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

  @Test
  void 모르는_예외는_500과_internal_server_error로_나간다() {
    MvcTestResult result = throwUnknown();

    assertThat(result).hasStatus(500).hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON);
    assertThat(result)
        .bodyJson()
        .extractingPath("$.code")
        .asString()
        .isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR.getCode());
    assertThat(result)
        .bodyJson()
        .extractingPath("$.message")
        .asString()
        .isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR.getMessage());
  }

  @Test
  void 오류_본문에는_code와_message만_실린다() {
    MvcTestResult result = throwUnknown();

    assertThat(result).bodyJson().extractingPath("$").asMap().containsOnlyKeys("code", "message");
  }

  @Test
  void 예외의_원인은_본문에_실리지_않는다() {
    MvcTestResult result = throwUnknown();

    assertThat(result).bodyText().doesNotContain(ThrowingController.CAUSE);
  }

  private MvcTestResult throwUnknown() {
    return mvc.get().uri(ThrowingController.PATH).exchange();
  }

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
        .extractingPath("$.errors[*].message")
        .asArray()
        .contains((Object[]) messages);
  }

  private void assertFields(MvcTestResult result, String... fields) {
    assertThat(result).hasStatus(400);
    assertThat(result)
        .bodyJson()
        .extractingPath("$.errors[*].field")
        .asArray()
        .contains((Object[]) fields);
  }

  private void assertCode(MvcTestResult result, int status, String code) {
    assertThat(result).hasStatus(status);
    assertThat(result).bodyJson().extractingPath("$.code").asString().isEqualTo(code);
  }

  @RestController
  static class ThrowingController {

    static final String PATH = "/test/throw";
    static final String CAUSE = "thrown on purpose by the test";

    @GetMapping(PATH)
    void throwUnknown() {
      throw new IllegalStateException(CAUSE);
    }
  }
}
