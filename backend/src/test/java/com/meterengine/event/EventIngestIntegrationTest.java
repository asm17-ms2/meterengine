package com.meterengine.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.meterengine.TestcontainersConfiguration;
import com.meterengine.global.error.ErrorCode;
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
            "SELECT occurred_at FROM event WHERE organization_id = ? AND transaction_id = 'tx-1'",
            OffsetDateTime.class,
            orgId);
    assertThat(occurredAt).isEqualTo(OffsetDateTime.parse(OCCURRED_AT));
  }

  @Test
  void 필수_필드가_하나라도_없으면_400이고_저장은_0건이다() {
    UUID orgId = insertOrganization("도입사 A");
    UUID customerId = insertCustomer(orgId, "acme");

    String[] incompleteBodies = {
      """
      {"customer_id":"%s","type":"chat_completion","properties":{},"timestamp":"%s"}
      """
          .formatted(customerId, OCCURRED_AT),
      """
      {"transaction_id":"tx-1","type":"chat_completion","properties":{},"timestamp":"%s"}
      """
          .formatted(OCCURRED_AT),
      """
      {"transaction_id":"tx-1","customer_id":"%s","properties":{},"timestamp":"%s"}
      """
          .formatted(customerId, OCCURRED_AT),
      """
      {"transaction_id":"tx-1","customer_id":"%s","type":"chat_completion","timestamp":"%s"}
      """
          .formatted(customerId, OCCURRED_AT),
      """
      {"transaction_id":"tx-1","customer_id":"%s","type":"chat_completion","properties":{}}
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
        {"transaction_id":"tx-1","customer_id":"%s","type":"chat_completion",
         "properties":{},"timestamp":"%s"}
        """
            .formatted(customerId, OCCURRED_AT);
    String unrelatedProperties =
        """
        {"transaction_id":"tx-2","customer_id":"%s","type":"chat_completion",
         "properties":{"whatever":"value"},"timestamp":"%s"}
        """
            .formatted(customerId, OCCURRED_AT);

    assertThat(post(orgId, emptyProperties)).hasStatusOk();
    assertThat(post(orgId, unrelatedProperties)).hasStatusOk();
    assertThat(totalCount(orgId)).isEqualTo(2);
  }

  @Test
  void 등록되지_않은_고객이면_404이고_저장은_0건이다() {
    UUID orgId = insertOrganization("도입사 A");

    MvcTestResult result = post(orgId, body("tx-1", UUID.randomUUID().toString()));

    assertThat(result)
        .hasStatus(404)
        .bodyJson()
        .extractingPath("$.code")
        .asString()
        .isEqualTo(ErrorCode.CUSTOMER_NOT_FOUND.getCode());
    assertThat(totalCount(orgId)).isZero();
  }

  @Test
  void 다른_도입사_소속_고객이면_404이고_저장은_0건이다() {
    UUID orgId = insertOrganization("도입사 A");
    UUID otherOrgId = insertOrganization("도입사 B");
    UUID otherCustomerId = insertCustomer(otherOrgId, "다른 도입사의 고객");

    MvcTestResult result = post(orgId, body("tx-1", otherCustomerId.toString()));

    assertThat(result)
        .hasStatus(404)
        .bodyJson()
        .extractingPath("$.code")
        .asString()
        .isEqualTo(ErrorCode.CUSTOMER_NOT_FOUND.getCode());
    assertThat(totalCount(orgId)).isZero();
  }

  @Test
  void received_at은_클라이언트가_무엇을_보내든_서버_시각이다() {
    UUID orgId = insertOrganization("도입사 A");
    UUID customerId = insertCustomer(orgId, "acme");

    String withReceivedAt =
        """
        {"transaction_id":"tx-1","customer_id":"%s","type":"chat_completion",
         "properties":{},"timestamp":"%s","received_at":"2020-01-01T00:00:00Z"}
        """
            .formatted(customerId, OCCURRED_AT);
    assertThat(post(orgId, withReceivedAt)).hasStatusOk();

    OffsetDateTime receivedAt =
        jdbc.queryForObject(
            "SELECT received_at FROM event WHERE organization_id = ? AND transaction_id = 'tx-1'",
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
        {"transaction_id":"tx-1","customer_id":"%s","type":"embedding",
         "properties":{"token":999999},"timestamp":"2026-08-11T00:00:00+09:00"}
        """
            .formatted(customerId);
    assertThat(post(orgId, different))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.duplicate")
        .asBoolean()
        .isTrue();

    String type =
        jdbc.queryForObject(
            "SELECT type FROM event WHERE organization_id = ? AND transaction_id = 'tx-1'",
            String.class,
            orgId);
    assertThat(type).isEqualTo("chat_completion");
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
  void 형식_오류와_고객_매핑_실패는_code로_서로_구별된다() {
    UUID orgId = insertOrganization("도입사 A");

    String withoutTransactionId =
        """
        {"customer_id":"%s","type":"chat_completion","properties":{},"timestamp":"%s"}
        """
            .formatted(UUID.randomUUID(), OCCURRED_AT);

    assertThat(post(orgId, withoutTransactionId))
        .hasStatus(400)
        .hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
        .bodyJson()
        .extractingPath("$.code")
        .asString()
        .isEqualTo(ErrorCode.VALIDATION_ERROR.getCode());

    assertThat(post(orgId, body("tx-1", UUID.randomUUID().toString())))
        .hasStatus(404)
        .hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
        .bodyJson()
        .extractingPath("$.code")
        .asString()
        .isEqualTo(ErrorCode.CUSTOMER_NOT_FOUND.getCode());
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
        .isEqualTo("type");
  }

  @Test
  void 소수는_자릿수가_잘리지_않고_저장된다() {
    UUID orgId = insertOrganization("도입사 A");
    UUID customerId = insertCustomer(orgId, "acme");

    String preciseDecimal =
        """
        {"transaction_id":"tx-1","customer_id":"%s","type":"chat_completion",
         "properties":{"cost":0.1234567890123456789,"token":12345678901234567890123},
         "timestamp":"%s"}
        """
            .formatted(customerId, OCCURRED_AT);
    assertThat(post(orgId, preciseDecimal)).hasStatusOk();

    String stored =
        jdbc.queryForObject(
            "SELECT properties->>'cost' FROM event WHERE organization_id = ? AND transaction_id = 'tx-1'",
            String.class,
            orgId);
    assertThat(stored).isEqualTo("0.1234567890123456789");

    String storedInteger =
        jdbc.queryForObject(
            "SELECT properties->>'token' FROM event WHERE organization_id = ? AND transaction_id = 'tx-1'",
            String.class,
            orgId);
    assertThat(storedInteger).isEqualTo("12345678901234567890123");
  }

  @Test
  void DB가_담을_수_없는_값은_500이_아니라_400이다() {
    UUID orgId = insertOrganization("도입사 A");
    UUID customerId = insertCustomer(orgId, "acme");

    String withNulCharacter =
        """
        {"transaction_id":"tx-1","customer_id":"%s","type":"chat_completion",
         "properties":{"prompt":"a\\u0000b"},"timestamp":"%s"}
        """
            .formatted(customerId, OCCURRED_AT);

    MvcTestResult result = post(orgId, withNulCharacter);

    assertThat(result)
        .hasStatus(400)
        .hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
        .bodyJson()
        .extractingPath("$.code")
        .asString()
        .isEqualTo(ErrorCode.INVALID_EVENT.getCode());
    assertThat(result).bodyJson().doesNotHavePath("$.errors");
  }

  @Test
  void 도입사를_잘못_보내면_404이고_보낸_값은_본문에_없다() {
    UUID orgId = insertOrganization("도입사 A");
    UUID customerId = insertCustomer(orgId, "acme");
    UUID wrongOrgId = UUID.randomUUID();

    MvcTestResult result = post(wrongOrgId, body("tx-1", customerId.toString()));

    assertThat(result)
        .hasStatus(404)
        .bodyJson()
        .extractingPath("$.code")
        .asString()
        .isEqualTo(ErrorCode.CUSTOMER_NOT_FOUND.getCode());
    assertThat(result).bodyText().doesNotContain(wrongOrgId.toString());
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
        .isEqualTo("transaction_id");
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
        {"transaction_id":"%s","customer_id":"%s","type":"chat_completion",
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
            "SELECT count(*) FROM event WHERE organization_id = ? AND transaction_id = ?",
            Integer.class,
            organizationId,
            transactionId);
    return count == null ? 0 : count;
  }

  private int totalCount(UUID organizationId) {
    Integer count =
        jdbc.queryForObject(
            "SELECT count(*) FROM event WHERE organization_id = ?", Integer.class, organizationId);
    return count == null ? 0 : count;
  }
}
