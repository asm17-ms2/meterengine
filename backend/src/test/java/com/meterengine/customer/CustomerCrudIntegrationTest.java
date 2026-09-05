package com.meterengine.customer;

import static org.assertj.core.api.Assertions.assertThat;

import com.meterengine.ErrorCodes;
import com.meterengine.TestcontainersConfiguration;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
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
import tools.jackson.databind.json.JsonMapper;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class CustomerCrudIntegrationTest {

  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private JsonMapper jsonMapper;

  private MockMvcTester mvc;

  @BeforeEach
  void setUp() {
    mvc = MockMvcTester.from(webApplicationContext);
  }

  // --- 등록 ---

  @Test
  void 등록하면_201과_발급된_id와_등록시각이_오고_목록에_보인다() {
    UUID orgId = insertOrganization();
    OffsetDateTime beforePost = dbNow();

    MvcTestResult created =
        post(
            orgId,
            """
        {"name":"아크메 주식회사"}
        """);

    assertThat(created).hasStatus(201).bodyJson().extractingPath("$.name").isEqualTo("아크메 주식회사");
    assertThat(created).bodyJson().extractingPath("$.customer_id").asString().isNotEmpty();

    OffsetDateTime createdAt =
        OffsetDateTime.parse(jsonMapper.readTree(bodyText(created)).get("created_at").asString());
    assertThat(createdAt).isAfterOrEqualTo(beforePost);

    assertThat(list(orgId))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.customers[*].name")
        .asArray()
        .containsExactly("아크메 주식회사");
  }

  @Test
  void 발급된_id로_이벤트를_수집할_수_있다() {
    UUID orgId = insertOrganization();
    UUID customerId = createCustomer(orgId, "아크메");

    assertThat(
            mvc.post()
                .uri("/v1/events")
                .header("X-Organization-Id", orgId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"transaction_id":"tx-1","customer_id":"%s","type":"chat_completion",
                     "properties":{"token":1200},"occurred_at":"2026-08-10T12:00:00+09:00"}
                    """
                        .formatted(customerId))
                .exchange())
        .hasStatusOk();
  }

  @Test
  void 이름이_비었거나_공백뿐이면_400이고_저장은_0건이다() {
    UUID orgId = insertOrganization();

    for (String invalid : new String[] {"{\"name\":\"\"}", "{\"name\":\"   \"}", "{}"}) {
      assertThat(post(orgId, invalid))
          .hasStatus(400)
          .bodyJson()
          .extractingPath("$.code")
          .asString()
          .isEqualTo(ErrorCodes.VALIDATION_ERROR);
    }
    assertThat(customerCount(orgId)).isZero();
  }

  @Test
  void 이름은_255자까지_받고_256자는_거절한다() {
    UUID orgId = insertOrganization();

    assertThat(
            post(
                orgId,
                """
        {"name":"%s"}
        """
                    .formatted("가".repeat(255))))
        .hasStatus(201);

    assertThat(
            post(
                orgId,
                """
        {"name":"%s"}
        """
                    .formatted("가".repeat(256))))
        .hasStatus(400)
        .bodyJson()
        .extractingPath("$.errors[0].field")
        .asString()
        .isEqualTo("name");
  }

  @Test
  void 등록되지_않은_도입사로_등록하면_400이다() {
    assertThat(
            post(
                UUID.randomUUID(),
                """
        {"name":"아크메"}
        """))
        .hasStatus(400)
        .bodyJson()
        .extractingPath("$.code")
        .asString()
        .isEqualTo(ErrorCodes.UNKNOWN_ORGANIZATION);
  }

  @Test
  void 같은_이름을_두_번_등록하면_둘_다_남는다() {
    UUID orgId = insertOrganization();

    UUID first = createCustomer(orgId, "아크메");
    UUID second = createCustomer(orgId, "아크메");

    assertThat(first).isNotEqualTo(second);
    assertThat(customerCount(orgId)).isEqualTo(2);
  }

  // --- 목록 ---

  @Test
  void 목록은_이름_오름차순이고_다른_도입사_고객은_섞이지_않는다() {
    UUID orgId = insertOrganization();
    createCustomer(orgId, "히읗");
    createCustomer(orgId, "기역");
    createCustomer(orgId, "니은");

    UUID otherOrgId = insertOrganization();
    createCustomer(otherOrgId, "남의 고객");

    assertThat(list(orgId))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.customers[*].name")
        .asArray()
        .containsExactly("기역", "니은", "히읗");
  }

  @Test
  void 목록은_한국어_사전순이다() {
    UUID orgId = insertOrganization();
    createCustomer(orgId, "힘찬");
    createCustomer(orgId, "Beta Corp");
    createCustomer(orgId, "가나다");
    createCustomer(orgId, "acme corp");
    createCustomer(orgId, "나비");

    assertThat(list(orgId))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.customers[*].name")
        .asArray()
        .containsExactly("가나다", "나비", "힘찬", "acme corp", "Beta Corp");
  }

  @Test
  void 고객이_없으면_빈_배열이다() {
    assertThat(list(insertOrganization()))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.customers")
        .asArray()
        .isEmpty();
  }

  // --- 수정 ---

  @Test
  void 이름을_고치면_200이고_목록에_반영된다() {
    UUID orgId = insertOrganization();
    MvcTestResult created =
        post(
            orgId,
            """
        {"name":"옛 이름"}
        """);
    UUID customerId =
        UUID.fromString(jsonMapper.readTree(bodyText(created)).get("customer_id").asString());
    String createdAt = jsonMapper.readTree(bodyText(created)).get("created_at").asString();

    MvcTestResult renamed =
        put(
            orgId,
            customerId,
            """
        {"name":"새 이름"}
        """);
    assertThat(renamed).hasStatusOk().bodyJson().extractingPath("$.name").isEqualTo("새 이름");

    assertThat(renamed).bodyJson().extractingPath("$.created_at").asString().isEqualTo(createdAt);

    assertThat(list(orgId))
        .bodyJson()
        .extractingPath("$.customers[*].name")
        .asArray()
        .containsExactly("새 이름");
    assertThat(list(orgId))
        .bodyJson()
        .extractingPath("$.customers[0].created_at")
        .asString()
        .isEqualTo(createdAt);
  }

  @Test
  void 다른_도입사_고객은_고칠_수_없다() {
    UUID orgId = insertOrganization();
    UUID otherOrgId = insertOrganization();
    UUID otherCustomerId = createCustomer(otherOrgId, "남의 고객");

    assertThat(
            put(
                orgId,
                otherCustomerId,
                """
        {"name":"가로챈 이름"}
        """))
        .hasStatus(404)
        .bodyJson()
        .extractingPath("$.code")
        .asString()
        .isEqualTo(ErrorCodes.CUSTOMER_NOT_FOUND);

    assertThat(nameOf(otherCustomerId)).isEqualTo("남의 고객");
  }

  @Test
  void 없는_고객을_고치면_404다() {
    assertThat(
            put(
                insertOrganization(),
                UUID.randomUUID(),
                """
        {"name":"아무개"}
        """))
        .hasStatus(404);
  }

  @Test
  void 경로의_id가_UUID가_아니면_500이_아니라_400이다() {
    UUID orgId = insertOrganization();

    assertThat(
            mvc.delete()
                .uri("/v1/customers/not-a-uuid")
                .header("X-Organization-Id", orgId.toString())
                .exchange())
        .hasStatus(400)
        .bodyJson()
        .extractingPath("$.code")
        .asString()
        .isEqualTo(ErrorCodes.VALIDATION_ERROR);
  }

  // --- 삭제 ---

  @Test
  void 이벤트가_없는_고객은_삭제되고_목록에서_빠진다() {
    UUID orgId = insertOrganization();
    UUID customerId = createCustomer(orgId, "지울 고객");

    assertThat(delete(orgId, customerId)).hasStatus(204);

    assertThat(list(orgId)).bodyJson().extractingPath("$.customers").asArray().isEmpty();
    assertThat(customerCount(orgId)).isZero();
  }

  @Test
  void 이벤트가_있는_고객을_지우면_409이고_고객은_그대로다() {
    UUID orgId = insertOrganization();
    UUID customerId = createCustomer(orgId, "이벤트 있는 고객");
    insertEvent(orgId, customerId);

    assertThat(delete(orgId, customerId))
        .hasStatus(409)
        .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .bodyJson()
        .extractingPath("$.code")
        .asString()
        .isEqualTo(ErrorCodes.CUSTOMER_HAS_EVENTS);

    assertThat(customerCount(orgId)).isEqualTo(1);
    assertThat(list(orgId)).bodyJson().extractingPath("$.customers").asArray().hasSize(1);
  }

  @Test
  void 같은_고객을_두_번_지우면_두_번째는_404다() {
    UUID orgId = insertOrganization();
    UUID customerId = createCustomer(orgId, "지울 고객");

    assertThat(delete(orgId, customerId)).hasStatus(204);

    assertThat(delete(orgId, customerId))
        .hasStatus(404)
        .bodyJson()
        .extractingPath("$.code")
        .asString()
        .isEqualTo(ErrorCodes.CUSTOMER_NOT_FOUND);
  }

  @Test
  void 지운_고객은_고칠_수도_없다() {
    UUID orgId = insertOrganization();
    UUID customerId = createCustomer(orgId, "지울 고객");
    delete(orgId, customerId);

    assertThat(
            put(
                orgId,
                customerId,
                """
        {"name":"되살리기 시도"}
        """))
        .hasStatus(404);
  }

  @Test
  void 다른_도입사_고객은_지울_수_없다() {
    UUID orgId = insertOrganization();
    UUID otherOrgId = insertOrganization();
    UUID otherCustomerId = createCustomer(otherOrgId, "남의 고객");

    assertThat(delete(orgId, otherCustomerId)).hasStatus(404);
    assertThat(customerCount(otherOrgId)).isEqualTo(1);
  }

  // --- 공통 ---

  @Test
  void 도입사_헤더가_없으면_네_메서드_모두_400이다() {
    assertThat(mvc.get().uri("/v1/customers").exchange()).hasStatus(400);
    assertThat(
            mvc.post()
                .uri("/v1/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"아크메\"}")
                .exchange())
        .hasStatus(400);
    assertThat(
            mvc.put()
                .uri("/v1/customers/" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"아크메\"}")
                .exchange())
        .hasStatus(400);
    assertThat(mvc.delete().uri("/v1/customers/" + UUID.randomUUID()).exchange()).hasStatus(400);
  }

  // --- 헬퍼 ---

  private MvcTestResult list(UUID organizationId) {
    return mvc.get()
        .uri("/v1/customers")
        .header("X-Organization-Id", organizationId.toString())
        .exchange();
  }

  private MvcTestResult post(UUID organizationId, String jsonBody) {
    return mvc.post()
        .uri("/v1/customers")
        .header("X-Organization-Id", organizationId.toString())
        .contentType(MediaType.APPLICATION_JSON)
        .content(jsonBody)
        .exchange();
  }

  private MvcTestResult put(UUID organizationId, UUID customerId, String jsonBody) {
    return mvc.put()
        .uri("/v1/customers/" + customerId)
        .header("X-Organization-Id", organizationId.toString())
        .contentType(MediaType.APPLICATION_JSON)
        .content(jsonBody)
        .exchange();
  }

  private MvcTestResult delete(UUID organizationId, UUID customerId) {
    return mvc.delete()
        .uri("/v1/customers/" + customerId)
        .header("X-Organization-Id", organizationId.toString())
        .exchange();
  }

  private OffsetDateTime dbNow() {
    return jdbc.queryForObject("SELECT clock_timestamp()", OffsetDateTime.class);
  }

  private UUID createCustomer(UUID organizationId, String name) {
    MvcTestResult result =
        post(
            organizationId,
            """
        {"name":"%s"}
        """
                .formatted(name));
    return UUID.fromString(jsonMapper.readTree(bodyText(result)).get("customer_id").asString());
  }

  private String bodyText(MvcTestResult result) {
    try {
      return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    } catch (UnsupportedEncodingException e) {
      throw new IllegalStateException("응답 본문을 읽지 못했다", e);
    }
  }

  private UUID insertOrganization() {
    return jdbc.queryForObject(
        "INSERT INTO organization (name) VALUES ('테스트 도입사') RETURNING id", UUID.class);
  }

  private void insertEvent(UUID organizationId, UUID customerId) {
    jdbc.update(
        """
        INSERT INTO usage_event
          (organization_id, transaction_id, customer_id, event_type, properties, occurred_at)
        VALUES (?, 'tx-1', ?, 'chat_completion', '{"token": 1200}', now())
        """,
        organizationId,
        customerId);
  }

  private Integer customerCount(UUID organizationId) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM customer WHERE organization_id = ?", Integer.class, organizationId);
  }

  private String nameOf(UUID customerId) {
    return jdbc.queryForObject("SELECT name FROM customer WHERE id = ?", String.class, customerId);
  }
}
