package com.meterengine.global.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meterengine.payment.client.TossPaymentsClient;
import com.meterengine.webhook.client.WebhookClient;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(
    classes = ReadOnlyEnvironmentTestConfig.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.docker.compose.enabled=false",
      "meterengine.scheduling.enabled=true",
      "meterengine.webhook.enabled=true",
      "meterengine.auth-rate-limit.enabled=true",
      "METERENGINE_DEPLOYMENT_ENVIRONMENT=test",
      "METERENGINE_CUTOVER_MODE=read_only",
      "meterengine.bootstrap.operator-email=fixture@example.com",
      "meterengine.bootstrap.operator-password=fixture-password",
      "meterengine.bootstrap.frontend-api-key=me_test_AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
      "meterengine.bootstrap.frontend-organization-id=11111111-1111-1111-1111-111111111111",
      "crypto.encryption-key=AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=",
      "management.endpoint.health.probes.enabled=true",
      "tosspayments.base-url=http://127.0.0.1:1"
    })
@org.junit.jupiter.api.TestMethodOrder(org.junit.jupiter.api.MethodOrderer.OrderAnnotation.class)
class ReadOnlyDatabaseVerifierIntegrationTest {
  @LocalServerPort int port;
  @Autowired PostgreSQLContainer environmentPostgres;
  @Autowired CutoverPolicy policy;
  @Autowired TossPaymentsClient toss;
  @Autowired WebhookClient webhook;
  @Autowired org.springframework.context.ApplicationContext context;
  @Autowired com.meterengine.scheduling.service.ScheduledJobRunService jobs;
  @Autowired com.meterengine.payment.config.PaymentJobScheduler paymentJobs;
  @Autowired com.meterengine.webhook.config.WebhookDeliveryConfig webhookJobs;
  @Autowired com.meterengine.notification.config.UnbilledUsageNotificationConfig notificationJobs;
  @Autowired com.meterengine.audit.service.AuditLogService audit;

  @Test
  @org.junit.jupiter.api.Order(1)
  void 초기_읽기역할의_기동과_HTTP와_모든_예약과_직접경계는_행과_외부효과를_남기지_않는다() throws Exception {
    assertThat(policy.readOnly()).isTrue();
    var owner = owner();
    assertThat(owner.queryForObject("SELECT count(*) FROM shared.user_account", Long.class))
        .isZero();
    assertThat(owner.queryForObject("SELECT count(*) FROM shared.api_key", Long.class)).isZero();
    var before = snapshot(owner);
    assertThatThrownBy(
            () ->
                DatabaseScopes.call(
                    DatabaseScope.TEST,
                    () ->
                        context
                            .getBean(com.meterengine.customer.service.CustomerService.class)
                            .create(java.util.UUID.randomUUID(), "blocked customer", null)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("read_only");
    stimulate();
    assertThat(snapshot(owner)).isEqualTo(before);
    for (var scope : DatabaseScope.values()) {
      var source = runtime(scope);
      ReadOnlyDatabaseVerifier.verify(source, scope);
      try (var connection = source.getConnection()) {
        connection.createStatement().execute("SET default_transaction_read_only=off");
        String sql =
            scope == DatabaseScope.SHARED
                ? "INSERT INTO shared.auth_request_rate_limit(scope,identity_hash,attempts,expires_at) VALUES ('login_ip',decode(repeat('00',32),'hex'),1,now())"
                : "INSERT INTO "
                    + scope.name().toLowerCase(java.util.Locale.ROOT)
                    + ".customer(organization_id,name) VALUES (gen_random_uuid(),'fixture')";
        assertThatThrownBy(() -> connection.createStatement().execute(sql))
            .isInstanceOf(java.sql.SQLException.class)
            .extracting(error -> ((java.sql.SQLException) error).getSQLState())
            .isEqualTo("42501");
      }
    }
    assertThat(snapshot(owner)).isEqualTo(before);
  }

  @Test
  @org.junit.jupiter.api.Order(2)
  void 테이블과_열과_시퀀스와_공개함수와_역할과_소유와_DDL_쓰기우회를_기동전에_거절한다() {
    var owner = owner();
    var target = runtime(DatabaseScope.TEST);
    for (var grant :
        List.of(
            List.of(
                "GRANT UPDATE ON test.customer TO meterengine_test",
                "REVOKE UPDATE ON test.customer FROM meterengine_test"),
            List.of(
                "GRANT UPDATE(id) ON test.invoice TO meterengine_test",
                "REVOKE UPDATE(id) ON test.invoice FROM meterengine_test"),
            List.of(
                "GRANT USAGE ON SEQUENCE test.invoice_display_number_seq TO meterengine_test",
                "REVOKE USAGE ON SEQUENCE test.invoice_display_number_seq FROM meterengine_test"),
            List.of(
                "GRANT EXECUTE ON FUNCTION shared.claim_job(uuid,varchar,timestamptz,timestamptz) TO PUBLIC",
                "REVOKE EXECUTE ON FUNCTION shared.claim_job(uuid,varchar,timestamptz,timestamptz) FROM PUBLIC"),
            List.of(
                "GRANT meterengine_shared TO meterengine_test",
                "REVOKE meterengine_shared FROM meterengine_test"),
            List.of(
                "GRANT CREATE ON SCHEMA test TO meterengine_test",
                "REVOKE CREATE ON SCHEMA test FROM meterengine_test"),
            List.of(
                "GRANT TEMP ON DATABASE meterengine_app TO meterengine_test",
                "REVOKE TEMP ON DATABASE meterengine_app FROM meterengine_test"),
            List.of(
                "ALTER TABLE test.customer OWNER TO meterengine_test",
                "ALTER TABLE test.customer OWNER TO "
                    + environmentPostgres.getUsername()
                    + "; GRANT SELECT ON test.customer TO meterengine_test"))) {
      owner.execute(grant.getFirst());
      try {
        assertThatThrownBy(() -> ReadOnlyDatabaseVerifier.verify(target, DatabaseScope.TEST))
            .as(grant.getFirst())
            .isInstanceOf(IllegalStateException.class);
      } finally {
        owner.execute(grant.getLast());
      }
      ReadOnlyDatabaseVerifier.verify(target, DatabaseScope.TEST);
    }
  }

  @Test
  @org.junit.jupiter.api.Order(3)
  void 활성화_뒤에도_기존_읽기프로세스는_고정되고_새_읽기기동의_권한검사는_거절한다() throws Exception {
    var owner = owner();
    owner.execute(
        new String(
            getClass().getResourceAsStream("/db/activate-target.sql").readAllBytes(),
            java.nio.charset.StandardCharsets.UTF_8));
    var before = snapshot(owner);
    assertThatThrownBy(
            () ->
                DatabaseScopes.call(
                    DatabaseScope.TEST,
                    () ->
                        context
                            .getBean(com.meterengine.customer.service.CustomerService.class)
                            .create(java.util.UUID.randomUUID(), "blocked customer", null)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("read_only");
    stimulate();
    assertThat(snapshot(owner)).isEqualTo(before);
    for (var scope : DatabaseScope.values())
      assertThatThrownBy(() -> ReadOnlyDatabaseVerifier.verify(runtime(scope), scope))
          .isInstanceOf(IllegalStateException.class);
    assertThat(policy.readOnly()).isTrue();
    ReadOnlyRestartConfig.postgres = environmentPostgres;
    assertThatThrownBy(
            () ->
                new org.springframework.boot.builder.SpringApplicationBuilder(
                        ReadOnlyRestartConfig.class)
                    .run(
                        "--server.port=0",
                        "--spring.docker.compose.enabled=false",
                        "--METERENGINE_DEPLOYMENT_ENVIRONMENT=test",
                        "--METERENGINE_CUTOVER_MODE=read_only",
                        "--crypto.encryption-key=AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE="))
        .isInstanceOf(RuntimeException.class)
        .hasStackTraceContaining("read_only requires initial SELECT-only runtime privileges");
    assertThat(snapshot(owner)).isEqualTo(before);
    ActiveRestartConfig.postgres = environmentPostgres;
    try (var active =
        new org.springframework.boot.builder.SpringApplicationBuilder(ActiveRestartConfig.class)
            .run(
                "--server.port=0",
                "--spring.docker.compose.enabled=false",
                "--METERENGINE_DEPLOYMENT_ENVIRONMENT=test",
                "--METERENGINE_CUTOVER_MODE=active",
                "--meterengine.scheduling.enabled=true",
                "--meterengine.webhook.enabled=true",
                "--meterengine.auth-rate-limit.enabled=true",
                "--meterengine.bootstrap.operator-email=active-fixture@example.com",
                "--meterengine.bootstrap.operator-password=fixture-password",
                "--crypto.encryption-key=AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=",
                "--tosspayments.base-url=http://127.0.0.1:1")) {
      assertThat(active.getBean(CutoverPolicy.class).readOnly()).isFalse();
      assertThat(active.getBean(org.springframework.orm.jpa.JpaTransactionManager.class))
          .isInstanceOf(CutoverTransactions.class);
      for (var scope : List.of(DatabaseScope.TEST, DatabaseScope.LIVE)) {
        var customerId = java.util.UUID.randomUUID();
        var organizationId = java.util.UUID.randomUUID();
        owner.update(
            "INSERT INTO shared.organization(id,name) VALUES (?,'transaction fixture')",
            organizationId);
        assertThatThrownBy(
                () ->
                    active
                        .getBean(ScopeTransactions.class)
                        .write(
                            scope,
                            () -> {
                              var jdbc = active.getBean(JdbcTemplate.class);
                              var entityManager =
                                  active.getBean(jakarta.persistence.EntityManager.class);
                              long jpaPid =
                                  entityManager
                                      .unwrap(org.hibernate.Session.class)
                                      .doReturningWork(
                                          connection -> {
                                            try (var statement = connection.createStatement();
                                                var result =
                                                    statement.executeQuery(
                                                        "SELECT pg_backend_pid()")) {
                                              result.next();
                                              return result.getLong(1);
                                            }
                                          });
                              assertThat(jdbc.queryForObject("SELECT pg_backend_pid()", Long.class))
                                  .isEqualTo(jpaPid);
                              assertThat(jdbc.queryForObject("SELECT current_user", String.class))
                                  .isEqualTo(
                                      "meterengine_"
                                          + scope.name().toLowerCase(java.util.Locale.ROOT));
                              active
                                  .getBean(
                                      com.meterengine.customer.repository.CustomerRepository.class)
                                  .saveAndFlush(
                                      new com.meterengine.customer.entity.Customer(
                                          customerId, organizationId, "rollback", null));
                              assertThat(
                                      jdbc.queryForObject(
                                          "SELECT count(*) FROM customer WHERE id=?",
                                          Long.class,
                                          customerId))
                                  .isEqualTo(1);
                              jdbc.update(
                                  "UPDATE customer SET name='same connection' WHERE id=?",
                                  customerId);
                              entityManager.clear();
                              assertThat(
                                      active
                                          .getBean(
                                              com.meterengine.customer.repository.CustomerRepository
                                                  .class)
                                          .findById(customerId)
                                          .orElseThrow()
                                          .getName())
                                  .isEqualTo("same connection");
                              throw new IllegalStateException("rollback fixture");
                            }))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("rollback fixture");
        for (String schema : List.of("test", "live"))
          assertThat(
                  owner.queryForObject(
                      "SELECT count(*) FROM " + schema + ".customer WHERE id=?",
                      Long.class,
                      customerId))
              .isZero();
      }

      assertThat(
              owner.queryForObject(
                  "SELECT count(*) FROM shared.user_account WHERE email='active-fixture@example.com'",
                  Long.class))
          .isEqualTo(1);
      var jdbc = active.getBean(JdbcTemplate.class);
      var organization = java.util.UUID.randomUUID();
      jdbc.update(
          "INSERT INTO shared.organization(id,name) VALUES (?,'active restart')", organization);
      active
          .getBean(ScopeTransactions.class)
          .write(
              DatabaseScope.TEST,
              () -> {
                jdbc.update(
                    "INSERT INTO customer(organization_id,name) VALUES (?,'active runtime')",
                    organization);
                return null;
              });
      assertThat(
              owner.queryForObject(
                  "SELECT count(*) FROM test.customer WHERE organization_id=?",
                  Long.class,
                  organization))
          .isEqualTo(1);
      assertThat(
              owner.queryForObject(
                  "SELECT count(*) FROM live.customer WHERE organization_id=?",
                  Long.class,
                  organization))
          .isZero();
      String valid =
          active
              .getBean(com.meterengine.account.service.SessionService.class)
              .create("active-fixture@example.com", "fixture-password")
              .token();
      var activeRows = snapshot(owner);
      var authenticated =
          HttpClient.newHttpClient()
              .send(
                  HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v1/operations"))
                      .header("Authorization", "Bearer " + valid)
                      .build(),
                  HttpResponse.BodyHandlers.ofString());
      assertThat(authenticated.statusCode()).as(authenticated.body()).isEqualTo(503);
      stimulate();
      assertThat(snapshot(owner)).isEqualTo(activeRows);
    }
  }

  @org.springframework.context.annotation.Configuration
  @org.springframework.boot.test.context.TestComponent
  static class ActiveRestartConfig extends SideEffectEnvironmentTestConfig {
    static PostgreSQLContainer postgres;

    @Override
    @org.springframework.context.annotation.Bean(destroyMethod = "")
    PostgreSQLContainer environmentPostgres() {
      return new ExistingPostgres(postgres);
    }
  }

  @org.springframework.context.annotation.Configuration
  @org.springframework.boot.test.context.TestComponent
  static class ReadOnlyRestartConfig extends ReadOnlyEnvironmentTestConfig {
    static PostgreSQLContainer postgres;

    @Override
    @org.springframework.context.annotation.Bean(destroyMethod = "")
    PostgreSQLContainer environmentPostgres() {
      return new ExistingPostgres(postgres);
    }
  }

  static class ExistingPostgres extends PostgreSQLContainer {
    private final PostgreSQLContainer existing;

    ExistingPostgres(PostgreSQLContainer existing) {
      super("postgres:18.4-alpine");
      this.existing = existing;
    }

    @Override
    public void start() {}

    @Override
    public void stop() {}

    @Override
    public String getJdbcUrl() {
      return existing.getJdbcUrl();
    }

    @Override
    public String getUsername() {
      return existing.getUsername();
    }

    @Override
    public String getPassword() {
      return existing.getPassword();
    }
  }

  private void stimulate() throws Exception {
    var client = HttpClient.newHttpClient();
    for (String path :
        List.of(
            "/v1/sessions",
            "/v1/signups",
            "/v1/customers",
            "/v1/public/invoice-links/lookup",
            "/v1/payment-method-registrations/unknown",
            "/v1/toss-payments-webhooks/11111111-1111-1111-1111-111111111111",
            "/actuator/prometheus",
            "/v3/api-docs",
            "/error",
            "/invalid")) {
      for (String method : List.of("GET", "POST", "PUT", "DELETE")) {
        var request =
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Authorization", "Bearer invalid")
                .method(method, HttpRequest.BodyPublishers.noBody())
                .build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode())
            .as(method + " " + path + " " + response.body())
            .isEqualTo(503);
        assertThat(response.body()).contains("read_only");
      }
    }
    for (String path : List.of("/actuator/health", "/actuator/health/readiness")) {
      var response =
          client.send(
              HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build(),
              HttpResponse.BodyHandlers.ofString());
      assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    }
    paymentJobs.register();
    paymentJobs.confirm();
    paymentJobs.retry();
    paymentJobs.reconcile();
    webhookJobs.register();
    webhookJobs.deliver();
    notificationJobs.register();
    notificationJobs.report();
    jobs.run(
        "fixture",
        OffsetDateTime.now(),
        () -> {
          throw new AssertionError("read_only job executed");
        });
    context
        .getBean(com.meterengine.payment.service.PaymentAttemptConfirmationService.class)
        .confirm();
    context.getBean(com.meterengine.payment.service.PaymentRetryService.class).retry();
    context
        .getBean(com.meterengine.payment.service.PaymentReconciliationService.class)
        .reconcile(OffsetDateTime.now());
    context.getBean(com.meterengine.webhook.service.WebhookDeliveryService.class).deliver();
    context
        .getBean(com.meterengine.notification.service.UnbilledUsageNotificationService.class)
        .report();
    audit.record("fixture", "fixture", "fixture", java.util.Map.of());
    assertThatThrownBy(
            () ->
                context
                    .getBean(ScopeTransactions.class)
                    .read(
                        DatabaseScope.TEST,
                        () ->
                            context
                                .getBean(com.meterengine.customer.service.CustomerService.class)
                                .create(
                                    java.util.UUID.randomUUID(),
                                    "blocked joined transaction",
                                    null)))
        .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class)
        .hasMessageContaining("read-only");

    var id = java.util.UUID.randomUUID();
    assertThatThrownBy(
            () ->
                context
                    .getBean(com.meterengine.payment.service.PaymentAttemptService.class)
                    .create(id, id, "test", null))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(
            () ->
                context
                    .getBean(com.meterengine.payment.service.PaymentMethodRegistrationService.class)
                    .create(id, id))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(
            () ->
                context
                    .getBean(com.meterengine.payment.service.BillingKeyService.class)
                    .create("fixture", null))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(
            () ->
                context
                    .getBean(com.meterengine.account.service.SessionService.class)
                    .create("fixture@example.com", "fixture"))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(
            () ->
                context
                    .getBean(com.meterengine.account.service.AuthRequestRateLimitService.class)
                    .login(
                        "fixture@example.com",
                        new org.springframework.mock.web.MockHttpServletRequest()))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(
            () ->
                context
                    .getBean(com.meterengine.webhook.service.WebhookEndpointService.class)
                    .create(
                        id,
                        new com.meterengine.webhook.dto.CreateWebhookEndpointRequest(
                            "http://127.0.0.1:1/", List.of("payment_succeeded"), "fixture")))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> toss.findPayment("test_sk_fixture", "fixture"))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> toss.findPaymentByOrderId("live_sk_fixture", "fixture"))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> toss.issueBillingKey("test_sk_fixture", "fixture", "fixture"))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> toss.deleteBillingKey("test_sk_fixture", "fixture"))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(
            () -> toss.approveBilling("test_sk_fixture", "fixture", null, "fixture", null))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> toss.cancel("test_sk_fixture", "fixture", null, "fixture"))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(
            () ->
                toss.listTransactions(
                    "test_sk_fixture",
                    java.time.LocalDateTime.now(),
                    java.time.LocalDateTime.now(),
                    null,
                    1))
        .isInstanceOf(IllegalStateException.class);
    assertThat(webhook.send("http://127.0.0.1:1/", "fixture", "1", "fixture", "{}").failureReason())
        .isEqualTo("blocked_address");
    var requestAuditFailures =
        context
            .getBean(io.micrometer.core.instrument.MeterRegistry.class)
            .find("meterengine.request.audit.failures")
            .counter();
    assertThat(requestAuditFailures).isNotNull();
    assertThat(requestAuditFailures.count()).isZero();
    assertThat(
            owner()
                .queryForObject("SELECT count(*) FROM shared.auth_request_rate_limit", Long.class))
        .isZero();
  }

  private DriverManagerDataSource runtime(DatabaseScope scope) {
    String name = scope.name().toLowerCase(java.util.Locale.ROOT);
    String url = environmentPostgres.getJdbcUrl().replace("/postgres", "/meterengine_app");
    return new DriverManagerDataSource(
        url + (url.contains("?") ? "&" : "?") + "currentSchema=" + name,
        "meterengine_" + name,
        "fixture-password");
  }

  private JdbcTemplate owner() {
    return new JdbcTemplate(
        new DriverManagerDataSource(
            environmentPostgres.getJdbcUrl().replace("/postgres", "/meterengine_app"),
            environmentPostgres.getUsername(),
            environmentPostgres.getPassword()));
  }

  private String snapshot(JdbcTemplate owner) {
    var rows = new java.util.ArrayList<String>();
    for (String table :
        owner.queryForList(
            "SELECT quote_ident(n.nspname)||'.'||quote_ident(c.relname) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname IN ('shared','test','live') AND c.relkind IN ('r','p') ORDER BY 1",
            String.class))
      rows.add(
          table
              + owner.queryForList(
                  "SELECT row_to_json(t)::text FROM " + table + " t ORDER BY row_to_json(t)::text",
                  String.class));
    return rows.toString();
  }
}
