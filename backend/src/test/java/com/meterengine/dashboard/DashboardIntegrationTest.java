package com.meterengine.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.meterengine.TestCredentials;
import com.meterengine.TestcontainersConfiguration;
import java.util.UUID;
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
  private MockMvcTester mvc;

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
    assertThat(
            mvc.get()
                .uri("/v1/dashboard?month=2026-10")
                .header("Authorization", readKey)
                .exchange())
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.customer_count")
        .isEqualTo(0);
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
            + " target_property, archived_at) values (?, 'archived', '보관 미터', 'archived', 'sum',"
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
            + " target_property) values (?, 'active', '활성 미터', 'active', 'sum', 'quantity')",
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
            + " 'archived', '보관 미터', 'archived', 'sum', 'quantity', now())",
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
            + " 미터', 'active', 'sum', 'quantity')",
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
  void 수집_키와_무인증은_과금_현황을_조회하지_못한다() {
    String ingestKey = TestCredentials.issueApiKey(jdbcTemplate, UUID.randomUUID(), "ingest");
    assertThat(mvc.get().uri("/v1/dashboard").header("Authorization", ingestKey).exchange())
        .hasStatus(403);
    assertThat(mvc.get().uri("/v1/dashboard").exchange()).hasStatus(401);
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
        .hasSize(5);
  }
}
