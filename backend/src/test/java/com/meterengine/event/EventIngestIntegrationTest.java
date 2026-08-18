package com.meterengine.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.meterengine.ErrorCodes;
import com.meterengine.TestcontainersConfiguration;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/**
 * 이벤트 수집 API의 인수 기준을 HTTP 계층부터 DB까지 관통해 검증한다 (MS2-130).
 *
 * <p>범위는 이 하위작업의 인수 기준으로 한정한다. 스키마 자체의 제약(append-only, received_at 트리거, 멱등키 유니크)은
 * SchemaConstraintTest가 이미 검증하므로 여기서는 API를 통과했을 때의 결과만 본다.
 *
 * <p><b>{@code @AutoConfigureMockMvc}를 쓰지 않고 WebApplicationContext에서 직접 만든다.</b> 그 애너테이션이 붙으면 컨텍스트
 * 캐시 키가 달라져서 MeterEngineApplicationTests, SchemaConstraintTest와 컨텍스트를 공유하지 못한다. 그러면
 * TestcontainersConfiguration의 빈이 다시 만들어져 Postgres 컨테이너가 두 번 뜬다(실측). 애너테이션을 빼면 세 테스트가 같은 컨텍스트와 컨테이너
 * 하나를 쓴다. 이 슬라이스에는 서블릿 필터가 없어 Boot가 MockMvc에 얹어 주는 필터 체인도 필요 없다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class EventIngestIntegrationTest {

  private static final String OCCURRED_AT = "2026-08-10T12:00:00+09:00";

  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private JdbcTemplate jdbc;

  private MockMvcTester mvc;

  @BeforeEach
  void setUp() {
    mvc = MockMvcTester.from(webApplicationContext);
  }

  @Test
  void 유효한_이벤트는_저장되고_200을_받는다() {
    UUID orgId = insertOrganization("도입사 A");
    UUID customerId = insertCustomer(orgId, "acme");

    MvcTestResult result = post(orgId, body("tx-1", customerId.toString()));

    assertThat(result).hasStatusOk().bodyJson().extractingPath("$.duplicate").asBoolean().isFalse();
    assertThat(storedCount(orgId, "tx-1")).isEqualTo(1);
  }

  @Test
  void 요청의_timestamp가_occurred_at으로_저장된다() {
    UUID orgId = insertOrganization("도입사 A");
    UUID customerId = insertCustomer(orgId, "acme");

    post(orgId, body("tx-1", customerId.toString()));

    OffsetDateTime occurredAt =
        jdbc.queryForObject(
            "SELECT occurred_at FROM usage_event WHERE organization_id = ? AND transaction_id = 'tx-1'",
            OffsetDateTime.class,
            orgId);
    assertThat(occurredAt).isEqualTo(OffsetDateTime.parse(OCCURRED_AT));
  }

  @Test
  void 필수_필드가_하나라도_없으면_400이고_저장은_0건이다() {
    UUID orgId = insertOrganization("도입사 A");
    UUID customerId = insertCustomer(orgId, "acme");

    // 다섯 필드를 하나씩 빼 본다. V1 스키마의 NOT NULL 컬럼 목록과 같은 집합이다.
    String[] incompleteBodies = {
      """
      {"customer_id":"%s","event_type":"chat_completion","properties":{},"timestamp":"%s"}
      """
          .formatted(customerId, OCCURRED_AT),
      """
      {"transaction_id":"tx-1","event_type":"chat_completion","properties":{},"timestamp":"%s"}
      """
          .formatted(OCCURRED_AT),
      """
      {"transaction_id":"tx-1","customer_id":"%s","properties":{},"timestamp":"%s"}
      """
          .formatted(customerId, OCCURRED_AT),
      """
      {"transaction_id":"tx-1","customer_id":"%s","event_type":"chat_completion","timestamp":"%s"}
      """
          .formatted(customerId, OCCURRED_AT),
      """
      {"transaction_id":"tx-1","customer_id":"%s","event_type":"chat_completion","properties":{}}
      """
          .formatted(customerId)
    };

    for (String incomplete : incompleteBodies) {
      assertThat(post(orgId, incomplete)).hasStatus(400);
    }
    assertThat(totalCount(orgId)).isZero();
  }

  @Test
  void _400으로_거절된_transaction_id는_같은_키로_다시_보내면_저장된다() {
    UUID orgId = insertOrganization("도입사 A");
    UUID customerId = insertCustomer(orgId, "acme");

    String withoutEventType =
        """
        {"transaction_id":"tx-1","customer_id":"%s","properties":{},"timestamp":"%s"}
        """
            .formatted(customerId, OCCURRED_AT);
    assertThat(post(orgId, withoutEventType)).hasStatus(400);

    assertThat(post(orgId, body("tx-1", customerId.toString())))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.duplicate")
        .asBoolean()
        .isFalse();
    assertThat(storedCount(orgId, "tx-1")).isEqualTo(1);
  }

  @Test
  void properties가_비어_있거나_model과_token이_없어도_저장된다() {
    UUID orgId = insertOrganization("도입사 A");
    UUID customerId = insertCustomer(orgId, "acme");

    String emptyProperties =
        """
        {"transaction_id":"tx-1","customer_id":"%s","event_type":"chat_completion",
         "properties":{},"timestamp":"%s"}
        """
            .formatted(customerId, OCCURRED_AT);
    String unrelatedProperties =
        """
        {"transaction_id":"tx-2","customer_id":"%s","event_type":"chat_completion",
         "properties":{"whatever":"value"},"timestamp":"%s"}
        """
            .formatted(customerId, OCCURRED_AT);

    assertThat(post(orgId, emptyProperties)).hasStatusOk();
    assertThat(post(orgId, unrelatedProperties)).hasStatusOk();
    assertThat(totalCount(orgId)).isEqualTo(2);
  }

  @Test
  void 등록되지_않은_고객이면_400이고_저장은_0건이다() {
    UUID orgId = insertOrganization("도입사 A");

    MvcTestResult result = post(orgId, body("tx-1", UUID.randomUUID().toString()));

    assertThat(result)
        .hasStatus(400)
        .bodyJson()
        .extractingPath("$.code")
        .asString()
        .isEqualTo(ErrorCodes.UNKNOWN_CUSTOMER_REFERENCE);
    assertThat(totalCount(orgId)).isZero();
  }

  @Test
  void 다른_도입사_소속_고객이면_400이고_저장은_0건이다() {
    UUID orgId = insertOrganization("도입사 A");
    UUID otherOrgId = insertOrganization("도입사 B");
    UUID otherCustomerId = insertCustomer(otherOrgId, "다른 도입사의 고객");

    MvcTestResult result = post(orgId, body("tx-1", otherCustomerId.toString()));

    assertThat(result)
        .hasStatus(400)
        .bodyJson()
        .extractingPath("$.code")
        .asString()
        .isEqualTo(ErrorCodes.UNKNOWN_CUSTOMER_REFERENCE);
    assertThat(totalCount(orgId)).isZero();
  }

  @Test
  void received_at은_클라이언트가_무엇을_보내든_서버_시각이다() {
    UUID orgId = insertOrganization("도입사 A");
    UUID customerId = insertCustomer(orgId, "acme");

    // received_at은 DTO에 필드가 없어 매핑되지 않고, DB 트리거가 서버 시각으로 덮어쓴다.
    String withReceivedAt =
        """
        {"transaction_id":"tx-1","customer_id":"%s","event_type":"chat_completion",
         "properties":{},"timestamp":"%s","received_at":"2020-01-01T00:00:00Z"}
        """
            .formatted(customerId, OCCURRED_AT);
    assertThat(post(orgId, withReceivedAt)).hasStatusOk();

    OffsetDateTime receivedAt =
        jdbc.queryForObject(
            "SELECT received_at FROM usage_event WHERE organization_id = ? AND transaction_id = 'tx-1'",
            OffsetDateTime.class,
            orgId);
    assertThat(receivedAt).isAfter(OffsetDateTime.parse("2020-01-01T00:00:00Z"));
  }

  @Test
  void 같은_transaction_id를_두_번_보내면_저장은_1건이고_두_번째도_성공이다() {
    UUID orgId = insertOrganization("도입사 A");
    UUID customerId = insertCustomer(orgId, "acme");

    assertThat(post(orgId, body("tx-1", customerId.toString())))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.duplicate")
        .asBoolean()
        .isFalse();

    assertThat(post(orgId, body("tx-1", customerId.toString())))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.duplicate")
        .asBoolean()
        .isTrue();

    assertThat(storedCount(orgId, "tx-1")).isEqualTo(1);
  }

  @Test
  void 같은_키로_내용이_다른_요청이_와도_최초_저장본만_유지된다() {
    UUID orgId = insertOrganization("도입사 A");
    UUID customerId = insertCustomer(orgId, "acme");

    post(orgId, body("tx-1", customerId.toString()));

    String different =
        """
        {"transaction_id":"tx-1","customer_id":"%s","event_type":"embedding",
         "properties":{"token":999999},"timestamp":"2026-08-11T00:00:00+09:00"}
        """
            .formatted(customerId);
    assertThat(post(orgId, different))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.duplicate")
        .asBoolean()
        .isTrue();

    String eventType =
        jdbc.queryForObject(
            "SELECT event_type FROM usage_event WHERE organization_id = ? AND transaction_id = 'tx-1'",
            String.class,
            orgId);
    assertThat(eventType).isEqualTo("chat_completion");
    assertThat(storedCount(orgId, "tx-1")).isEqualTo(1);
  }

  @Test
  void 도입사_헤더가_없거나_형식이_틀리면_400이고_저장은_0건이다() {
    UUID orgId = insertOrganization("도입사 A");
    UUID customerId = insertCustomer(orgId, "acme");
    String payload = body("tx-1", customerId.toString());

    assertThat(
            mvc.post()
                .uri("/v1/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange())
        .hasStatus(400);

    assertThat(
            mvc.post()
                .uri("/v1/events")
                .header("X-Organization-Id", "not-a-uuid")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .exchange())
        .hasStatus(400);

    assertThat(totalCount(orgId)).isZero();
  }

  @Test
  void 도입사가_다르면_같은_transaction_id도_각각_저장된다() {
    UUID orgA = insertOrganization("도입사 A");
    UUID orgB = insertOrganization("도입사 B");
    UUID customerA = insertCustomer(orgA, "acme");
    UUID customerB = insertCustomer(orgB, "acme");

    assertThat(post(orgA, body("tx-1", customerA.toString()))).hasStatusOk();
    assertThat(post(orgB, body("tx-1", customerB.toString()))).hasStatusOk();

    assertThat(storedCount(orgA, "tx-1")).isEqualTo(1);
    assertThat(storedCount(orgB, "tx-1")).isEqualTo(1);
  }

  @Test
  void 형식_오류와_고객_매핑_실패는_둘_다_problem_json이고_서로_구별된다() {
    UUID orgId = insertOrganization("도입사 A");

    // 형식 오류는 Boot의 ProblemDetailsExceptionHandler가, 고객 매핑 실패는
    // EventExceptionHandler가 맡는다. 후자가 ResponseEntityExceptionHandler를
    // 상속하면 전자의 자동 설정이 물러나므로, 둘이 공존하는지 확인한다.
    String withoutTransactionId =
        """
        {"customer_id":"%s","event_type":"chat_completion","properties":{},"timestamp":"%s"}
        """
            .formatted(UUID.randomUUID(), OCCURRED_AT);

    assertThat(post(orgId, withoutTransactionId))
        .hasStatus(400)
        .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .bodyJson()
        .extractingPath("$.code")
        .asString()
        .isEqualTo(ErrorCodes.VALIDATION_ERROR);

    assertThat(post(orgId, body("tx-1", UUID.randomUUID().toString())))
        .hasStatus(400)
        .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .bodyJson()
        .extractingPath("$.code")
        .asString()
        .isEqualTo(ErrorCodes.UNKNOWN_CUSTOMER_REFERENCE);
  }

  @Test
  void 형식_검증_실패는_어느_필드가_왜_걸렸는지_알려준다() {
    UUID orgId = insertOrganization("도입사 A");
    UUID customerId = insertCustomer(orgId, "acme");

    String withoutEventType =
        """
        {"transaction_id":"tx-1","customer_id":"%s","properties":{},"timestamp":"%s"}
        """
            .formatted(customerId, OCCURRED_AT);

    assertThat(post(orgId, withoutEventType))
        .hasStatus(400)
        .bodyJson()
        .extractingPath("$.errors[0].field")
        .asString()
        .isEqualTo("eventType");
  }

  @Test
  void 소수는_자릿수가_잘리지_않고_저장된다() {
    UUID orgId = insertOrganization("도입사 A");
    UUID customerId = insertCustomer(orgId, "acme");

    // Double로 바인딩하면 0.12345678901234568로 잘린다. usage_event는 append-only라 되돌릴 수 없고
    // 이 값이 청구 근거가 된다.
    String preciseDecimal =
        """
        {"transaction_id":"tx-1","customer_id":"%s","event_type":"chat_completion",
         "properties":{"cost":0.1234567890123456789,"token":12345678901234567890123},
         "timestamp":"%s"}
        """
            .formatted(customerId, OCCURRED_AT);
    assertThat(post(orgId, preciseDecimal)).hasStatusOk();

    String stored =
        jdbc.queryForObject(
            "SELECT properties->>'cost' FROM usage_event WHERE organization_id = ? AND transaction_id = 'tx-1'",
            String.class,
            orgId);
    assertThat(stored).isEqualTo("0.1234567890123456789");

    String storedInteger =
        jdbc.queryForObject(
            "SELECT properties->>'token' FROM usage_event WHERE organization_id = ? AND transaction_id = 'tx-1'",
            String.class,
            orgId);
    assertThat(storedInteger).isEqualTo("12345678901234567890123");
  }

  @Test
  void DB가_담을_수_없는_값은_500이_아니라_400이다() {
    UUID orgId = insertOrganization("도입사 A");
    UUID customerId = insertCustomer(orgId, "acme");

    // NUL 문자는 유효한 JSON이지만 Postgres jsonb가 거부한다. 500으로 나가면 5xx가 재시도 신호라
    // 수집 클라이언트가 저장되지도 않을 이벤트를 영원히 재전송한다.
    String withNulCharacter =
        """
        {"transaction_id":"tx-1","customer_id":"%s","event_type":"chat_completion",
         "properties":{"prompt":"a\\u0000b"},"timestamp":"%s"}
        """
            .formatted(customerId, OCCURRED_AT);

    assertThat(post(orgId, withNulCharacter))
        .hasStatus(400)
        .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .bodyJson()
        .extractingPath("$.code")
        .asString()
        .isEqualTo(ErrorCodes.INVALID_EVENT);

    // 여기서 저장 건수를 세지 않는다. 제약 위반이 나면 PostgreSQL이 트랜잭션을 abort 상태로 만들어
    // (SQLSTATE 25P02) 이 테스트의 @Transactional 안에서는 이후 어떤 조회도 실패한다. 실제로 한 번
    // 겪었다. INSERT 문 자체가 실패했으니 저장은 0건이고, 운영에서는 ingest()가 트랜잭션 밖에서 돌아
    // 다음 요청에 영향이 없다. 이 제약이 EventIngestService의 DuplicateKeyException catch에
    // 달아 둔 경고와 같은 사실이다.
  }

  @Test
  void 도입사를_잘못_보내도_고객만_지목하지_않는다() {
    UUID orgId = insertOrganization("도입사 A");
    UUID customerId = insertCustomer(orgId, "acme");
    UUID wrongOrgId = UUID.randomUUID();

    // customer_id는 멀쩡한데 X-Organization-Id가 틀린 경우다. 고객만 지목하면 도입사는 고객 등록을
    // 의심하며 엉뚱한 곳을 디버깅한다.
    assertThat(post(wrongOrgId, body("tx-1", customerId.toString())))
        .hasStatus(400)
        .bodyJson()
        .extractingPath("$.detail")
        .asString()
        .contains(wrongOrgId.toString())
        .contains("X-Organization-Id");
  }

  @Test
  void transaction_id는_255자까지_받고_256자는_거절한다() {
    UUID orgId = insertOrganization("도입사 A");
    UUID customerId = insertCustomer(orgId, "acme");

    assertThat(post(orgId, body("x".repeat(255), customerId.toString()))).hasStatusOk();

    assertThat(post(orgId, body("x".repeat(256), customerId.toString())))
        .hasStatus(400)
        .bodyJson()
        .extractingPath("$.errors[0].field")
        .asString()
        .isEqualTo("transactionId");
  }

  private MvcTestResult post(UUID organizationId, String jsonBody) {
    return mvc.post()
        .uri("/v1/events")
        .header("X-Organization-Id", organizationId.toString())
        .contentType(MediaType.APPLICATION_JSON)
        .content(jsonBody)
        .exchange();
  }

  private String body(String transactionId, String customerId) {
    return """
        {"transaction_id":"%s","customer_id":"%s","event_type":"chat_completion",
         "properties":{"model":"gpt-4o-mini","token":1200},"timestamp":"%s"}
        """
        .formatted(transactionId, customerId, OCCURRED_AT);
  }

  private UUID insertOrganization(String name) {
    return jdbc.queryForObject(
        "INSERT INTO organization (name) VALUES (?) RETURNING id", UUID.class, name);
  }

  private UUID insertCustomer(UUID organizationId, String name) {
    return jdbc.queryForObject(
        "INSERT INTO customer (organization_id, name) VALUES (?, ?) RETURNING id",
        UUID.class,
        organizationId,
        name);
  }

  private int storedCount(UUID organizationId, String transactionId) {
    Integer count =
        jdbc.queryForObject(
            "SELECT count(*) FROM usage_event WHERE organization_id = ? AND transaction_id = ?",
            Integer.class,
            organizationId,
            transactionId);
    return count == null ? 0 : count;
  }

  private int totalCount(UUID organizationId) {
    Integer count =
        jdbc.queryForObject(
            "SELECT count(*) FROM usage_event WHERE organization_id = ?",
            Integer.class,
            organizationId);
    return count == null ? 0 : count;
  }
}
