package com.meterengine.invoice;

import static org.assertj.core.api.Assertions.assertThat;

import com.meterengine.TestcontainersConfiguration;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
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

/**
 * 청구 예정액 조회 API를 HTTP 계층부터 DB까지 관통해 검증한다 (MS2-124).
 *
 * <p>실제 Postgres여야 하는 이유와 MockMvc를 직접 구성하는 이유는 {@link
 * com.meterengine.metric.BillableMetricUsageIntegrationTest} 참조. 컨텍스트를 공유해 Postgres 컨테이너가 한 번만 뜬다.
 *
 * <p>월 경계 자체(8월 마지막 순간과 9월 첫 순간)는 MS2-129의 테스트가 소유하므로 여기서 반복하지 않는다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class DraftInvoiceIntegrationTest {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");
  private static final String AUGUST = "2026-08";

  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private JdbcTemplate jdbc;

  private MockMvcTester mvc;

  @BeforeEach
  void setUp() {
    mvc = MockMvcTester.from(webApplicationContext);
  }

  @Test
  void 응답에_월과_고객별_금액이_함께_나온다() {
    UUID orgId = organizationWithTokenMetric();
    UUID acme = insertCustomer(orgId, "아크메");
    insertEvent(orgId, "tx-1", acme, 500, "2026-08-10T12:00:00+09:00");
    insertEvent(orgId, "tx-2", acme, 2791, "2026-08-11T12:00:00+09:00");

    MvcTestResult result = get(orgId, AUGUST);

    assertThat(result).hasStatusOk().bodyJson().extractingPath("$.month").isEqualTo(AUGUST);
    // 3291 x 0.5 = 1645.5원 -> 절사 1645원. JSON에 정수로 실리는지까지 겸해서 본다 (인수 조건 "금액은 정수 타입").
    assertThat(result).bodyJson().extractingPath("$.total_amount").isEqualTo(1645);
    assertThat(result)
        .bodyJson()
        .extractingPath("$.customers[0].customer_id")
        .isEqualTo(acme.toString());
    assertThat(result).bodyJson().extractingPath("$.customers[0].customer_name").isEqualTo("아크메");
    assertThat(result).bodyJson().extractingPath("$.customers[0].amount").isEqualTo(1645);
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
        .extractingPath("$.customers[0].lines[0].quantity")
        .isEqualTo(3291);
    assertThat(result)
        .bodyJson()
        .extractingPath("$.customers[0].lines[0].unit_price")
        .isEqualTo(0.5);
    assertThat(result).bodyJson().extractingPath("$.customers[0].lines[0].amount").isEqualTo(1645);
    // 계산 시각은 조회마다 달라서 값이 아니라 형식만 본다: KST 오프셋의 ISO-8601이어야 화면이 그대로 표시한다
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
  void 이벤트가_없는_고객도_금액_0으로_응답에_들어간다() {
    UUID orgId = organizationWithTokenMetric();
    UUID acme = insertCustomer(orgId, "아크메");
    insertCustomer(orgId, "제타상사");
    insertEvent(orgId, "tx-1", acme, 500, "2026-08-10T12:00:00+09:00");

    MvcTestResult result = get(orgId, AUGUST);

    assertThat(result).hasStatusOk().bodyJson().extractingPath("$.customers.length()").isEqualTo(2);
    assertThat(result).bodyJson().extractingPath("$.customers[1].customer_name").isEqualTo("제타상사");
    assertThat(result).bodyJson().extractingPath("$.customers[1].amount").isEqualTo(0);
    assertThat(result).bodyJson().extractingPath("$.customers[1].lines[0].quantity").isEqualTo(0);
    assertThat(result).bodyJson().extractingPath("$.customers[1].lines[0].amount").isEqualTo(0);
    // 단가는 사용량과 무관한 미터의 속성이라 0이 아니라 실제 값이 나간다 (화면 목업과 동일)
    assertThat(result)
        .bodyJson()
        .extractingPath("$.customers[1].lines[0].unit_price")
        .isEqualTo(0.5);
    assertThat(result).bodyJson().extractingPath("$.total_amount").isEqualTo(250);
  }

  @Test
  void 다른_도입사의_동일한_데이터는_섞이지_않는다() {
    UUID orgId = organizationWithTokenMetric();
    UUID acme = insertCustomer(orgId, "아크메");
    insertEvent(orgId, "tx-1", acme, 500, "2026-08-10T12:00:00+09:00");

    // 도입사 ID만 다르고 고객 이름, 미터, transaction_id, 수량, 시각이 전부 같은 쌍둥이 (인수 조건의 오집계 방지)
    UUID twinOrgId = organizationWithTokenMetric();
    UUID twinCustomer = insertCustomer(twinOrgId, "아크메");
    insertEvent(twinOrgId, "tx-1", twinCustomer, 500, "2026-08-10T12:00:00+09:00");

    MvcTestResult result = get(orgId, AUGUST);

    assertThat(result).hasStatusOk().bodyJson().extractingPath("$.customers.length()").isEqualTo(1);
    assertThat(result)
        .bodyJson()
        .extractingPath("$.customers[0].customer_id")
        .isEqualTo(acme.toString());
    assertThat(result).bodyJson().extractingPath("$.total_amount").isEqualTo(250);
  }

  @Test
  void 단가가_아직_없는_미터는_라인에서_빠진다() {
    UUID orgId = organizationWithTokenMetric();
    UUID acme = insertCustomer(orgId, "아크메");
    insertEvent(orgId, "tx-1", acme, 500, "2026-08-10T12:00:00+09:00");

    // 정책 등록 API(MS2-157)는 정책만 만들고 단가는 MS2-177이 붙인다. 그 사이의 미터가
    // 사용량을 가져도 청구 예정액은 죽거나 0원을 내보내지 않고 라인을 뺀다 (PR 43 리뷰 결정).
    jdbc.update(
        """
        INSERT INTO billable_metric
          (organization_id, code, name, event_type, aggregation, target_property)
        VALUES (?, 'api-calls', 'API 호출량', 'api_call', 'SUM', 'count')
        """,
        orgId);
    assertThat(
            mvc.post()
                .uri("/v1/billable-metrics/api-calls/price-policy")
                .header("X-Organization-Id", orgId.toString())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"dimension_properties\": []}")
                .exchange())
        .hasStatus(201);
    jdbc.update(
        """
        INSERT INTO usage_event
          (organization_id, transaction_id, customer_id, event_type, properties, occurred_at)
        VALUES (?, 'tx-2', ?, 'api_call', '{"count": 3}', '2026-08-10T13:00:00+09:00')
        """,
        orgId,
        acme);

    MvcTestResult result = get(orgId, AUGUST);

    assertThat(result).hasStatusOk();
    assertThat(result).bodyJson().extractingPath("$.customers[0].lines.length()").isEqualTo(1);
    assertThat(result)
        .bodyJson()
        .extractingPath("$.customers[0].lines[0].billable_metric_code")
        .isEqualTo("token-usage");
    assertThat(result).bodyJson().extractingPath("$.total_amount").isEqualTo(250);
  }

  @Test
  void month를_지정하면_그_달로_집계한다() {
    UUID orgId = organizationWithTokenMetric();
    UUID acme = insertCustomer(orgId, "아크메");
    insertEvent(orgId, "tx-1", acme, 500, "2026-08-10T12:00:00+09:00");
    insertEvent(orgId, "tx-2", acme, 700, "2026-09-10T12:00:00+09:00");

    assertThat(get(orgId, "2026-08"))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.total_amount")
        .isEqualTo(250);
    assertThat(get(orgId, "2026-09"))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.total_amount")
        .isEqualTo(350);
  }

  @Test
  void month를_생략하면_이번_달_KST로_집계한다() {
    UUID orgId = organizationWithTokenMetric();
    UUID acme = insertCustomer(orgId, "아크메");
    // 이번 달 1일 정오(KST). 지금이 월말 자정 직전이어도 같은 달 안이다.
    OffsetDateTime thisMonth =
        YearMonth.now(KST).atDay(1).atTime(12, 0).atZone(KST).toOffsetDateTime();
    insertEvent(orgId, "tx-1", acme, 500, thisMonth);

    MvcTestResult result =
        mvc.get().uri("/v1/invoice").header("X-Organization-Id", orgId.toString()).exchange();

    assertThat(result)
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.month")
        .isEqualTo(YearMonth.now(KST).toString());
    assertThat(result).bodyJson().extractingPath("$.total_amount").isEqualTo(250);
  }

  @Test
  void month_형식이_틀리면_400이다() {
    UUID orgId = organizationWithTokenMetric();

    assertThat(get(orgId, "2026-13")).hasStatus(400);
    assertThat(get(orgId, "2026")).hasStatus(400);
    assertThat(get(orgId, "august")).hasStatus(400);
  }

  @Test
  void 도입사_헤더가_없거나_형식이_틀리면_400이다() {
    assertThat(mvc.get().uri("/v1/invoice").exchange()).hasStatus(400);
    assertThat(mvc.get().uri("/v1/invoice").header("X-Organization-Id", "not-a-uuid").exchange())
        .hasStatus(400);
  }

  // ---------------------------------------------------------------------------
  // 헬퍼 (BillableMetricUsageIntegrationTest와 같은 방식)
  // ---------------------------------------------------------------------------

  private MvcTestResult get(UUID organizationId, String month) {
    return mvc.get()
        .uri("/v1/invoice?month={month}", month)
        .header("X-Organization-Id", organizationId.toString())
        .exchange();
  }

  /** 시드와 같은 모양의 미터(chat_completion의 token을 SUM, 단가 0.5원)를 가진 도입사를 만든다. */
  private UUID organizationWithTokenMetric() {
    UUID organizationId = insertOrganization();
    jdbc.update(
        """
        INSERT INTO billable_metric
          (organization_id, code, name, event_type, aggregation, target_property)
        VALUES (?, 'token-usage', '토큰 사용량', 'chat_completion', 'SUM', 'token')
        """,
        organizationId);
    // 단가는 MS2-158부터 분리 테이블에 있다. 무차원 정책 + '{}' 기본 단가 행이 시드와 같은 규약이다.
    jdbc.update(
        "INSERT INTO price_policy (organization_id, billable_metric_code) VALUES (?, 'token-usage')",
        organizationId);
    jdbc.update(
        """
        INSERT INTO price_rate (organization_id, billable_metric_code, dimension_values, unit_price)
        VALUES (?, 'token-usage', '{}', 0.5)
        """,
        organizationId);
    return organizationId;
  }

  private UUID insertOrganization() {
    return jdbc.queryForObject(
        "INSERT INTO organization (name) VALUES ('도입사') RETURNING id", UUID.class);
  }

  private UUID insertCustomer(UUID organizationId, String name) {
    return jdbc.queryForObject(
        "INSERT INTO customer (organization_id, name) VALUES (?, ?) RETURNING id",
        UUID.class,
        organizationId,
        name);
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
    jdbc.update(
        """
        INSERT INTO usage_event
          (organization_id, transaction_id, customer_id, event_type, properties, occurred_at)
        VALUES (?, ?, ?, 'chat_completion', ?::jsonb, ?)
        """,
        organizationId,
        transactionId,
        customerId,
        "{\"model\":\"opus-5\",\"token\":%d}".formatted(token),
        occurredAt);
  }
}
