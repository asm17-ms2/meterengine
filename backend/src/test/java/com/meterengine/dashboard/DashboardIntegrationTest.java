package com.meterengine.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.jayway.jsonpath.JsonPath;
import com.meterengine.TestClock;
import com.meterengine.TestCredentials;
import com.meterengine.TestEvents;
import com.meterengine.TestcontainersConfiguration;
import com.meterengine.dashboard.client.DashboardInsightsStore;
import com.meterengine.dashboard.dto.DashboardInsightsKey;
import com.meterengine.dashboard.service.DashboardInsightsRefreshService;
import com.meterengine.global.security.BusinessEnvironmentContext;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class DashboardIntegrationTest {
  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private TestClock testClock;
  @Autowired private DashboardInsightsRefreshService dashboardInsightsRefreshService;
  @Autowired private DashboardInsightsStore dashboardInsightsStore;
  private MockMvcTester mvc;

  @AfterEach
  void resetClock() {
    testClock.reset();
  }

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcTester.from(
            webApplicationContext, builder -> builder.apply(springSecurity()).build());
  }

  @Test
  void 도입사_세션과_두_모드의_read_키는_같은_과금_현황을_조회한다() {
    UUID organizationId = UUID.randomUUID();
    String session = TestCredentials.issueMemberSession(jdbcTemplate, organizationId);
    for (String credential :
        new String[] {
          session,
          TestCredentials.issueApiKey(jdbcTemplate, organizationId, "test", "read"),
          TestCredentials.issueApiKey(jdbcTemplate, organizationId, "live", "read")
        }) {
      assertThat(
              mvc.get()
                  .uri("/v1/dashboard?month=2026-10")
                  .header("Authorization", credential)
                  .exchange())
          .hasStatusOk()
          .bodyJson()
          .extractingPath("$.customer_count")
          .isEqualTo(0);
    }
  }

  @Test
  void 다른_도입사의_고객과_금액이_현황에_섞이지_않는다() {
    UUID organizationId = UUID.randomUUID();
    UUID otherOrganizationId = UUID.randomUUID();
    String readKey = TestCredentials.issueApiKey(jdbcTemplate, organizationId, "read");
    TestCredentials.issueApiKey(jdbcTemplate, otherOrganizationId, "read");
    jdbcTemplate.update(
        "insert into customer (id, organization_id, name, created_at) values (?, ?, '다른 고객',"
            + " now())",
        UUID.randomUUID(),
        otherOrganizationId);
    UUID otherPaid = insertInvoice(otherOrganizationId, "2026-10", 1000);
    insertAttempt(otherOrganizationId, otherPaid, "test", "done");
    insertInvoice(otherOrganizationId, "2026-10", 2000);
    assertThat(
            mvc.get()
                .uri("/v1/dashboard?month=2026-10")
                .header("Authorization", readKey)
                .exchange())
        .hasStatusOk()
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"customer_count": 0, "finalized_total_amount": 0, "collected_amount": 0,
             "canceled_amount": 0, "outstanding_amount": 0, "failed_payment_count": 0}
            """);
  }

  @Test
  void 개요_금액은_결제_상태마다_수납과_취소와_미수로_나눠_더한다() {
    UUID organizationId = UUID.randomUUID();
    String readKey = TestCredentials.issueApiKey(jdbcTemplate, organizationId, "read");
    UUID paid = insertInvoice(organizationId, "2026-10", 1000);
    insertAttempt(organizationId, paid, "test", "done");
    UUID partial = insertInvoice(organizationId, "2026-10", 2000);
    insertAttempt(organizationId, partial, "test", "done");
    insertCancellation(organizationId, partial, 200);
    UUID canceled = insertInvoice(organizationId, "2026-10", 3000);
    insertAttempt(organizationId, canceled, "test", "done");
    insertCancellation(organizationId, canceled, 3300);
    UUID failed = insertInvoice(organizationId, "2026-10", 4000);
    insertAttempt(organizationId, failed, "test", "failed");
    UUID pending = insertInvoice(organizationId, "2026-10", 5000);
    insertAttempt(organizationId, pending, "test", "pending");
    insertInvoice(organizationId, "2026-10", 6000);
    assertThat(
            mvc.get()
                .uri("/v1/dashboard?month=2026-10")
                .header("Authorization", readKey)
                .exchange())
        .hasStatusOk()
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"finalized_total_amount": 23100, "collected_amount": 3100, "canceled_amount": 3500,
             "outstanding_amount": 16500, "failed_payment_count": 1}
            """);
  }

  @Test
  void 보관한_고객과_미터와_결제_키는_현황의_설정_완료로_세지_않는다() {
    UUID organizationId = UUID.randomUUID();
    String readKey = TestCredentials.issueApiKey(jdbcTemplate, organizationId, "read");
    jdbcTemplate.update(
        "insert into customer (id, organization_id, name, created_at, archived_at) values (?, ?,"
            + " '보관 고객', now(), now())",
        UUID.randomUUID(),
        organizationId);
    jdbcTemplate.update(
        "insert into billable_metric (organization_id, code, name, event_type, aggregation,"
            + " target_property, archived_at) values (?, 'archived', '보관 과금 미터', 'archived', 'sum',"
            + " 'quantity', now())",
        organizationId);
    jdbcTemplate.update(
        "insert into toss_payments_credential (organization_id, mode, client_key, secret_key,"
            + " secret_key_last_four, verified_at, updated_at, archived_at) values (?, 'test',"
            + " 'test_ck_dashboard_mock', 'test_sk_dashboard_mock', 'mock', now(), now(), now())",
        organizationId);
    assertThat(
            mvc.get()
                .uri("/v1/dashboard?month=2026-10")
                .header("Authorization", readKey)
                .exchange())
        .hasStatusOk()
        .bodyJson()
        .isLenientlyEqualTo(
            "{\"customer_count\": 0, \"billable_metric_count\": 0, \"payment_configured\":"
                + " false}");

    jdbcTemplate.update(
        "insert into customer (id, organization_id, name, created_at) values (?, ?, '활성 고객',"
            + " now())",
        UUID.randomUUID(),
        organizationId);
    jdbcTemplate.update(
        "insert into billable_metric (organization_id, code, name, event_type, aggregation,"
            + " target_property) values (?, 'active', '활성 과금 미터', 'active', 'sum', 'quantity')",
        organizationId);
    jdbcTemplate.update(
        "update toss_payments_credential set archived_at = null where organization_id = ?",
        organizationId);
    assertThat(
            mvc.get()
                .uri("/v1/dashboard?month=2026-10")
                .header("Authorization", readKey)
                .exchange())
        .hasStatusOk()
        .bodyJson()
        .isLenientlyEqualTo(
            "{\"customer_count\": 1, \"billable_metric_count\": 1, \"payment_configured\": true}");
  }

  @Test
  void 환경_범위에서도_보관한_고객과_미터와_결제_키는_현황의_설정_완료로_세지_않는다() {
    UUID organizationId = UUID.randomUUID();
    String session = TestCredentials.issueMemberSession(jdbcTemplate, organizationId);
    UUID environmentId =
        jdbcTemplate.queryForObject(
            "select id from organization_environment where organization_id = ? and mode = 'test'",
            UUID.class,
            organizationId);
    jdbcTemplate.update(
        "insert into customer (id, organization_id, environment_id, name, created_at,"
            + " archived_at) values (?, ?, ?, '보관 고객', now(), now())",
        UUID.randomUUID(),
        organizationId,
        environmentId);
    jdbcTemplate.update(
        "insert into billable_metric (organization_id, environment_id, environment_scope_id, code,"
            + " name, event_type, aggregation, target_property, archived_at) values (?, ?, ?,"
            + " 'archived', '보관 과금 미터', 'archived', 'sum', 'quantity', now())",
        organizationId,
        environmentId,
        environmentId);
    jdbcTemplate.update(
        "insert into toss_payments_credential (organization_id, environment_id,"
            + " environment_scope_id, mode, client_key, secret_key, secret_key_last_four,"
            + " verified_at, updated_at, archived_at) values (?, ?, ?, 'test',"
            + " 'test_ck_dashboard_mock', 'test_sk_dashboard_mock', 'mock', now(), now(), now())",
        organizationId,
        environmentId,
        environmentId);
    assertThat(
            mvc.get()
                .uri("/v1/dashboard?month=2026-10")
                .header("Authorization", session)
                .header("X-MeterEngine-Environment", "test")
                .exchange())
        .hasStatusOk()
        .bodyJson()
        .isLenientlyEqualTo(
            "{\"customer_count\": 0, \"billable_metric_count\": 0, \"payment_configured\":"
                + " false}");

    jdbcTemplate.update(
        "insert into customer (id, organization_id, environment_id, name, created_at) values (?,"
            + " ?, ?, '활성 고객', now())",
        UUID.randomUUID(),
        organizationId,
        environmentId);
    jdbcTemplate.update(
        "insert into billable_metric (organization_id, environment_id, environment_scope_id, code,"
            + " name, event_type, aggregation, target_property) values (?, ?, ?, 'active', '활성"
            + " 과금 미터', 'active', 'sum', 'quantity')",
        organizationId,
        environmentId,
        environmentId);
    jdbcTemplate.update(
        "update toss_payments_credential set archived_at = null where organization_id = ?",
        organizationId);
    assertThat(
            mvc.get()
                .uri("/v1/dashboard?month=2026-10")
                .header("Authorization", session)
                .header("X-MeterEngine-Environment", "test")
                .exchange())
        .hasStatusOk()
        .bodyJson()
        .isLenientlyEqualTo(
            "{\"customer_count\": 1, \"billable_metric_count\": 1, \"payment_configured\": true}");
  }

  @Test
  void 선택한_월의_집계를_유지하면서_전체_월의_현재_모드_미해결_결제만_보여준다() {
    UUID organizationId = UUID.randomUUID();
    String session = TestCredentials.issueMemberSession(jdbcTemplate, organizationId);
    UUID pending = insertInvoice(organizationId, "2026-10", 1000);
    UUID failed = insertInvoice(organizationId, "2026-09", 1000);
    UUID unpaid = insertInvoice(organizationId, "2026-08", 1000);
    UUID paid = insertInvoice(organizationId, "2026-07", 1000);
    insertInvoice(organizationId, "2026-06", 0);
    insertAttempt(organizationId, pending, "test", "pending");
    insertAttempt(organizationId, failed, "test", "failed");
    insertAttempt(organizationId, paid, "test", "done");
    insertAttempt(organizationId, paid, "live", "failed");
    UUID otherOrganizationId = UUID.randomUUID();
    TestCredentials.issueMemberSession(jdbcTemplate, otherOrganizationId);
    insertInvoice(otherOrganizationId, "2026-09", 1000);

    assertThat(
            mvc.get()
                .uri("/v1/dashboard?month=2026-10")
                .header("Authorization", session)
                .exchange())
        .hasStatusOk()
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"month":"2026-10", "outstanding_amount":1100, "failed_payment_count":0,
             "invoice_payments":[{"invoice_id":"%s", "month":"2026-10"}],
             "unresolved_invoice_payments":[
               {"invoice_id":"%s", "month":"2026-10", "status":"pending", "mode":"test", "amount":1100},
               {"invoice_id":"%s", "month":"2026-09", "status":"failed", "mode":"test", "amount":1100},
               {"invoice_id":"%s", "month":"2026-08", "status":"unpaid", "mode":"test", "amount":1100}
             ]}
            """
                .formatted(pending, pending, failed, unpaid));
  }

  @Test
  void 미해결_결제가_없으면_전체_월_미해결_목록은_빈_배열이다() {
    String session = TestCredentials.issueMemberSession(jdbcTemplate, UUID.randomUUID());
    assertThat(
            mvc.get()
                .uri("/v1/dashboard?month=2026-10")
                .header("Authorization", session)
                .exchange())
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.unresolved_invoice_payments")
        .asArray()
        .isEmpty();
  }

  @Test
  void 수집_키와_무인증은_과금_현황을_조회하지_못한다() {
    String ingestKey = TestCredentials.issueApiKey(jdbcTemplate, UUID.randomUUID(), "ingest");
    assertThat(mvc.get().uri("/v1/dashboard").header("Authorization", ingestKey).exchange())
        .hasStatus(403);
    assertThat(mvc.get().uri("/v1/dashboard").exchange()).hasStatus(401);
  }

  @Test
  void 인사이트는_최근_석_달의_확정_청구액과_받은_금액을_오래된_달부터_준다() {
    testClock.fix(OffsetDateTime.parse("2026-10-15T03:00:00Z"));
    UUID organizationId = UUID.randomUUID();
    String readKey = TestCredentials.issueApiKey(jdbcTemplate, organizationId, "read");
    UUID paid = insertInvoice(organizationId, "2026-10", 1000);
    insertAttempt(organizationId, paid, "test", "done");
    insertInvoice(organizationId, "2026-08", 2000);
    insertInvoice(organizationId, "2026-07", 5000);
    String json = insights(readKey, "2026-10");
    assertThat((List<String>) JsonPath.read(json, "$.monthly_amounts[*].month"))
        .containsExactly("2026-08", "2026-09", "2026-10");
    assertThat((List<Integer>) JsonPath.read(json, "$.monthly_amounts[*].finalized_total_amount"))
        .containsExactly(2200, 0, 1100);
    assertThat((List<Integer>) JsonPath.read(json, "$.monthly_amounts[*].collected_amount"))
        .containsExactly(0, 0, 1100);
  }

  @Test
  void 인사이트의_달별_금액은_받은_금액과_받을_금액을_공급가액으로_나눠_준다() {
    testClock.fix(OffsetDateTime.parse("2026-10-15T03:00:00Z"));
    UUID organizationId = UUID.randomUUID();
    String readKey = TestCredentials.issueApiKey(jdbcTemplate, organizationId, "read");
    UUID paid = insertInvoice(organizationId, "2026-09", 1000);
    insertAttempt(organizationId, paid, "test", "done");
    UUID failed = insertInvoice(organizationId, "2026-09", 2000);
    insertAttempt(organizationId, failed, "test", "failed");
    insertInvoice(organizationId, "2026-09", 500);
    UUID partiallyCanceled = insertInvoice(organizationId, "2026-08", 4000);
    insertAttempt(organizationId, partiallyCanceled, "test", "done");
    insertCancellation(organizationId, partiallyCanceled, 1100);
    String json = insights(readKey, "2026-10");
    assertThat((List<Integer>) JsonPath.read(json, "$.monthly_amounts[*].collected_supply_amount"))
        .containsExactly(3000, 1000, 0);
    assertThat(
            (List<Integer>) JsonPath.read(json, "$.monthly_amounts[*].outstanding_supply_amount"))
        .containsExactly(0, 2500, 0);
    assertThat((List<Integer>) JsonPath.read(json, "$.monthly_amounts[*].draft_supply_amount"))
        .containsExactly(0, 0, 0);
  }

  @Test
  void 인사이트의_일별_이벤트_수는_KST_날짜로_세고_어제까지만_준다() {
    testClock.fix(OffsetDateTime.parse("2026-10-15T03:00:00Z"));
    UUID organizationId = UUID.randomUUID();
    String readKey = TestCredentials.issueApiKey(jdbcTemplate, organizationId, "read");
    UUID customerId = insertCustomer(organizationId);
    insertEvent(organizationId, customerId, "2026-09-30T14:59:59Z");
    insertEvent(organizationId, customerId, "2026-09-30T15:00:00Z");
    insertEvent(organizationId, customerId, "2026-10-10T01:00:00Z");
    insertEvent(organizationId, customerId, "2026-10-10T02:00:00Z");
    insertEvent(organizationId, customerId, "2026-10-15T01:00:00Z");
    String json = insights(readKey, "2026-10");
    assertThat((List<String>) JsonPath.read(json, "$.daily_event_counts[*].date"))
        .hasSize(14)
        .startsWith("2026-10-01")
        .endsWith("2026-10-14");
    assertThat((List<Integer>) JsonPath.read(json, "$.daily_event_counts[*].event_count"))
        .containsExactly(1, 0, 0, 0, 0, 0, 0, 0, 0, 2, 0, 0, 0, 0);
    assertThat((List<?>) JsonPath.read(insights(readKey, "2026-09"), "$.daily_event_counts"))
        .hasSize(30);
  }

  @Test
  void 인사이트는_계산해_둔_날짜_뒤부터_오늘까지의_이벤트_수를_조회할_때_세어_준다() {
    testClock.fix(OffsetDateTime.parse("2026-10-15T03:00:00Z"));
    UUID organizationId = UUID.randomUUID();
    String readKey = TestCredentials.issueApiKey(jdbcTemplate, organizationId, "read");
    UUID customerId = insertCustomer(organizationId);
    insertEvent(organizationId, customerId, "2026-10-10T01:00:00Z");
    insertEvent(organizationId, customerId, "2026-10-15T01:00:00Z");
    String json = insights(readKey, "2026-10");
    assertThat((List<String>) JsonPath.read(json, "$.recent_event_counts[*].date"))
        .containsExactly("2026-10-15");
    assertThat((List<Integer>) JsonPath.read(json, "$.recent_event_counts[*].event_count"))
        .containsExactly(1);

    // 같은 날 다시 조회하면 계산해 둔 날짜는 그대로이고 오늘 칸만 는다
    insertEvent(organizationId, customerId, "2026-10-10T02:00:00Z");
    insertEvent(organizationId, customerId, "2026-10-15T04:00:00Z");
    testClock.fix(OffsetDateTime.parse("2026-10-15T10:00:00Z"));
    String later = insights(readKey, "2026-10");
    assertThat(dailyEventCount(later, 10)).isEqualTo(1);
    assertThat((List<Integer>) JsonPath.read(later, "$.recent_event_counts[*].event_count"))
        .containsExactly(2);

    // 자정이 지나고 다음 계산(05:00 KST) 전에는 어제도 아직 계산해 둔 값에 없어 함께 센다
    testClock.fix(OffsetDateTime.parse("2026-10-15T16:00:00Z"));
    String afterMidnight = insights(readKey, "2026-10");
    assertThat((List<String>) JsonPath.read(afterMidnight, "$.recent_event_counts[*].date"))
        .containsExactly("2026-10-15", "2026-10-16");
    assertThat((List<Integer>) JsonPath.read(afterMidnight, "$.recent_event_counts[*].event_count"))
        .containsExactly(2, 0);
  }

  @Test
  void 지난_달의_인사이트에는_조회할_때_세는_이벤트_수가_없다() {
    testClock.fix(OffsetDateTime.parse("2026-10-15T03:00:00Z"));
    UUID organizationId = UUID.randomUUID();
    String readKey = TestCredentials.issueApiKey(jdbcTemplate, organizationId, "read");
    insertEvent(organizationId, insertCustomer(organizationId), "2026-10-15T01:00:00Z");
    assertThat((List<?>) JsonPath.read(insights(readKey, "2026-09"), "$.recent_event_counts"))
        .isEmpty();
  }

  @Test
  void 같은_날_다시_조회하면_계산해_둔_값을_주고_다음_날_다시_계산한다() {
    testClock.fix(OffsetDateTime.parse("2026-10-15T03:00:00Z"));
    UUID organizationId = UUID.randomUUID();
    String readKey = TestCredentials.issueApiKey(jdbcTemplate, organizationId, "read");
    UUID customerId = insertCustomer(organizationId);
    insertEvent(organizationId, customerId, "2026-10-10T01:00:00Z");
    assertThat(dailyEventCount(insights(readKey, "2026-10"), 10)).isEqualTo(1);

    insertEvent(organizationId, customerId, "2026-10-10T02:00:00Z");
    testClock.fix(OffsetDateTime.parse("2026-10-15T10:00:00Z"));
    assertThat(dailyEventCount(insights(readKey, "2026-10"), 10)).isEqualTo(1);

    // 다음 날 05:00(KST)이 지나면 앞의 값은 어제 것이라 다시 계산한다
    testClock.fix(OffsetDateTime.parse("2026-10-15T20:10:00Z"));
    String next = insights(readKey, "2026-10");
    assertThat(dailyEventCount(next, 10)).isEqualTo(2);
    assertThat((List<?>) JsonPath.read(next, "$.daily_event_counts")).hasSize(15);
  }

  @Test
  void 하루_한_번_도는_작업은_조회한_적_없는_조직의_인사이트도_저장소에_올린다() {
    testClock.fix(OffsetDateTime.parse("2026-10-15T20:00:00Z"));
    UUID organizationId = UUID.randomUUID();
    String readKey = TestCredentials.issueApiKey(jdbcTemplate, organizationId, "read");
    UUID customerId = insertCustomer(organizationId);
    insertEvent(organizationId, customerId, "2026-10-10T01:00:00Z");

    dashboardInsightsRefreshService.refresh();

    DashboardInsightsKey key =
        new DashboardInsightsKey(
            organizationId, BusinessEnvironmentContext.scopeId(null), YearMonth.of(2026, 10));
    assertThat(dashboardInsightsStore.find(key))
        .hasValueSatisfying(
            stored -> assertThat(stored.dailyEventCounts().get(9).eventCount()).isEqualTo(1));
    // 조회는 저장소의 값을 그대로 준다. 그 뒤에 들어온 이벤트는 다음 계산 때 들어간다
    insertEvent(organizationId, customerId, "2026-10-10T02:00:00Z");
    testClock.fix(OffsetDateTime.parse("2026-10-15T21:00:00Z"));
    assertThat(dailyEventCount(insights(readKey, "2026-10"), 10)).isEqualTo(1);
  }

  @Test
  void 인사이트는_다른_조직의_금액과_이벤트를_섞지_않고_수집_키로는_볼_수_없다() {
    testClock.fix(OffsetDateTime.parse("2026-10-15T03:00:00Z"));
    UUID organizationId = UUID.randomUUID();
    UUID otherOrganizationId = UUID.randomUUID();
    String readKey = TestCredentials.issueApiKey(jdbcTemplate, organizationId, "read");
    TestCredentials.issueApiKey(jdbcTemplate, otherOrganizationId, "read");
    insertInvoice(otherOrganizationId, "2026-10", 1000);
    insertEvent(otherOrganizationId, insertCustomer(otherOrganizationId), "2026-10-10T01:00:00Z");
    String json = insights(readKey, "2026-10");
    assertThat((List<Integer>) JsonPath.read(json, "$.monthly_amounts[*].finalized_total_amount"))
        .containsOnly(0);
    assertThat((List<Integer>) JsonPath.read(json, "$.daily_event_counts[*].event_count"))
        .containsOnly(0);
    assertThat(
            mvc.get()
                .uri("/v1/dashboard/insights?month=2026-10")
                .header(
                    "Authorization",
                    TestCredentials.issueApiKey(jdbcTemplate, organizationId, "ingest"))
                .exchange())
        .hasStatus(403);
  }

  private String insights(String authorization, String month) {
    var result =
        mvc.get()
            .uri("/v1/dashboard/insights?month=" + month)
            .header("Authorization", authorization)
            .exchange();
    assertThat(result).hasStatusOk();
    return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
  }

  private static int dailyEventCount(String json, int dayOfMonth) {
    return JsonPath.<Number>read(json, "$.daily_event_counts[" + (dayOfMonth - 1) + "].event_count")
        .intValue();
  }

  private UUID insertCustomer(UUID organizationId) {
    UUID customerId = UUID.randomUUID();
    jdbcTemplate.update(
        "insert into customer (id, organization_id, name) values (?, ?, '인사이트 시험 고객')",
        customerId,
        organizationId);
    return customerId;
  }

  private void insertEvent(UUID organizationId, UUID customerId, String occurredAt) {
    OffsetDateTime at = OffsetDateTime.parse(occurredAt);
    TestEvents.insert(
        jdbcTemplate, organizationId, "tx-" + UUID.randomUUID(), customerId, "usage", "{}", at, at);
  }

  @Test
  void 전체_운영_현황은_운영자만_조회하고_첫_실행_전_작업도_표시한다() {
    String member = TestCredentials.issueMemberSession(jdbcTemplate, UUID.randomUUID());
    assertThat(mvc.get().uri("/v1/operations").header("Authorization", member).exchange())
        .hasStatus(403);
    assertThat(
            mvc.get()
                .uri("/v1/operations")
                .header("Authorization", TestCredentials.issueOperatorSession(jdbcTemplate))
                .exchange())
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.jobs")
        .asArray()
        .hasSize(7);
  }

  @Test
  void 전체_운영_현황은_검토_대기_중인_실제_환경_이용_신청만_센다() {
    String operator = TestCredentials.issueOperatorSession(jdbcTemplate);
    long before = pendingLiveEnvironmentApplicationCount(operator);
    insertLiveEnvironmentApplication("pending");
    insertLiveEnvironmentApplication("approved");

    assertThat(pendingLiveEnvironmentApplicationCount(operator)).isEqualTo(before + 1);
  }

  private long pendingLiveEnvironmentApplicationCount(String authorization) {
    String body =
        new String(
            mvc.get()
                .uri("/v1/operations")
                .header("Authorization", authorization)
                .exchange()
                .getResponse()
                .getContentAsByteArray(),
            StandardCharsets.UTF_8);
    return JsonPath.<Number>read(body, "$.pending_live_environment_application_count").longValue();
  }

  private void insertLiveEnvironmentApplication(String status) {
    UUID organizationId = UUID.randomUUID();
    TestCredentials.issueMemberSession(jdbcTemplate, organizationId);
    UUID applicantId =
        jdbcTemplate.queryForObject(
            "select user_account_id from organization_member where organization_id = ?",
            UUID.class,
            organizationId);
    boolean pending = "pending".equals(status);
    jdbcTemplate.update(
        "insert into live_environment_application (id, organization_id,"
            + " business_registration_number, toss_payments_merchant_id, toss_payments_client_key,"
            + " toss_payments_secret_key, status, business_status, applicant_user_account_id,"
            + " created_at, reviewer_user_account_id, reviewed_at) values (?, ?, '1234567890',"
            + " 'merchant', ?, ?, ?, 'operating', ?, now(), ?, ?)",
        UUID.randomUUID(),
        organizationId,
        pending ? "live_ck_test" : null,
        pending ? "live_sk_test" : null,
        status,
        applicantId,
        pending ? null : applicantId,
        pending ? null : OffsetDateTime.now());
  }

  private UUID insertInvoice(UUID organizationId, String month, long supplyAmount) {
    UUID customerId = UUID.randomUUID();
    UUID invoiceId = UUID.randomUUID();
    jdbcTemplate.update(
        "insert into customer (id, organization_id, name) values (?, ?, '개요 시험 고객')",
        customerId,
        organizationId);
    jdbcTemplate.update(
        "insert into invoice (id, organization_id, customer_id, customer_name, period,"
            + " supply_amount, tax_amount, total_amount, cutoff_at, finalized_at)"
            + " values (?, ?, ?, '개요 시험 고객', ?, ?, ?, ?, now(), now())",
        invoiceId,
        organizationId,
        customerId,
        month,
        supplyAmount,
        supplyAmount / 10,
        supplyAmount + supplyAmount / 10);
    return invoiceId;
  }

  private void insertCancellation(UUID organizationId, UUID invoiceId, long amount) {
    jdbcTemplate.update(
        "insert into payment_cancellation (id, organization_id, payment_attempt_id, mode, amount,"
            + " reason, status, transaction_key, requested_at, completed_at)"
            + " select ?, organization_id, id, mode, ?, '개요 시험', 'done', ?, now(), now()"
            + " from payment_attempt where organization_id = ? and invoice_id = ? and status = 'done'",
        UUID.randomUUID(),
        amount,
        "mock-cancel-" + UUID.randomUUID(),
        organizationId,
        invoiceId);
  }

  private void insertAttempt(UUID organizationId, UUID invoiceId, String mode, String status) {
    jdbcTemplate.update(
        "insert into payment_attempt (id, organization_id, customer_id, invoice_id, mode, amount,"
            + " order_name, status, payment_key, failure_code, requested_at, completed_at)"
            + " select ?, organization_id, customer_id, id, ?, total_amount, '개요 시험', ?, ?, ?,"
            + " now(), ? from invoice where organization_id = ? and id = ?",
        UUID.randomUUID(),
        mode,
        status,
        "done".equals(status) ? "mock-payment-" + UUID.randomUUID() : null,
        "failed".equals(status) ? "REJECT_CARD_PAYMENT" : null,
        "pending".equals(status) ? null : OffsetDateTime.now(),
        organizationId,
        invoiceId);
  }
}
