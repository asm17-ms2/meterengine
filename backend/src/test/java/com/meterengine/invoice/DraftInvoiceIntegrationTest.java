package com.meterengine.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.meterengine.TestCredentials;
import com.meterengine.TestcontainersConfiguration;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class DraftInvoiceIntegrationTest {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");
  private static final String AUGUST = "2026-08";

  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private JdbcTemplate jdbcTemplate;

  private final Map<UUID, String> authorizationsByOrganizationId = new HashMap<>();

  private MockMvcTester mvc;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcTester.from(
            webApplicationContext, builder -> builder.apply(springSecurity()).build());
  }

  @Test
  void 응답에_월과_고객별_금액이_함께_나온다() {
    UUID organizationId = organizationWithTokenBillableMetric();
    UUID acme = insertCustomer(organizationId, "아크메");
    insertEvent(organizationId, "tx-1", acme, 500, "2026-08-10T12:00:00+09:00");
    insertEvent(organizationId, "tx-2", acme, 2799, "2026-08-11T12:00:00+09:00");

    MvcTestResult result = get(organizationId, AUGUST);

    assertThat(result).hasStatusOk().bodyJson().extractingPath("$.month").isEqualTo(AUGUST);
    assertThat(result).bodyJson().extractingPath("$.total_amount").isEqualTo(1640);
    assertThat(result)
        .bodyJson()
        .extractingPath("$.customers[0].customer_id")
        .isEqualTo(acme.toString());
    assertThat(result).bodyJson().extractingPath("$.customers[0].customer_name").isEqualTo("아크메");
    assertThat(result).bodyJson().extractingPath("$.customers[0].amount").isEqualTo(1640);
    assertThat(result)
        .bodyJson()
        .extractingPath("$.customers[0].lines[0].billable_metric_code")
        .isEqualTo("token-usage");
    assertThat(result)
        .bodyJson()
        .extractingPath("$.customers[0].lines[0].target_property")
        .isEqualTo("token");
    assertThat(result)
        .bodyJson()
        .extractingPath("$.customers[0].lines[0].dimension_values")
        .isEqualTo(Map.of());
    assertThat(result)
        .bodyJson()
        .extractingPath("$.customers[0].lines[0].quantity")
        .isEqualTo(3299);
    assertThat(result)
        .bodyJson()
        .extractingPath("$.customers[0].lines[0].unit_price")
        .isEqualTo(0.5);
    assertThat(result).bodyJson().extractingPath("$.customers[0].lines[0].amount").isEqualTo(1640);
    assertThat(result)
        .bodyJson()
        .extractingPath("$.customers[0].unpriced_usages")
        .asArray()
        .isEmpty();
    assertThat(result)
        .bodyJson()
        .extractingPath("$.calculated_at")
        .asString()
        .satisfies(
            value ->
                assertThat(OffsetDateTime.parse(value).getOffset())
                    .isEqualTo(ZoneOffset.ofHours(9)));
  }

  @Test
  void 이벤트가_없는_고객도_라인_없이_금액_0으로_응답에_들어간다() {
    UUID organizationId = organizationWithTokenBillableMetric();
    UUID acme = insertCustomer(organizationId, "아크메");
    insertCustomer(organizationId, "제타상사");
    insertEvent(organizationId, "tx-1", acme, 500, "2026-08-10T12:00:00+09:00");

    MvcTestResult result = get(organizationId, AUGUST);

    assertThat(result).hasStatusOk().bodyJson().extractingPath("$.customers.length()").isEqualTo(2);
    assertThat(result).bodyJson().extractingPath("$.customers[1].customer_name").isEqualTo("제타상사");
    assertThat(result).bodyJson().extractingPath("$.customers[1].amount").isEqualTo(0);
    assertThat(result).bodyJson().extractingPath("$.customers[1].lines.length()").isEqualTo(0);
    assertThat(result).bodyJson().extractingPath("$.total_amount").isEqualTo(250);
  }

  @Test
  void 같은_조합의_단가가_있는_조합만_라인이_된다() {
    UUID organizationId = insertOrganization();
    jdbcTemplate.update(
        """
        INSERT INTO billable_metric
          (organization_id, code, name, event_type, aggregation, target_property,
           dimension_properties)
        VALUES (?, 'token-usage', '토큰 사용량', 'chat_completion', 'sum', 'token', '{model}')
        """,
        organizationId);
    jdbcTemplate.update(
        """
        INSERT INTO price_rate (organization_id, billable_metric_code, dimension_values, unit_price)
        VALUES (?, 'token-usage', '{"model": "a"}', 0.5)
        """,
        organizationId);
    UUID acme = insertCustomer(organizationId, "아크메");
    OffsetDateTime august10 = OffsetDateTime.parse("2026-08-10T12:00:00+09:00");
    insertEvent(organizationId, "tx-1", acme, "a", 500, august10);
    insertEvent(organizationId, "tx-2", acme, "b", 700, august10);

    MvcTestResult result = get(organizationId, AUGUST);

    assertThat(result).hasStatusOk();
    assertThat(result).bodyJson().extractingPath("$.customers[0].lines.length()").isEqualTo(1);
    assertThat(result)
        .bodyJson()
        .extractingPath("$.customers[0].lines[0].dimension_values")
        .isEqualTo(Map.of("model", "a"));
    assertThat(result).bodyJson().extractingPath("$.customers[0].lines[0].quantity").isEqualTo(500);
    assertThat(result).bodyJson().extractingPath("$.customers[0].lines[0].amount").isEqualTo(250);
    assertThat(result).bodyJson().extractingPath("$.customers[0].amount").isEqualTo(250);
    assertThat(result).bodyJson().extractingPath("$.total_amount").isEqualTo(250);
    assertThat(result)
        .bodyJson()
        .extractingPath("$.customers[0].unpriced_usages")
        .isEqualTo(
            List.of(
                Map.of(
                    "billable_metric_code",
                    "token-usage",
                    "target_property",
                    "token",
                    "dimension_values",
                    Map.of("model", "b"),
                    "quantity",
                    700)));
  }

  @Test
  void 수량이_0인_단가_없는_조합은_unpriced_usages에_싣지_않는다() {
    UUID organizationId = insertOrganization();
    jdbcTemplate.update(
        """
        INSERT INTO billable_metric
          (organization_id, code, name, event_type, aggregation, target_property,
           dimension_properties)
        VALUES (?, 'token-usage', '토큰 사용량', 'chat_completion', 'sum', 'token', '{model}')
        """,
        organizationId);
    UUID acme = insertCustomer(organizationId, "아크메");
    OffsetDateTime august10 = OffsetDateTime.parse("2026-08-10T12:00:00+09:00");
    insertEvent(organizationId, "tx-1", acme, "b", 0, august10);

    MvcTestResult result = get(organizationId, AUGUST);

    assertThat(result)
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.customers[0].unpriced_usages")
        .asArray()
        .isEmpty();
  }

  @Test
  void 다른_도입사의_동일한_데이터는_섞이지_않는다() {
    UUID organizationId = organizationWithTokenBillableMetric();
    UUID acme = insertCustomer(organizationId, "아크메");
    insertEvent(organizationId, "tx-1", acme, 500, "2026-08-10T12:00:00+09:00");

    UUID twinOrganizationId = organizationWithTokenBillableMetric();
    UUID twinCustomer = insertCustomer(twinOrganizationId, "아크메");
    insertEvent(twinOrganizationId, "tx-1", twinCustomer, 500, "2026-08-10T12:00:00+09:00");

    MvcTestResult result = get(organizationId, AUGUST);

    assertThat(result).hasStatusOk().bodyJson().extractingPath("$.customers.length()").isEqualTo(1);
    assertThat(result)
        .bodyJson()
        .extractingPath("$.customers[0].customer_id")
        .isEqualTo(acme.toString());
    assertThat(result).bodyJson().extractingPath("$.total_amount").isEqualTo(250);
  }

  @Test
  void 단가가_아직_없는_미터는_라인에서_빠진다() {
    UUID organizationId = organizationWithTokenBillableMetric();
    UUID acme = insertCustomer(organizationId, "아크메");
    insertEvent(organizationId, "tx-1", acme, 500, "2026-08-10T12:00:00+09:00");

    jdbcTemplate.update(
        """
        INSERT INTO billable_metric
          (organization_id, code, name, event_type, aggregation, target_property)
        VALUES (?, 'api-calls', 'API 호출량', 'api_call', 'sum', 'count')
        """,
        organizationId);
    jdbcTemplate.update(
        """
        INSERT INTO event
          (organization_id, transaction_id, customer_id, type, properties, occurred_at)
        VALUES (?, 'tx-2', ?, 'api_call', '{"count": 3}', '2026-08-10T13:00:00+09:00')
        """,
        organizationId,
        acme);

    MvcTestResult result = get(organizationId, AUGUST);

    assertThat(result).hasStatusOk();
    assertThat(result).bodyJson().extractingPath("$.customers[0].lines.length()").isEqualTo(1);
    assertThat(result)
        .bodyJson()
        .extractingPath("$.customers[0].lines[0].billable_metric_code")
        .isEqualTo("token-usage");
    assertThat(result).bodyJson().extractingPath("$.total_amount").isEqualTo(250);
    assertThat(result)
        .bodyJson()
        .extractingPath("$.customers[0].unpriced_usages[*].billable_metric_code")
        .asArray()
        .containsExactly("api-calls");
    assertThat(result)
        .bodyJson()
        .extractingPath("$.customers[0].unpriced_usages[0].quantity")
        .isEqualTo(3);
  }

  @Test
  void month를_지정하면_그_달로_집계한다() {
    UUID organizationId = organizationWithTokenBillableMetric();
    UUID acme = insertCustomer(organizationId, "아크메");
    insertEvent(organizationId, "tx-1", acme, 500, "2026-08-10T12:00:00+09:00");
    insertEvent(organizationId, "tx-2", acme, 700, "2026-09-10T12:00:00+09:00");

    assertThat(get(organizationId, "2026-08"))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.total_amount")
        .isEqualTo(250);
    assertThat(get(organizationId, "2026-09"))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.total_amount")
        .isEqualTo(350);
  }

  @Test
  void month를_생략하면_이번_달_KST로_집계한다() {
    UUID organizationId = organizationWithTokenBillableMetric();
    UUID acme = insertCustomer(organizationId, "아크메");
    OffsetDateTime thisMonth = YearMonth.now(KST).atDay(1).atStartOfDay(KST).toOffsetDateTime();
    insertEvent(organizationId, "tx-1", acme, 500, thisMonth);

    MvcTestResult result =
        mvc.get()
            .uri("/v1/invoices/draft")
            .header(AUTHORIZATION, authorization(organizationId))
            .exchange();

    assertThat(result)
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.month")
        .isEqualTo(YearMonth.now(KST).toString());
    assertThat(result).bodyJson().extractingPath("$.total_amount").isEqualTo(250);
  }

  @Test
  void month_형식이_틀리면_400이다() {
    UUID organizationId = organizationWithTokenBillableMetric();

    assertThat(get(organizationId, "2026-13")).hasStatus(400);
    assertThat(get(organizationId, "2026")).hasStatus(400);
    assertThat(get(organizationId, "august")).hasStatus(400);
  }

  @Test
  void 그_달이_확정된_고객은_나오지_않고_total_amount에서도_빠진다() {
    UUID organizationId = organizationWithTokenBillableMetric();
    UUID acme = insertCustomer(organizationId, "아크메");
    UUID beta = insertCustomer(organizationId, "베타");
    insertEvent(organizationId, "tx-1", acme, 3280, "2026-08-10T12:00:00+09:00");
    insertEvent(organizationId, "tx-2", beta, 1000, "2026-08-10T12:00:00+09:00");
    insertInvoice(organizationId, acme, AUGUST);

    MvcTestResult result = get(organizationId, AUGUST);

    assertThat(result)
        .bodyJson()
        .extractingPath("$.customers[*].customer_id")
        .asArray()
        .containsExactly(beta.toString());
    assertThat(result).bodyJson().extractingPath("$.total_amount").isEqualTo(500);
  }

  @Test
  void 다른_달의_확정은_영향을_주지_않고_모두_확정되면_customers가_빈_배열이다() {
    UUID organizationId = organizationWithTokenBillableMetric();
    UUID acme = insertCustomer(organizationId, "아크메");
    insertEvent(organizationId, "tx-1", acme, 3280, "2026-08-10T12:00:00+09:00");
    insertInvoice(organizationId, acme, "2026-09");

    assertThat(get(organizationId, AUGUST))
        .bodyJson()
        .extractingPath("$.total_amount")
        .isEqualTo(1640);
    assertThat(get(organizationId, "2026-09"))
        .bodyJson()
        .extractingPath("$.customers")
        .asArray()
        .isEmpty();
    assertThat(get(organizationId, "2026-09"))
        .bodyJson()
        .extractingPath("$.total_amount")
        .isEqualTo(0);
  }

  @Test
  void 인증이_없으면_401이다() {
    assertThat(mvc.get().uri("/v1/invoices/draft").exchange()).hasStatus(401);
  }

  // --- 헬퍼 ---

  private String authorization(UUID organizationId) {
    return authorizationsByOrganizationId.computeIfAbsent(
        organizationId, id -> TestCredentials.issueApiKey(jdbcTemplate, id));
  }

  private MvcTestResult get(UUID organizationId, String month) {
    return mvc.get()
        .uri("/v1/invoices/draft?month={month}", month)
        .header(AUTHORIZATION, authorization(organizationId))
        .exchange();
  }

  // 키 없는 토큰 사용량 미터와 빈 조합 단가를 가진 도입사를 만든다.
  private UUID organizationWithTokenBillableMetric() {
    UUID organizationId = insertOrganization();
    jdbcTemplate.update(
        """
        INSERT INTO billable_metric
          (organization_id, code, name, event_type, aggregation, target_property)
        VALUES (?, 'token-usage', '토큰 사용량', 'chat_completion', 'sum', 'token')
        """,
        organizationId);
    jdbcTemplate.update(
        """
        INSERT INTO price_rate (organization_id, billable_metric_code, dimension_values, unit_price)
        VALUES (?, 'token-usage', '{}', 0.5)
        """,
        organizationId);
    return organizationId;
  }

  private UUID insertOrganization() {
    return jdbcTemplate.queryForObject(
        "INSERT INTO organization (name) VALUES ('도입사') RETURNING id", UUID.class);
  }

  private UUID insertCustomer(UUID organizationId, String name) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO customer (organization_id, name) VALUES (?, ?) RETURNING id",
        UUID.class,
        organizationId,
        name);
  }

  private void insertInvoice(UUID organizationId, UUID customerId, String period) {
    jdbcTemplate.update(
        """
        INSERT INTO invoice
          (organization_id, customer_id, customer_name, period, supply_amount, tax_amount,
           total_amount, finalized_at)
        VALUES (?, ?, '확정 고객', ?, 1000, 100, 1100, now())
        """,
        organizationId,
        customerId,
        period);
  }

  private void insertEvent(
      UUID organizationId, String transactionId, UUID customerId, int token, String occurredAt) {
    insertEvent(organizationId, transactionId, customerId, token, OffsetDateTime.parse(occurredAt));
  }

  private void insertEvent(
      UUID organizationId,
      String transactionId,
      UUID customerId,
      int token,
      OffsetDateTime occurredAt) {
    insertEvent(organizationId, transactionId, customerId, "opus-5", token, occurredAt);
  }

  private void insertEvent(
      UUID organizationId,
      String transactionId,
      UUID customerId,
      String model,
      int token,
      OffsetDateTime occurredAt) {
    jdbcTemplate.update(
        """
        INSERT INTO event
          (organization_id, transaction_id, customer_id, type, properties, occurred_at)
        VALUES (?, ?, ?, 'chat_completion', ?::jsonb, ?)
        """,
        organizationId,
        transactionId,
        customerId,
        "{\"model\":\"%s\",\"token\":%d}".formatted(model, token),
        occurredAt);
  }
}
