package com.meterengine.global.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class TargetActivationIntegrationTest {

  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:18.4-alpine").withDatabaseName("postgres");

  DriverManagerDataSource target;
  SchemaMigrator migrator;

  @BeforeEach
  void prepareTarget() throws Exception {
    var environment = new HashMap<String, String>();
    environment.put("DB_NAME", "meterengine_app");
    environment.put("DB_HOST", POSTGRES.getHost());
    environment.put("DB_PORT", POSTGRES.getMappedPort(5432).toString());
    environment.put("DB_SSL_ROOT_CERT", "/fixture/ca.pem");
    environment.put("DB_MIGRATION_USERNAME", POSTGRES.getUsername());
    environment.put("DB_MIGRATION_PASSWORD", POSTGRES.getPassword());
    for (var scope : new String[] {"SHARED", "TEST", "LIVE"}) {
      environment.put(
          "DB_" + scope + "_USERNAME", "meterengine_" + scope.toLowerCase(java.util.Locale.ROOT));
      environment.put("DB_" + scope + "_PASSWORD", "fixture-password");
    }
    target =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl().replace("/postgres", "/meterengine_app"),
            POSTGRES.getUsername(),
            POSTGRES.getPassword());
    try (var c = admin()) {
      DatabaseBootstrap.run(c, target, MigrationSettings.from(environment), environment);
    }
    migrator = new SchemaMigrator(target);
    assertThat(migrator.migrate()).isEqualTo(3);
    assertThat(migrator.migrate()).isZero();
  }

  @AfterEach
  void removeOwnedTarget() throws Exception {
    try (var c = admin()) {
      c.createStatement().execute("DROP DATABASE meterengine_app");
    }
  }

  static Connection admin() throws SQLException {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
  }

  Connection runtime(String scope) throws SQLException {
    return DriverManager.getConnection(target.getUrl(), "meterengine_" + scope, "fixture-password");
  }

  @Test
  void 명시한_빈_대상만_활성화하고_실제_로그인으로_identity와_불변_원장을_검증한다() throws Exception {
    migrator.activate(true, "fixture-staging", null);
    try (var c = runtime("shared")) {
      c.createStatement()
          .execute(
              "INSERT INTO shared.organization(id,name) VALUES"
                  + " ('00000000-0000-0000-0000-000000000001','fixture')");
    }
    for (var scope : new String[] {"test", "live"}) {
      try (var c = runtime(scope)) {
        c.createStatement()
            .execute(
                "INSERT INTO customer(id,organization_id,name,external_id) VALUES"
                    + " ('00000000-0000-0000-0000-000000000002','00000000-0000-0000-0000-000000000001','fixture','same-external-id')");
        assertThat(
                SchemaMigratorInitialSchemaIntegrationTest.scalar(
                    c,
                    "INSERT INTO"
                        + " invoice(id,organization_id,customer_id,period,supply_amount,tax_amount,total_amount,customer_name,finalized_at)"
                        + " VALUES"
                        + " ('00000000-0000-0000-0000-000000000003','00000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000002','2026-10',100,10,110,'fixture',now())"
                        + " RETURNING display_number"))
            .isEqualTo("1");
        c.createStatement().executeQuery("SELECT * FROM invoice FOR UPDATE").close();
        c.createStatement()
            .execute(
                "INSERT INTO"
                    + " invoice_view_link(id,organization_id,invoice_id,token_hash,mode,created_at,expires_at)"
                    + " VALUES"
                    + " (gen_random_uuid(),'00000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000003',decode(repeat('ab',32),'hex'),'"
                    + scope
                    + "',now(),now()+interval '1 day')");
        assertThatThrownBy(() -> c.createStatement().execute("UPDATE invoice SET id=id"))
            .isInstanceOf(SQLException.class)
            .hasMessageContaining("append-only");
        SchemaMigratorInitialSchemaIntegrationTest.denied(c, "DELETE FROM invoice");
        SchemaMigratorInitialSchemaIntegrationTest.denied(c, "TRUNCATE invoice");
        SchemaMigratorInitialSchemaIntegrationTest.denied(
            c, "SELECT nextval('" + scope + ".invoice_display_number_seq')");
        SchemaMigratorInitialSchemaIntegrationTest.denied(
            c, "SELECT setval('" + scope + ".invoice_display_number_seq',999)");
        c.createStatement()
            .execute(
                "INSERT INTO"
                    + " event(organization_id,transaction_id,customer_id,type,occurred_at,received_at)"
                    + " VALUES"
                    + " ('00000000-0000-0000-0000-000000000001','same-tx','00000000-0000-0000-0000-000000000002','fixture',now(),'2000-01-01')");
        assertThat(
                SchemaMigratorInitialSchemaIntegrationTest.scalar(
                    c, "SELECT received_at>'2026-01-01' FROM event"))
            .isEqualTo("t");
        assertThatThrownBy(
                () ->
                    c.createStatement()
                        .execute(
                            "INSERT INTO payment_setting(organization_id,mode,updated_at) VALUES"
                                + " ('00000000-0000-0000-0000-000000000001','"
                                + (scope.equals("test") ? "live" : "test")
                                + "',now())"))
            .isInstanceOf(SQLException.class)
            .satisfies(
                error -> assertThat(((SQLException) error).getSQLState()).isEqualTo("23514"));
        assertThat(
                SchemaMigratorInitialSchemaIntegrationTest.scalar(
                    c,
                    "SELECT"
                        + " shared.claim_job(gen_random_uuid(),'payment_retry','2026-10-30','2026-10-30')"))
            .isEqualTo("t");
        assertThat(
                SchemaMigratorInitialSchemaIntegrationTest.scalar(
                    c,
                    "SELECT"
                        + " shared.claim_job(gen_random_uuid(),'payment_retry','2026-10-30','2026-10-30')"))
            .isEqualTo("f");
      }
    }
    try (var c = runtime("shared")) {
      assertThat(
              SchemaMigratorInitialSchemaIntegrationTest.scalar(
                  c, "SELECT count(*) FROM shared.payment_order_route"))
          .isEqualTo("0");
      assertThat(
              SchemaMigratorInitialSchemaIntegrationTest.scalar(
                  c,
                  "SELECT count(*) FROM shared.invoice_view_link_route WHERE"
                      + " token_hash=decode(repeat('ab',32),'hex')"))
          .isEqualTo("2");
      assertThat(
              SchemaMigratorInitialSchemaIntegrationTest.scalar(
                  c, "SELECT count(*) FROM shared.operations_job_status"))
          .isEqualTo("10");
      SchemaMigratorInitialSchemaIntegrationTest.denied(c, "SELECT * FROM shared.legacy_import");
    }
  }

  @Test
  void 이관_보고서는_커밋된_성공_행과_현재_행_수까지_맞아야_활성화한다() throws Exception {
    Map<String, Object> report;
    try (var c = target.getConnection()) {
      report = legacyReport(c);
    }
    String json = SchemaFingerprint.canonical(report);
    assertThatThrownBy(() -> migrator.activate(false, "fixture-production", json))
        .isInstanceOf(IllegalArgumentException.class);
    var verification = (Map<?, ?>) report.get("verification");
    try (var c = target.getConnection();
        var insert =
            c.prepareStatement(
                "INSERT INTO"
                    + " shared.legacy_import(bundle_sha256,source_fingerprint_sha256,target_fingerprint_sha256,verification_sha256)"
                    + " VALUES (?,?,?,?)")) {
      insert.setString(1, (String) verification.get("bundle_sha256"));
      insert.setString(2, (String) verification.get("source_fingerprint_sha256"));
      insert.setString(3, (String) verification.get("target_fingerprint_sha256"));
      insert.setString(4, "0".repeat(64));
      insert.executeUpdate();
      assertThatThrownBy(() -> migrator.activate(false, "fixture-production", json))
          .isInstanceOf(IllegalArgumentException.class);
      c.createStatement()
          .execute(
              "UPDATE shared.legacy_import SET verification_sha256='"
                  + SchemaFingerprint.hash(verification)
                  + "'");
      c.createStatement()
          .execute("INSERT INTO shared.organization(name) VALUES ('changed-after-verification')");
      assertThatThrownBy(() -> migrator.activate(false, "fixture-production", json))
          .isInstanceOf(IllegalArgumentException.class);
      c.createStatement().execute("DELETE FROM shared.organization_environment");
      c.createStatement().execute("DELETE FROM shared.organization");
    }
    try (var c = runtime("shared")) {
      SchemaMigratorInitialSchemaIntegrationTest.denied(
          c, "INSERT INTO shared.organization(name) VALUES ('forbidden')");
    }
    migrator.activate(false, "fixture-production", json);
    try (var c = runtime("shared")) {
      c.createStatement().execute("INSERT INTO shared.organization(name) VALUES ('activated')");
    }
  }

  private Map<String, Object> legacyReport(Connection c) throws SQLException {
    var tables = new HashMap<String, Object>();
    try (var rs =
        c.createStatement()
            .executeQuery(
                "SELECT table_schema,table_name FROM information_schema.tables WHERE table_schema"
                    + " IN ('shared','test','live') AND table_type='BASE TABLE' AND table_name NOT"
                    + " IN ('flyway_schema_history','legacy_import')")) {
      while (rs.next()) {
        String table = rs.getString(1) + "." + rs.getString(2);
        long rows =
            Long.parseLong(
                SchemaMigratorInitialSchemaIntegrationTest.scalar(
                    c, "SELECT count(*) FROM " + table));
        tables.put(
            table,
            Map.of(
                "expected_rows",
                rows,
                "actual_rows",
                rows,
                "expected_sha256",
                "e".repeat(64),
                "actual_sha256",
                "e".repeat(64),
                "passed",
                true));
      }
    }
    var targetIdentity =
        Map.of(
            "identifier",
            "fixture-production",
            "database_name",
            "meterengine_app",
            "schema_fingerprint_sha256",
            SchemaGolden.loadTargetHash());
    return Map.of(
        "schema_version",
        1,
        "kind",
        "legacy_import",
        "target",
        targetIdentity,
        "verification",
        Map.of(
            "bundle_sha256",
            "a".repeat(64),
            "source_fingerprint_sha256",
            "b".repeat(64),
            "target_fingerprint_sha256",
            SchemaFingerprint.hash(targetIdentity),
            "tables",
            tables,
            "constraints",
            Map.of("passed", true),
            "triggers",
            Map.of("passed", true),
            "passed",
            true));
  }

  @Test
  void 업무_감사와_작업_함수는_로그인_scope만_쓰고_다른_scope의_완료를_거절한다() throws Exception {
    migrator.activate(true, "fixture-staging", null);
    try (var c = runtime("shared")) {
      c.createStatement()
          .execute(
              "INSERT INTO shared.organization(id,name) VALUES"
                  + " ('00000000-0000-0000-0000-000000000001','fixture')");
      c.createStatement()
          .execute(
              "INSERT INTO shared.audit_log(id,occurred_at,organization_id,actor_type,action)"
                  + " VALUES"
                  + " (gen_random_uuid(),now(),'00000000-0000-0000-0000-000000000001','system','organization_created')");
      SchemaMigratorInitialSchemaIntegrationTest.denied(
          c, "SELECT shared.lock_organization('00000000-0000-0000-0000-000000000001')");
    }
    try (var test = runtime("test");
        var live = runtime("live")) {
      test.createStatement()
          .execute(
              "SELECT"
                  + " shared.append_business_audit(gen_random_uuid(),now(),'00000000-0000-0000-0000-000000000001','system',NULL,'customer_created','customer','id','{}')");
      assertThat(
              SchemaMigratorInitialSchemaIntegrationTest.scalar(
                  test, "SELECT count(*) FROM shared.audit_for_scope"))
          .isEqualTo("2");
      assertThat(
              SchemaMigratorInitialSchemaIntegrationTest.scalar(
                  live, "SELECT count(*) FROM shared.audit_for_scope"))
          .isEqualTo("1");
      SchemaMigratorInitialSchemaIntegrationTest.denied(
          test,
          "INSERT INTO shared.audit_log(id,occurred_at,actor_type,action) VALUES"
              + " (gen_random_uuid(),now(),'system','forbidden')");
      assertThat(
              SchemaMigratorInitialSchemaIntegrationTest.scalar(
                  test,
                  "SELECT count(*) FROM"
                      + " shared.lock_organization('00000000-0000-0000-0000-000000000001')"))
          .isEqualTo("1");
      assertThat(
              SchemaMigratorInitialSchemaIntegrationTest.scalar(
                  test,
                  "SELECT count(*) FROM"
                      + " shared.lock_organization_no_key_update('00000000-0000-0000-0000-000000000001')"))
          .isEqualTo("1");
      assertThat(
              SchemaMigratorInitialSchemaIntegrationTest.scalar(
                  test,
                  "SELECT"
                      + " shared.claim_job('00000000-0000-0000-0000-000000000010','payment_retry','2026-10-30','2026-10-30')"))
          .isEqualTo("t");
      assertThat(
              SchemaMigratorInitialSchemaIntegrationTest.scalar(
                  live,
                  "SELECT"
                      + " shared.complete_job('00000000-0000-0000-0000-000000000010','succeeded',1,0,0,NULL,'2026-10-30"
                      + " 00:01Z')"))
          .isEqualTo("f");
      assertThat(
              SchemaMigratorInitialSchemaIntegrationTest.scalar(
                  test,
                  "SELECT"
                      + " shared.complete_job('00000000-0000-0000-0000-000000000010','succeeded',1,0,0,NULL,'2026-10-30"
                      + " 00:01Z')"))
          .isEqualTo("t");
      assertThat(
              SchemaMigratorInitialSchemaIntegrationTest.scalar(
                  test,
                  "SELECT"
                      + " shared.complete_job('00000000-0000-0000-0000-000000000010','failed',0,0,1,NULL,'2026-10-30"
                      + " 00:02Z')"))
          .isEqualTo("f");
      assertThat(
              SchemaMigratorInitialSchemaIntegrationTest.scalar(
                  test, "SELECT shared.job_watermark('payment_retry','2026-10-31') IS NOT NULL"))
          .isEqualTo("t");
      assertThat(
              SchemaMigratorInitialSchemaIntegrationTest.scalar(
                  live, "SELECT shared.job_watermark('payment_retry','2026-10-31') IS NULL"))
          .isEqualTo("t");
      assertThat(
              SchemaMigratorInitialSchemaIntegrationTest.scalar(
                  test, "SELECT shared.job_last_success('payment_retry') IS NOT NULL"))
          .isEqualTo("t");
      assertThat(
              SchemaMigratorInitialSchemaIntegrationTest.scalar(
                  test, "SELECT shared.job_earliest('payment_retry','2026-10-31') IS NOT NULL"))
          .isEqualTo("t");
    }
  }

  @Test
  void 새_업무_자료가_있으면_fresh_활성화를_거절하고_권한을_추가하지_않는다() throws Exception {
    try (var c = target.getConnection()) {
      c.createStatement().execute("INSERT INTO shared.organization(name) VALUES ('unexpected')");
    }
    assertThatThrownBy(() -> migrator.activate(true, "fixture-staging", null))
        .isInstanceOf(IllegalArgumentException.class);
    try (var c = runtime("shared")) {
      SchemaMigratorInitialSchemaIntegrationTest.denied(
          c, "INSERT INTO shared.organization(name) VALUES ('forbidden')");
    }
  }

  @Test
  void 구조가_바뀌거나_이관_성공_행이_없으면_활성화를_거절한다() throws Exception {
    assertThatThrownBy(() -> migrator.activate(false, "fixture-production", "{}"))
        .isInstanceOf(IllegalArgumentException.class);
    try (var c = target.getConnection()) {
      c.createStatement().execute("CREATE TABLE test.unexpected(id int)");
    }
    assertThatThrownBy(() -> migrator.activate(true, "fixture-staging", null))
        .isInstanceOf(IllegalArgumentException.class);
    try (var c = runtime("test")) {
      SchemaMigratorInitialSchemaIntegrationTest.denied(
          c,
          "INSERT INTO test.customer(organization_id,name) VALUES (gen_random_uuid(),'forbidden')");
    }
  }

  @Test
  void 추가한_standalone_composite는_fingerprint를_바꾸고_활성화를_거절한다() throws Exception {
    try (var c = target.getConnection()) {
      String original = SchemaFingerprint.catalogHash(c);
      c.createStatement().execute("CREATE TYPE public.unexpected_composite AS (value text)");
      assertThat(SchemaFingerprint.catalogHash(c)).isNotEqualTo(original);
    }
    assertThatThrownBy(() -> migrator.activate(true, "fixture-staging", null))
        .isInstanceOf(IllegalArgumentException.class);
    try (var login = runtime("test")) {
      SchemaMigratorInitialSchemaIntegrationTest.denied(
          login, "SELECT * FROM shared.legacy_import");
      SchemaMigratorInitialSchemaIntegrationTest.denied(
          login, "INSERT INTO shared.organization(name) VALUES ('forbidden')");
    }
  }

  @Test
  void public의_history와_동명인_composite를_추가하면_fingerprint가_바뀐다() throws Exception {
    try (var c = target.getConnection()) {
      String original = SchemaFingerprint.catalogHash(c);
      c.createStatement().execute("CREATE TYPE public.flyway_schema_history AS (value text)");
      assertThat(SchemaFingerprint.catalogHash(c)).isNotEqualTo(original);
    }
  }

  @Test
  void public의_history와_동명인_composite가_있으면_활성화와_권한_추가를_거절한다() throws Exception {
    try (var c = target.getConnection()) {
      c.createStatement().execute("CREATE TYPE public.flyway_schema_history AS (value text)");
    }
    assertThatThrownBy(() -> migrator.activate(true, "fixture-staging", null))
        .isInstanceOf(IllegalArgumentException.class);
    try (var login = runtime("test")) {
      SchemaMigratorInitialSchemaIntegrationTest.denied(
          login, "SELECT * FROM shared.legacy_import");
      SchemaMigratorInitialSchemaIntegrationTest.denied(
          login, "INSERT INTO shared.organization(name) VALUES ('forbidden')");
    }
  }

  @Test
  void public의_history와_동명인_테이블도_fingerprint를_바꾸고_활성화를_거절한다() throws Exception {
    try (var c = target.getConnection()) {
      String original = SchemaFingerprint.catalogHash(c);
      c.createStatement().execute("CREATE TABLE public.flyway_schema_history(value text)");
      assertThat(SchemaFingerprint.catalogHash(c)).isNotEqualTo(original);
    }
    assertThatThrownBy(() -> migrator.activate(true, "fixture-staging", null))
        .isInstanceOf(IllegalArgumentException.class);
    try (var login = runtime("test")) {
      SchemaMigratorInitialSchemaIntegrationTest.denied(
          login, "INSERT INTO shared.organization(name) VALUES ('forbidden')");
    }
  }

  @Test
  void 허용하지_않은_history_schema는_트랜잭션을_바꾸기_전에_거절한다() throws Exception {
    try (var c = target.getConnection()) {
      for (String schema : new String[] {"test", "live", "public' OR true--", "", null}) {
        assertThatThrownBy(() -> SchemaFingerprint.catalogHash(c, schema))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(c.getAutoCommit()).isTrue();
        assertThat(c.isReadOnly()).isFalse();
      }
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void 옛_target_hash의_외부_v1_보고서는_현재_target에서_거절한다() throws Exception {
    Map<String, Object> report;
    try (var c = target.getConnection()) {
      report = new HashMap<>(legacyReport(c));
    }
    var identity = new HashMap<>((Map<String, Object>) report.get("target"));
    var old =
        (Map<?, ?>)
            SchemaFingerprint.parse(SchemaFingerprint.resource("/db/schema-golden.v1.json"));
    identity.put("schema_fingerprint_sha256", old.get("target_schema_sha256"));
    report.put("target", identity);
    var verification = new HashMap<>((Map<String, Object>) report.get("verification"));
    verification.put("target_fingerprint_sha256", SchemaFingerprint.hash(identity));
    report.put("verification", verification);
    assertThat(report.get("schema_version")).isEqualTo(1);
    assertThatThrownBy(
            () ->
                migrator.activate(false, "fixture-production", SchemaFingerprint.canonical(report)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("report target mismatch");
    try (var c = runtime("shared")) {
      SchemaMigratorInitialSchemaIntegrationTest.denied(
          c, "INSERT INTO shared.organization(name) VALUES ('forbidden')");
    }
  }

  @Test
  void 새_delivery_CHECK를_지우면_실제_target과_권한_부여를_거절한다() throws Exception {
    try (var c = target.getConnection()) {
      c.createStatement()
          .execute(
              "ALTER TABLE test.webhook_delivery DROP CONSTRAINT webhook_delivery_failure_reason_check");
    }
    assertThatThrownBy(() -> migrator.activate(true, "fixture-staging", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("target schema differs from verified golden");
    try (var c = runtime("test")) {
      SchemaMigratorInitialSchemaIntegrationTest.denied(
          c,
          "INSERT INTO test.customer(organization_id,name) VALUES (gen_random_uuid(),'forbidden')");
    }
  }
}
