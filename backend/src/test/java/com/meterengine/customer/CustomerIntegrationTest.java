package com.meterengine.customer;

import static org.assertj.core.api.Assertions.assertThat;

import com.meterengine.TestcontainersConfiguration;
import com.meterengine.global.error.ErrorCode;
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
class CustomerIntegrationTest {

  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private JsonMapper jsonMapper;

  private MockMvcTester mvc;

  @BeforeEach
  void setUp() {
    mvc = MockMvcTester.from(webApplicationContext);
  }

  // --- 등록 ---

  @Test
  void 등록하면_201과_발급된_id와_등록시각이_오고_목록에_보인다() {
    UUID organizationId = insertOrganization();
    OffsetDateTime beforePost = dbNow();

    MvcTestResult created =
        post(
            organizationId,
            """
        {"name":"아크메 주식회사"}
        """);

    assertThat(created).hasStatus(201).bodyJson().extractingPath("$.name").isEqualTo("아크메 주식회사");
    assertThat(created).bodyJson().extractingPath("$.id").asString().isNotEmpty();

    OffsetDateTime createdAt =
        OffsetDateTime.parse(jsonMapper.readTree(bodyText(created)).get("created_at").asString());
    assertThat(createdAt).isAfterOrEqualTo(beforePost);

    assertThat(list(organizationId))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.customers[*].name")
        .asArray()
        .containsExactly("아크메 주식회사");
  }

  @Test
  void 발급된_id로_이벤트를_수집할_수_있다() {
    UUID organizationId = insertOrganization();
    UUID customerId = createCustomer(organizationId, "아크메");

    assertThat(
            mvc.post()
                .uri("/v1/events")
                .header("X-Organization-Id", organizationId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"transaction_id":"tx-1","customer_id":"%s","type":"chat_completion",
                     "properties":{"token":1200},"timestamp":"2026-08-10T12:00:00+09:00"}
                    """
                        .formatted(customerId))
                .exchange())
        .hasStatusOk();
  }

  @Test
  void 이름이_비었거나_공백뿐이면_400이고_저장은_0건이다() {
    UUID organizationId = insertOrganization();

    for (String invalid : new String[] {"{\"name\":\"\"}", "{\"name\":\"   \"}", "{}"}) {
      assertThat(post(organizationId, invalid))
          .hasStatus(400)
          .bodyJson()
          .extractingPath("$.code")
          .asString()
          .isEqualTo(ErrorCode.VALIDATION_ERROR.getCode());
    }
    assertThat(customerCount(organizationId)).isZero();
  }

  @Test
  void 이름은_255자까지_받고_256자는_거절한다() {
    UUID organizationId = insertOrganization();

    assertThat(
            post(
                organizationId,
                """
        {"name":"%s"}
        """
                    .formatted("가".repeat(255))))
        .hasStatus(201);

    assertThat(
            post(
                organizationId,
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
        .isEqualTo(ErrorCode.UNKNOWN_ORGANIZATION.getCode());
  }

  @Test
  void 같은_이름을_두_번_등록하면_둘_다_남는다() {
    UUID organizationId = insertOrganization();

    UUID first = createCustomer(organizationId, "아크메");
    UUID second = createCustomer(organizationId, "아크메");

    assertThat(first).isNotEqualTo(second);
    assertThat(customerCount(organizationId)).isEqualTo(2);
  }

  // --- 목록 ---

  @Test
  void 목록은_이름_오름차순이고_다른_도입사_고객은_섞이지_않는다() {
    UUID organizationId = insertOrganization();
    createCustomer(organizationId, "히읗");
    createCustomer(organizationId, "기역");
    createCustomer(organizationId, "니은");

    UUID otherOrganizationId = insertOrganization();
    createCustomer(otherOrganizationId, "남의 고객");

    assertThat(list(organizationId))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.customers[*].name")
        .asArray()
        .containsExactly("기역", "니은", "히읗");
  }

  @Test
  void 목록은_한국어_사전순이다() {
    UUID organizationId = insertOrganization();
    createCustomer(organizationId, "힘찬");
    createCustomer(organizationId, "Beta Corp");
    createCustomer(organizationId, "가나다");
    createCustomer(organizationId, "acme corp");
    createCustomer(organizationId, "나비");

    assertThat(list(organizationId))
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
    UUID organizationId = insertOrganization();
    MvcTestResult created =
        post(
            organizationId,
            """
        {"name":"옛 이름"}
        """);
    UUID customerId = UUID.fromString(jsonMapper.readTree(bodyText(created)).get("id").asString());
    String createdAt = jsonMapper.readTree(bodyText(created)).get("created_at").asString();

    MvcTestResult renamed =
        put(
            organizationId,
            customerId,
            """
        {"name":"새 이름"}
        """);
    assertThat(renamed).hasStatusOk().bodyJson().extractingPath("$.name").isEqualTo("새 이름");

    assertThat(renamed).bodyJson().extractingPath("$.created_at").asString().isEqualTo(createdAt);

    assertThat(list(organizationId))
        .bodyJson()
        .extractingPath("$.customers[*].name")
        .asArray()
        .containsExactly("새 이름");
    assertThat(list(organizationId))
        .bodyJson()
        .extractingPath("$.customers[0].created_at")
        .asString()
        .isEqualTo(createdAt);
  }

  @Test
  void 수정도_이름을_255자까지_받는다() {
    UUID organizationId = insertOrganization();
    UUID customerId = createCustomer(organizationId, "옛 이름");
    String longest = "가".repeat(255);

    assertThat(put(organizationId, customerId, "{\"name\":\"%s\"}".formatted(longest)))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.name")
        .isEqualTo(longest);
    assertThat(list(organizationId))
        .bodyJson()
        .extractingPath("$.customers[*].name")
        .asArray()
        .containsExactly(longest);
  }

  @Test
  void 수정도_이름이_비었거나_256자를_넘으면_400이고_이름은_그대로다() {
    UUID organizationId = insertOrganization();
    UUID customerId = createCustomer(organizationId, "옛 이름");

    for (String invalid :
        new String[] {
          "{\"name\":\"\"}", "{\"name\":\"   \"}", "{\"name\":\"%s\"}".formatted("가".repeat(256))
        }) {
      assertThat(put(organizationId, customerId, invalid))
          .hasStatus(400)
          .bodyJson()
          .extractingPath("$.code")
          .asString()
          .isEqualTo(ErrorCode.VALIDATION_ERROR.getCode());
    }
    assertThat(nameOf(customerId)).isEqualTo("옛 이름");
  }

  @Test
  void 다른_도입사_고객은_고칠_수_없다() {
    UUID organizationId = insertOrganization();
    UUID otherOrganizationId = insertOrganization();
    UUID otherCustomerId = createCustomer(otherOrganizationId, "남의 고객");

    assertThat(
            put(
                organizationId,
                otherCustomerId,
                """
        {"name":"가로챈 이름"}
        """))
        .hasStatus(404)
        .bodyJson()
        .extractingPath("$.code")
        .asString()
        .isEqualTo(ErrorCode.CUSTOMER_NOT_FOUND.getCode());

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
    UUID organizationId = insertOrganization();

    assertThat(
            mvc.delete()
                .uri("/v1/customers/not-a-uuid")
                .header("X-Organization-Id", organizationId.toString())
                .exchange())
        .hasStatus(400)
        .bodyJson()
        .extractingPath("$.code")
        .asString()
        .isEqualTo(ErrorCode.VALIDATION_ERROR.getCode());
  }

  // --- 삭제 ---

  @Test
  void 이벤트가_없는_고객은_삭제되고_목록에서_빠진다() {
    UUID organizationId = insertOrganization();
    UUID customerId = createCustomer(organizationId, "지울 고객");

    assertThat(delete(organizationId, customerId)).hasStatus(204);

    assertThat(list(organizationId)).bodyJson().extractingPath("$.customers").asArray().isEmpty();
    assertThat(customerCount(organizationId)).isZero();
  }

  @Test
  void 이벤트가_있는_고객을_지우면_409이고_고객은_그대로다() {
    UUID organizationId = insertOrganization();
    UUID customerId = createCustomer(organizationId, "이벤트 있는 고객");
    insertEvent(organizationId, customerId);

    assertThat(delete(organizationId, customerId))
        .hasStatus(409)
        .hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
        .bodyJson()
        .extractingPath("$.code")
        .asString()
        .isEqualTo(ErrorCode.CUSTOMER_HAS_EVENTS.getCode());

    assertThat(customerCount(organizationId)).isEqualTo(1);
    assertThat(list(organizationId)).bodyJson().extractingPath("$.customers").asArray().hasSize(1);
  }

  @Test
  void 같은_고객을_두_번_지우면_두_번째는_404다() {
    UUID organizationId = insertOrganization();
    UUID customerId = createCustomer(organizationId, "지울 고객");

    assertThat(delete(organizationId, customerId)).hasStatus(204);

    assertThat(delete(organizationId, customerId))
        .hasStatus(404)
        .bodyJson()
        .extractingPath("$.code")
        .asString()
        .isEqualTo(ErrorCode.CUSTOMER_NOT_FOUND.getCode());
  }

  @Test
  void 지운_고객은_고칠_수도_없다() {
    UUID organizationId = insertOrganization();
    UUID customerId = createCustomer(organizationId, "지울 고객");
    delete(organizationId, customerId);

    assertThat(
            put(
                organizationId,
                customerId,
                """
        {"name":"되살리기 시도"}
        """))
        .hasStatus(404);
  }

  @Test
  void 다른_도입사_고객은_지울_수_없다() {
    UUID organizationId = insertOrganization();
    UUID otherOrganizationId = insertOrganization();
    UUID otherCustomerId = createCustomer(otherOrganizationId, "남의 고객");

    assertThat(delete(organizationId, otherCustomerId)).hasStatus(404);
    assertThat(customerCount(otherOrganizationId)).isEqualTo(1);
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
    return jdbcTemplate.queryForObject("SELECT clock_timestamp()", OffsetDateTime.class);
  }

  private UUID createCustomer(UUID organizationId, String name) {
    MvcTestResult result =
        post(
            organizationId,
            """
        {"name":"%s"}
        """
                .formatted(name));
    return UUID.fromString(jsonMapper.readTree(bodyText(result)).get("id").asString());
  }

  private String bodyText(MvcTestResult result) {
    try {
      return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    } catch (UnsupportedEncodingException e) {
      throw new IllegalStateException("응답 본문을 읽지 못했다", e);
    }
  }

  private UUID insertOrganization() {
    return jdbcTemplate.queryForObject(
        "INSERT INTO organization (name) VALUES ('테스트 도입사') RETURNING id", UUID.class);
  }

  private void insertEvent(UUID organizationId, UUID customerId) {
    jdbcTemplate.update(
        """
        INSERT INTO event
          (organization_id, transaction_id, customer_id, type, properties, occurred_at)
        VALUES (?, 'tx-1', ?, 'chat_completion', '{"token": 1200}', now())
        """,
        organizationId,
        customerId);
  }

  private Integer customerCount(UUID organizationId) {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM customer WHERE organization_id = ?", Integer.class, organizationId);
  }

  private String nameOf(UUID customerId) {
    return jdbcTemplate.queryForObject(
        "SELECT name FROM customer WHERE id = ?", String.class, customerId);
  }
}
