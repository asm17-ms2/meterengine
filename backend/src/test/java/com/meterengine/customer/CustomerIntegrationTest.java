package com.meterengine.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.meterengine.TestCredentials;
import com.meterengine.TestcontainersConfiguration;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.metric.service.BillableMetricUsageService;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class CustomerIntegrationTest {

  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private JsonMapper jsonMapper;

  private final Map<UUID, String> authorizationsByOrganizationId = new HashMap<>();

  private MockMvcTester mvc;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcTester.from(
            webApplicationContext, builder -> builder.apply(springSecurity()).build());
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
                .header(AUTHORIZATION, authorization(organizationId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"transaction_id":"tx-1","customer_id":"%s","type":"chat_completion",
                     "properties":{"token":1200},"timestamp":"%s"}
                    """
                        .formatted(
                            customerId,
                            OffsetDateTime.now(BillableMetricUsageService.BILLING_ZONE)))
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
  void 이름에_받지_않는_문자가_있으면_400이고_저장은_0건이다() {
    UUID organizationId = insertOrganization();

    for (String unstorable : new String[] {"a\\u0000b", "a\\ud800b"}) {
      MvcTestResult result = post(organizationId, "{\"name\":\"%s\"}".formatted(unstorable));

      assertThat(result)
          .hasStatus(400)
          .bodyJson()
          .extractingPath("$.code")
          .asString()
          .isEqualTo(ErrorCode.VALIDATION_ERROR.getCode());
      assertThat(result)
          .bodyJson()
          .extractingPath("$.errors[*].field")
          .asArray()
          .containsExactly("name");
    }
    assertThat(customerCount(organizationId)).isZero();
  }

  @Test
  void 이모지와_한국어_이름은_그대로_저장된다() {
    UUID organizationId = insertOrganization();

    UUID customerId = createCustomer(organizationId, "아크메 \\ud83d\\ude00");

    assertThat(nameOf(customerId)).isEqualTo("아크메 😀");
  }

  @Test
  void 같은_이름을_두_번_등록하면_둘_다_남는다() {
    UUID organizationId = insertOrganization();

    UUID first = createCustomer(organizationId, "아크메");
    UUID second = createCustomer(organizationId, "아크메");

    assertThat(first).isNotEqualTo(second);
    assertThat(customerCount(organizationId)).isEqualTo(2);
  }

  @Test
  void 청구_이메일을_넣어_등록하면_응답과_목록에_실린다() {
    UUID organizationId = insertOrganization();

    assertThat(
            post(
                organizationId,
                """
        {"name":"아크메","billing_email":"billing@acme.example"}
        """))
        .hasStatus(201)
        .bodyJson()
        .extractingPath("$.billing_email")
        .isEqualTo("billing@acme.example");

    assertThat(list(organizationId))
        .bodyJson()
        .extractingPath("$.customers[0].billing_email")
        .isEqualTo("billing@acme.example");
  }

  @Test
  void 청구_이메일을_빼거나_null로_등록하면_비어_있다() {
    UUID organizationId = insertOrganization();

    for (String body :
        new String[] {"{\"name\":\"아크메\"}", "{\"name\":\"베타\",\"billing_email\":null}"}) {
      MvcTestResult created = post(organizationId, body);
      assertThat(created).hasStatus(201);
      assertThat(jsonMapper.readTree(bodyText(created)).get("billing_email").isNull()).isTrue();
    }
  }

  @Test
  void 청구_이메일이_형식이_틀리거나_비었거나_도메인에_점이_없으면_400이고_저장은_0건이다() {
    UUID organizationId = insertOrganization();

    for (String invalid :
        new String[] {
          "billing",
          "billing@",
          "@acme.example",
          "",
          "   ",
          "a@b",
          "billing@acme",
          "billing@localhost",
          "billing@acme.example\\r\\nBcc: x@acme.example"
        }) {
      MvcTestResult result =
          post(organizationId, "{\"name\":\"아크메\",\"billing_email\":\"%s\"}".formatted(invalid));
      assertThat(result)
          .hasStatus(400)
          .bodyJson()
          .extractingPath("$.errors[*].field")
          .asArray()
          .containsOnly("billing_email");
      assertThat(result)
          .bodyJson()
          .extractingPath("$.errors[*].message")
          .asArray()
          .containsOnly("올바른 형식의 이메일 주소여야 합니다");
    }
    assertThat(customerCount(organizationId)).isZero();
  }

  @Test
  void 청구_이메일은_254자까지_받고_255자는_거절한다() {
    UUID organizationId = insertOrganization();

    assertThat(
            post(
                organizationId,
                "{\"name\":\"아크메\",\"billing_email\":\"%s\"}".formatted(emailOfLength(254))))
        .hasStatus(201);
    assertThat(
            post(
                organizationId,
                "{\"name\":\"베타\",\"billing_email\":\"%s\"}".formatted(emailOfLength(255))))
        .hasStatus(400)
        .bodyJson()
        .extractingPath("$.errors[0].field")
        .asString()
        .isEqualTo("billing_email");
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
  void 수정도_받지_않는_문자가_있으면_400이고_이름은_그대로다() {
    UUID organizationId = insertOrganization();
    UUID customerId = createCustomer(organizationId, "옛 이름");

    for (String unstorable : new String[] {"a\\u0000b", "a\\ud800b"}) {
      assertThat(put(organizationId, customerId, "{\"name\":\"%s\"}".formatted(unstorable)))
          .hasStatus(400)
          .bodyJson()
          .extractingPath("$.errors[*].field")
          .asArray()
          .containsExactly("name");
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
                .header(AUTHORIZATION, authorization(organizationId))
                .exchange())
        .hasStatus(400)
        .bodyJson()
        .extractingPath("$.code")
        .asString()
        .isEqualTo(ErrorCode.VALIDATION_ERROR.getCode());
  }

  @Test
  void 수정으로_청구_이메일을_넣고_바꾸고_빼면_지워진다() {
    UUID organizationId = insertOrganization();
    UUID customerId = createCustomer(organizationId, "아크메");

    assertThat(
            put(
                organizationId,
                customerId,
                """
        {"name":"아크메","billing_email":"old@acme.example"}
        """))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.billing_email")
        .isEqualTo("old@acme.example");
    assertThat(listedBillingEmail(organizationId)).isEqualTo("old@acme.example");

    put(
        organizationId,
        customerId,
        """
        {"name":"아크메","billing_email":"new@acme.example"}
        """);
    assertThat(listedBillingEmail(organizationId)).isEqualTo("new@acme.example");

    assertThat(put(organizationId, customerId, "{\"name\":\"아크메\"}")).hasStatusOk();
    assertThat(listedBillingEmail(organizationId)).isNull();
  }

  @Test
  void 수정도_청구_이메일_형식이_틀리면_400이고_이메일은_그대로다() {
    UUID organizationId = insertOrganization();
    UUID customerId = createCustomer(organizationId, "아크메");
    put(
        organizationId,
        customerId,
        """
        {"name":"아크메","billing_email":"billing@acme.example"}
        """);

    assertThat(
            put(
                organizationId,
                customerId,
                """
        {"name":"아크메","billing_email":"billing"}
        """))
        .hasStatus(400)
        .bodyJson()
        .extractingPath("$.code")
        .asString()
        .isEqualTo(ErrorCode.VALIDATION_ERROR.getCode());
    assertThat(listedBillingEmail(organizationId)).isEqualTo("billing@acme.example");
  }

  @Test
  void 보관은_기존_이벤트와_청구결제_행을_보존하고_목록에서_숨긴다() {
    UUID organizationId = insertOrganization();
    UUID customerId = createCustomer(organizationId, "보관 고객");
    insertEvent(organizationId, customerId);
    insertFailedPaymentAttempt(organizationId, customerId);
    assertThat(delete(organizationId, customerId)).hasStatus(204);
    assertThat(delete(organizationId, customerId)).hasStatus(204);
    assertThat(customerCount(organizationId)).isEqualTo(1);
    assertThat(list(organizationId)).bodyJson().extractingPath("$.customers").asArray().isEmpty();
    assertThat(
            mvc.get()
                .uri("/v1/customers?status=archived")
                .header(AUTHORIZATION, authorization(organizationId))
                .exchange())
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.customers")
        .asArray()
        .hasSize(1);
    assertThat(
            mvc.get()
                .uri("/v1/customers/" + customerId)
                .header(AUTHORIZATION, authorization(organizationId))
                .exchange())
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.archived_at")
        .isNotNull();
    assertThat(
            mvc.post()
                .uri("/v1/customers/" + customerId + "/restorations")
                .header(AUTHORIZATION, authorization(organizationId))
                .exchange())
        .hasStatusOk();
    assertThat(list(organizationId)).bodyJson().extractingPath("$.customers").asArray().hasSize(1);
  }

  @Test
  void 보관한_고객은_복원_전까지_수정할_수_없다() {
    UUID organizationId = insertOrganization();
    UUID customerId = createCustomer(organizationId, "보관 고객");
    delete(organizationId, customerId);
    assertThat(put(organizationId, customerId, "{\"name\":\"이름\"}")).hasStatus(409);
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
  void 인증이_없으면_네_메서드_모두_401_authentication_required다() {
    for (MvcTestResult result :
        new MvcTestResult[] {
          mvc.get().uri("/v1/customers").exchange(),
          mvc.post()
              .uri("/v1/customers")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"name\":\"아크메\"}")
              .exchange(),
          mvc.put()
              .uri("/v1/customers/" + UUID.randomUUID())
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"name\":\"아크메\"}")
              .exchange(),
          mvc.delete().uri("/v1/customers/" + UUID.randomUUID()).exchange()
        }) {
      assertThat(result)
          .hasStatus(401)
          .bodyJson()
          .extractingPath("$.code")
          .isEqualTo(ErrorCode.AUTHENTICATION_REQUIRED.getCode());
    }
  }

  // --- 헬퍼 ---

  private String authorization(UUID organizationId) {
    return authorizationsByOrganizationId.computeIfAbsent(
        organizationId, id -> TestCredentials.issueApiKey(jdbcTemplate, id));
  }

  private MvcTestResult list(UUID organizationId) {
    return mvc.get()
        .uri("/v1/customers")
        .header(AUTHORIZATION, authorization(organizationId))
        .exchange();
  }

  private MvcTestResult post(UUID organizationId, String jsonBody) {
    return mvc.post()
        .uri("/v1/customers")
        .header(AUTHORIZATION, authorization(organizationId))
        .contentType(MediaType.APPLICATION_JSON)
        .content(jsonBody)
        .exchange();
  }

  private MvcTestResult put(UUID organizationId, UUID customerId, String jsonBody) {
    return mvc.put()
        .uri("/v1/customers/" + customerId)
        .header(AUTHORIZATION, authorization(organizationId))
        .contentType(MediaType.APPLICATION_JSON)
        .content(jsonBody)
        .exchange();
  }

  private MvcTestResult delete(UUID organizationId, UUID customerId) {
    return mvc.delete()
        .uri("/v1/customers/" + customerId)
        .header(AUTHORIZATION, authorization(organizationId))
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

  private void insertInvoice(UUID organizationId, UUID customerId) {
    jdbcTemplate.update(
        """
        INSERT INTO invoice
          (organization_id, customer_id, customer_name, period, supply_amount, tax_amount,
           total_amount, finalized_at)
        VALUES (?, ?, 'acme', '2026-08', 12000, 1200, 13200, now())
        """,
        organizationId,
        customerId);
  }

  private void insertFailedPaymentAttempt(UUID organizationId, UUID customerId) {
    insertInvoice(organizationId, customerId);
    UUID invoiceId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM invoice WHERE organization_id = ? AND customer_id = ?",
            UUID.class,
            organizationId,
            customerId);
    jdbcTemplate.update(
        """
        INSERT INTO payment_attempt
          (id, organization_id, customer_id, invoice_id, amount, order_name, status,
           failure_code, failure_message, requested_at, completed_at, mode)
        VALUES (gen_random_uuid(), ?, ?, ?, 1000, '2026-08 사용료', 'failed',
                'billing_key_not_registered', '등록된 카드가 없어 결제를 요청하지 않았습니다', now(), now(), 'live')
        """,
        organizationId,
        customerId,
        invoiceId);
  }

  private Integer customerCount(UUID organizationId) {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM customer WHERE organization_id = ?", Integer.class, organizationId);
  }

  private String nameOf(UUID customerId) {
    return jdbcTemplate.queryForObject(
        "SELECT name FROM customer WHERE id = ?", String.class, customerId);
  }

  private String listedBillingEmail(UUID organizationId) {
    JsonNode billingEmail =
        jsonMapper
            .readTree(bodyText(list(organizationId)))
            .get("customers")
            .get(0)
            .get("billing_email");
    return billingEmail.isNull() ? null : billingEmail.asString();
  }

  private static String emailOfLength(int length) {
    String localPart = "a".repeat(64);
    String domain = "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(length - 197) + ".com";
    return localPart + "@" + domain;
  }
}
