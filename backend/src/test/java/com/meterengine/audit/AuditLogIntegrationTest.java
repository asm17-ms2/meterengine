package com.meterengine.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.meterengine.TestCredentials;
import com.meterengine.TestEvents;
import com.meterengine.TestcontainersConfiguration;
import com.meterengine.audit.service.AuditActor;
import com.meterengine.audit.service.AuditLogService;
import com.meterengine.global.security.AuthenticatedPrincipal;
import com.meterengine.global.security.PrincipalKind;
import com.meterengine.invoice.entity.Invoice;
import com.meterengine.invoice.service.InvoiceFinalizationService;
import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class AuditLogIntegrationTest {

  private static final String BILLABLE_METRIC_CODE = "input-tokens";

  @Autowired private AuditLogService auditLogService;
  @Autowired private InvoiceFinalizationService invoiceFinalizationService;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private EntityManager entityManager;
  @Autowired private WebApplicationContext webApplicationContext;

  private MockMvcTester mvc;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcTester.from(
            webApplicationContext, builder -> builder.apply(springSecurity()).build());
  }

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void admin_키로_단가를_만들고_지우면_키를_행위자로_감사_행이_남는다() {
    UUID organizationId = insertOrganizationWithBillableMetric();
    String authorization = TestCredentials.issueApiKey(jdbcTemplate, organizationId);
    UUID apiKeyId = apiKeyIdOf(authorization);

    assertThat(
            mvc.post()
                .uri("/v1/billable-metrics/" + BILLABLE_METRIC_CODE + "/price-rates")
                .header(AUTHORIZATION, authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"dimension_values\": {\"model\": \"opus\"}, \"unit_price\": 0.007}")
                .exchange())
        .hasStatus(201);
    assertThat(
            mvc.delete()
                .uri("/v1/billable-metrics/" + BILLABLE_METRIC_CODE + "/price-rates")
                .queryParam("dimension_values", "{\"model\":\"opus\"}")
                .header(AUTHORIZATION, authorization)
                .exchange())
        .hasStatus(204);

    assertThat(auditLogsOf(organizationId))
        .extracting(
            row -> row.get("action"),
            row -> row.get("actor_type"),
            row -> row.get("actor_id"),
            row -> row.get("target_type"),
            row -> row.get("target_id"),
            row -> row.get("detail"))
        .containsExactlyInAnyOrder(
            tuple(
                "price_rate_created",
                "api_key",
                apiKeyId,
                "price_rate",
                BILLABLE_METRIC_CODE,
                "{\"unit_price\": 0.007, \"dimension_values\": {\"model\": \"opus\"}}"),
            tuple(
                "price_rate_deleted",
                "api_key",
                apiKeyId,
                "price_rate",
                BILLABLE_METRIC_CODE,
                "{\"dimension_values\": {\"model\": \"opus\"}}"));
  }

  @Test
  void 인보이스_확정은_확정한_인보이스마다_감사_행을_남긴다() {
    UUID organizationId = insertOrganizationWithBillableMetric();
    insertPriceRate(organizationId);
    UUID acme = insertCustomer(organizationId, "아크메");
    UUID beta = insertCustomer(organizationId, "베타");
    insertEvent(organizationId, "tx-1", acme, 1000);
    insertEvent(organizationId, "tx-2", beta, 2000);
    String authorization = TestCredentials.issueApiKey(jdbcTemplate, organizationId);
    UUID apiKeyId = apiKeyIdOf(authorization);

    assertThat(
            mvc.post()
                .uri("/v1/invoices?month=2026-08")
                .header(AUTHORIZATION, authorization)
                .exchange())
        .hasStatusOk();
    entityManager.flush();

    List<String> invoiceIds =
        jdbcTemplate.queryForList(
            "SELECT id::text FROM invoice WHERE organization_id = ?", String.class, organizationId);
    assertThat(invoiceIds).hasSize(2);
    assertThat(auditLogsOf(organizationId))
        .extracting(
            row -> row.get("action"),
            row -> row.get("actor_type"),
            row -> row.get("actor_id"),
            row -> row.get("target_type"),
            row -> row.get("target_id"),
            row -> row.get("detail"))
        .containsExactlyInAnyOrder(
            tuple(
                "invoice_finalized",
                "api_key",
                apiKeyId,
                "invoice",
                invoiceIds.get(0),
                "{\"month\": \"2026-08\"}"),
            tuple(
                "invoice_finalized",
                "api_key",
                apiKeyId,
                "invoice",
                invoiceIds.get(1),
                "{\"month\": \"2026-08\"}"));
  }

  @Test
  void 요청_밖에서_인보이스를_확정하면_시스템을_행위자로_인보이스의_도입사에_남긴다() {
    UUID organizationId = insertOrganizationWithBillableMetric();
    insertPriceRate(organizationId);
    insertEvent(organizationId, "tx-1", insertCustomer(organizationId, "아크메"), 1000);

    Invoice invoice =
        invoiceFinalizationService.finalize(organizationId, YearMonth.of(2026, 8)).getFirst();

    assertThat(auditLogsOfTarget(invoice.getId().toString()))
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.get("action")).isEqualTo("invoice_finalized");
              assertThat(row.get("actor_type")).isEqualTo("system");
              assertThat(row.get("actor_id")).isNull();
              assertThat(row.get("organization_id")).isEqualTo(organizationId);
            });
  }

  @Test
  void 시스템_행위자로_남긴_기록은_행위자_id와_도입사가_비어_있다() {
    String targetId = UUID.randomUUID().toString();

    auditLogService.record(
        AuditActor.system(), "console_key_issued", "api_key", targetId, Map.of());

    assertThat(auditLogsOfTarget(targetId))
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.get("action")).isEqualTo("console_key_issued");
              assertThat(row.get("actor_type")).isEqualTo("system");
              assertThat(row.get("actor_id")).isNull();
              assertThat(row.get("organization_id")).isNull();
              assertThat(row.get("target_type")).isEqualTo("api_key");
              assertThat(row.get("detail")).isEqualTo("{}");
              assertThat(row.get("occurred_at")).isNotNull();
            });
  }

  @Test
  void 세션_주체가_남긴_기록은_사용자_계정을_행위자로_남긴다() {
    UUID organizationId = insertOrganization();
    UUID userAccountId = UUID.randomUUID();
    AuthenticatedPrincipal principal =
        new AuthenticatedPrincipal(
            PrincipalKind.SESSION,
            organizationId,
            null,
            userAccountId,
            UUID.randomUUID(),
            null,
            "member",
            Set.of());
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.authorities()));

    auditLogService.record("session_created", "user_session", "s-1", Map.of("reason", "login"));

    assertThat(auditLogsOf(organizationId))
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.get("actor_type")).isEqualTo("user_account");
              assertThat(row.get("actor_id")).isEqualTo(userAccountId);
              assertThat(row.get("detail")).isEqualTo("{\"reason\": \"login\"}");
            });
  }

  @Test
  void 도입사를_넘긴_기록은_주체가_있으면_주체를_행위자로_넘긴_도입사에_남긴다() {
    UUID principalOrganizationId = insertOrganization();
    UUID organizationId = insertOrganization();
    UUID userAccountId = UUID.randomUUID();
    authenticateAsSession(principalOrganizationId, userAccountId);
    String targetId = UUID.randomUUID().toString();

    auditLogService.record(
        organizationId, "organization_approved", "organization", targetId, Map.of());

    assertThat(auditLogsOfTarget(targetId))
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.get("actor_type")).isEqualTo("user_account");
              assertThat(row.get("actor_id")).isEqualTo(userAccountId);
              assertThat(row.get("organization_id")).isEqualTo(organizationId);
            });
  }

  @Test
  void 도입사를_넘긴_기록은_주체가_없으면_시스템을_행위자로_넘긴_도입사에_남긴다() {
    UUID organizationId = insertOrganization();
    String targetId = UUID.randomUUID().toString();

    auditLogService.record(organizationId, "api_key_created", "api_key", targetId, Map.of());

    assertThat(auditLogsOfTarget(targetId))
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.get("actor_type")).isEqualTo("system");
              assertThat(row.get("actor_id")).isNull();
              assertThat(row.get("organization_id")).isEqualTo(organizationId);
            });
  }

  @Test
  void 사용자_계정_행위자는_계정과_도입사를_남긴다() {
    UUID organizationId = insertOrganization();
    UUID userAccountId = UUID.randomUUID();

    auditLogService.record(
        AuditActor.userAccount(userAccountId, organizationId),
        "organization_approved",
        "organization",
        organizationId.toString(),
        Map.of());

    assertThat(auditLogsOf(organizationId))
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.get("actor_type")).isEqualTo("user_account");
              assertThat(row.get("actor_id")).isEqualTo(userAccountId);
            });
  }

  @Test
  void 남긴_감사_행은_UPDATE할_수_없다() {
    String targetId = recordSystemAuditLog();

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "UPDATE audit_log SET action = 'console_key_revoked' WHERE target_id = ?",
                    targetId))
        .isInstanceOf(UncategorizedSQLException.class)
        .hasMessageContaining("audit_log is append-only");
  }

  @Test
  void 남긴_감사_행은_DELETE할_수_없다() {
    String targetId = recordSystemAuditLog();

    assertThatThrownBy(
            () -> jdbcTemplate.update("DELETE FROM audit_log WHERE target_id = ?", targetId))
        .isInstanceOf(UncategorizedSQLException.class)
        .hasMessageContaining("audit_log is append-only");
  }

  @Test
  void 감사_테이블은_TRUNCATE할_수_없다() {
    assertThatThrownBy(() -> jdbcTemplate.execute("TRUNCATE audit_log"))
        .isInstanceOf(UncategorizedSQLException.class)
        .hasMessageContaining("audit_log is append-only");
  }

  private void authenticateAsSession(UUID organizationId, UUID userAccountId) {
    AuthenticatedPrincipal principal =
        new AuthenticatedPrincipal(
            PrincipalKind.SESSION,
            organizationId,
            null,
            userAccountId,
            UUID.randomUUID(),
            null,
            "operator",
            Set.of());
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.authorities()));
  }

  private String recordSystemAuditLog() {
    String targetId = UUID.randomUUID().toString();
    auditLogService.record(
        AuditActor.system(), "console_key_issued", "api_key", targetId, Map.of());
    assertThat(auditLogsOfTarget(targetId)).hasSize(1);
    return targetId;
  }

  private List<Map<String, Object>> auditLogsOf(UUID organizationId) {
    entityManager.flush();
    return jdbcTemplate.queryForList(
        """
        SELECT action, actor_type, actor_id, organization_id, target_type, target_id,
               detail::text AS detail, occurred_at
        FROM audit_log WHERE organization_id = ?
        """,
        organizationId);
  }

  private List<Map<String, Object>> auditLogsOfTarget(String targetId) {
    entityManager.flush();
    return jdbcTemplate.queryForList(
        """
        SELECT action, actor_type, actor_id, organization_id, target_type, target_id,
               detail::text AS detail, occurred_at
        FROM audit_log WHERE target_id = ?
        """,
        targetId);
  }

  private UUID apiKeyIdOf(String authorization) {
    return jdbcTemplate.queryForObject(
        "SELECT id FROM api_key WHERE key_hash = sha256(convert_to(?, 'UTF8'))",
        UUID.class,
        TestCredentials.rawKeyOf(authorization));
  }

  private UUID insertOrganization() {
    return jdbcTemplate.queryForObject(
        "INSERT INTO organization (name) VALUES ('감사 테스트 도입사') RETURNING id", UUID.class);
  }

  private UUID insertOrganizationWithBillableMetric() {
    UUID organizationId = insertOrganization();
    jdbcTemplate.update(
        """
        INSERT INTO billable_metric
          (organization_id, code, name, event_type, aggregation, target_property,
           dimension_properties)
        VALUES (?, ?, '입력 토큰', 'chat_completion', 'sum', 'token', '{model}')
        """,
        organizationId,
        BILLABLE_METRIC_CODE);
    return organizationId;
  }

  private void insertPriceRate(UUID organizationId) {
    jdbcTemplate.update(
        """
        INSERT INTO price_rate (organization_id, billable_metric_code, dimension_values, unit_price)
        VALUES (?, ?, '{"model": "opus"}', 0.5)
        """,
        organizationId,
        BILLABLE_METRIC_CODE);
  }

  private UUID insertCustomer(UUID organizationId, String name) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO customer (organization_id, name) VALUES (?, ?) RETURNING id",
        UUID.class,
        organizationId,
        name);
  }

  private void insertEvent(UUID organizationId, String transactionId, UUID customerId, int token) {
    OffsetDateTime occurredAt = OffsetDateTime.parse("2026-08-10T12:00:00+09:00");
    TestEvents.insert(
        jdbcTemplate,
        organizationId,
        transactionId,
        customerId,
        "chat_completion",
        "{\"model\":\"opus\",\"token\":%d}".formatted(token),
        occurredAt,
        occurredAt);
  }
}
