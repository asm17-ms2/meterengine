package com.meterengine.global.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class SchemaMigratorInitialSchemaIntegrationTest {

  static int firstMigrationCount;

  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:18.4-alpine").withDatabaseName("meterengine_app");

  @BeforeAll
  static void prepareRoles() throws Exception {
    try (var c = owner()) {
      for (var scope : new String[] {"shared", "test", "live"}) {
        c.createStatement()
            .execute(
                "CREATE ROLE meterengine_"
                    + scope
                    + " LOGIN PASSWORD 'fixture-password' NOINHERIT");
      }
    }
    firstMigrationCount = flyway().migrate().migrationsExecuted;
  }

  static Connection owner() throws SQLException {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
  }

  static Flyway flyway() {
    return Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .defaultSchema("shared")
        .schemas("shared")
        .locations("classpath:db/migration")
        .load();
  }

  @Test
  void 빈_DB에는_압축한_세_마이그레이션만_적용하고_재실행은_변경하지_않는다() throws Exception {
    try (var connection =
        DriverManager.getConnection(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
      assertThat(connection.createStatement().executeQuery("SHOW server_version_num").next())
          .isTrue();
    }
    var runner = flyway();
    assertThat(firstMigrationCount).isEqualTo(3);
    assertThat(runner.migrate().migrationsExecuted).isZero();
    runner.validate();
  }

  @Test
  void 업무_환경_열을_없애고_조직_FK와_금액_정밀도와_보호_트리거를_보존한다() throws Exception {
    try (var c = owner()) {
      assertThat(scalar(c, "SELECT current_setting('server_version_num')")).isEqualTo("180004");
      assertThat(
              scalar(
                  c,
                  "SELECT count(*) FROM information_schema.tables WHERE table_schema IN"
                      + " ('test','live') AND table_type='BASE TABLE'"))
          .isEqualTo("54");
      assertThat(
              scalar(
                  c,
                  "SELECT count(*) FROM information_schema.columns WHERE table_schema IN"
                      + " ('test','live') AND column_name IN"
                      + " ('environment_id','environment_scope_id','environment_scope')"))
          .isEqualTo("0");
      assertThat(
              scalar(
                  c,
                  "SELECT count(*) FROM pg_constraint WHERE connamespace IN"
                      + " ('test'::regnamespace,'live'::regnamespace) AND"
                      + " confrelid='shared.organization'::regclass"))
          .isEqualTo("36");
      assertThat(
              scalar(
                  c,
                  "SELECT count(*) FROM information_schema.columns WHERE table_schema IN"
                      + " ('test','live') AND column_name IN"
                      + " ('quantity','unit_price','unit_size','percentile','quantity_numerator','quantity_denominator')"
                      + " AND data_type='numeric' AND numeric_precision IS NOT NULL"))
          .isEqualTo("0");
      assertThat(
              scalar(
                  c,
                  "SELECT count(*) FROM pg_constraint WHERE connamespace IN"
                      + " ('shared'::regnamespace,'test'::regnamespace,'live'::regnamespace) AND"
                      + " NOT convalidated"))
          .isEqualTo("0");
      String hash = SchemaFingerprint.catalogHash(c);
      c.setReadOnly(true);
      c.setAutoCommit(false);
      c.createStatement().execute("SET LOCAL search_path=pg_catalog");
      try (var statement =
          c.prepareStatement(SchemaFingerprint.resource("/db/schema-fingerprint.sql"))) {
        statement.setString(1, "shared");
        try (var rs = statement.executeQuery()) {
          rs.next();
          Files.writeString(
              Path.of("build/s1-target-catalog.json"),
              SchemaFingerprint.canonical(SchemaFingerprint.parse(rs.getString(1))));
        }
      } finally {
        c.rollback();
        c.setAutoCommit(true);
        c.setReadOnly(false);
      }
      Files.writeString(Path.of("build/s1-target-catalog.sha256"), hash);
      assertThat(hash).isEqualTo(SchemaGolden.loadTargetHash());
    }
  }

  static String scalar(Connection c, String sql) throws SQLException {
    try (var rs = c.createStatement().executeQuery(sql)) {
      rs.next();
      return rs.getString(1);
    }
  }

  @Test
  void 초기_역할은_자기_업무만_읽고_쓰기와_DDL을_거절한다() throws Exception {
    flyway().migrate();
    for (var scope : new String[] {"test", "live"}) {
      try (var c =
          DriverManager.getConnection(
              POSTGRES.getJdbcUrl(), "meterengine_" + scope, "fixture-password")) {
        try (var result = c.createStatement().executeQuery("SELECT shared.runtime_scope()")) {
          result.next();
          assertThat(result.getString(1)).isEqualTo(scope);
        }
        c.createStatement().executeQuery("SELECT * FROM " + scope + ".customer").close();
        denied(c, "SELECT * FROM " + (scope.equals("test") ? "live" : "test") + ".customer");
        denied(
            c,
            "INSERT INTO "
                + scope
                + ".customer (organization_id,name) VALUES (gen_random_uuid(),'x')");
        denied(c, "CREATE TABLE public.forbidden(id int)");
        denied(c, "CREATE TABLE " + scope + ".forbidden(id int)");
        denied(c, "CREATE TEMP TABLE forbidden(id int)");
        denied(c, "SET ROLE " + POSTGRES.getUsername());
        denied(c, "SELECT * FROM shared.user_account");
        denied(c, "SELECT * FROM shared.legacy_import");
        denied(c, "SELECT shared.claim_job(gen_random_uuid(),'payment_retry',now(),now())");
        denied(c, "DELETE FROM " + scope + ".invoice");
        denied(c, "TRUNCATE " + scope + ".invoice");
      }
    }
    try (var c =
        DriverManager.getConnection(
            POSTGRES.getJdbcUrl(), "meterengine_shared", "fixture-password")) {
      c.createStatement().executeQuery("SELECT * FROM shared.user_account").close();
      c.createStatement().executeQuery("SELECT * FROM shared.invoice_view_link_route").close();
      denied(c, "SELECT * FROM test.customer");
      denied(c, "INSERT INTO shared.organization(name) VALUES ('forbidden')");
      denied(c, "SELECT * FROM shared.legacy_import");
    }
  }

  static void denied(Connection c, String sql) {
    assertThatThrownBy(() -> c.createStatement().execute(sql))
        .isInstanceOf(SQLException.class)
        .satisfies(error -> assertThat(((SQLException) error).getSQLState()).isEqualTo("42501"));
  }

  @Test
  void 양_업무의_가격_조회_인덱스는_조직과_지표_순서이며_부분_조건이_없다() throws Exception {
    try (var c = owner()) {
      for (String scope : new String[] {"test", "live"}) {
        assertThat(
                scalar(
                    c,
                    "SELECT count(*) FROM pg_index i JOIN pg_class x ON x.oid=i.indexrelid JOIN pg_namespace n ON n.oid=x.relnamespace WHERE n.nspname='"
                        + scope
                        + "' AND x.relname='price_rate_organization_index' AND NOT i.indisunique AND i.indpred IS NULL AND pg_get_indexdef(i.indexrelid)='CREATE INDEX price_rate_organization_index ON "
                        + scope
                        + ".price_rate USING btree (organization_id, billable_metric_code)'"))
            .isEqualTo("1");
      }
    }
  }

  @Test
  void 집계와_수량_CHECK는_새_이름만_남는다() throws Exception {
    try (var c = owner()) {
      for (String scope : new String[] {"test", "live"}) {
        assertThat(
                scalar(
                    c,
                    "SELECT count(*) FROM pg_constraint WHERE connamespace='"
                        + scope
                        + "'::regnamespace AND conname IN ('billable_metric_aggregation_check','billable_metric_aggregation_percentile_check','billable_metric_aggregation_target_property_check','invoice_line_quantity_fraction_check') AND contype='c' AND convalidated AND conenforced"))
            .isEqualTo("4");
        assertThat(
                scalar(
                    c,
                    "SELECT count(*) FROM pg_constraint WHERE connamespace='"
                        + scope
                        + "'::regnamespace AND conname IN ('billable_metric_supported_aggregation','billable_metric_percentile_configuration','billable_metric_target_configuration','invoice_line_quantity_fraction_pair')"))
            .isEqualTo("0");
      }
    }
  }

  @Test
  void 전송_실패_사유는_검증된_일곱_값과_NULL만_허용한다() throws Exception {
    try (var c = owner()) {
      c.setAutoCommit(false);
      try {
        String organization =
            scalar(
                c, "INSERT INTO shared.organization(name) VALUES ('failure-check') RETURNING id");
        for (String scope : new String[] {"test", "live"}) {
          assertThat(
                  scalar(
                      c,
                      "SELECT count(*) FROM pg_constraint WHERE conrelid='"
                          + scope
                          + ".webhook_delivery'::regclass AND conname='webhook_delivery_failure_reason_check' AND contype='c' AND convalidated AND conenforced"))
              .isEqualTo("1");
          String endpoint =
              scalar(
                  c,
                  "INSERT INTO "
                      + scope
                      + ".webhook_endpoint(id,organization_id,url,secret,event_types) VALUES(gen_random_uuid(),'"
                      + organization
                      + "','https://example.invalid','fixture',ARRAY['fixture']) RETURNING id");
          String event =
              scalar(
                  c,
                  "INSERT INTO "
                      + scope
                      + ".webhook_event(id,organization_id,type,payload) VALUES(gen_random_uuid(),'"
                      + organization
                      + "','fixture','{}') RETURNING id");
          String insert =
              "INSERT INTO "
                  + scope
                  + ".webhook_delivery(id,organization_id,webhook_endpoint_id,webhook_event_id,failure_reason) VALUES(gen_random_uuid(),'"
                  + organization
                  + "','"
                  + endpoint
                  + "','"
                  + event
                  + "',?)";
          try (var statement = c.prepareStatement(insert)) {
            for (String reason :
                new String[] {
                  null,
                  "timeout",
                  "connection_error",
                  "unexpected_status",
                  "blocked_address",
                  "endpoint_disabled",
                  "secret_unavailable",
                  "lease_expired"
                }) {
              var savepoint = c.setSavepoint();
              statement.setString(1, reason);
              assertThat(statement.executeUpdate()).isEqualTo(1);
              c.rollback(savepoint);
            }
            assertThat(
                    scalar(
                        c,
                        "SELECT count(*) FROM "
                            + scope
                            + ".webhook_delivery WHERE organization_id='"
                            + organization
                            + "'"))
                .isEqualTo("0");
            for (String invalid :
                new String[] {"invalid", "", "TIMEOUT", "timeout,connection_error"}) {
              var savepoint = c.setSavepoint();
              statement.setString(1, invalid);
              assertThatThrownBy(statement::executeUpdate)
                  .isInstanceOf(SQLException.class)
                  .satisfies(
                      error -> assertThat(((SQLException) error).getSQLState()).isEqualTo("23514"));
              c.rollback(savepoint);
            }
          }
        }
      } finally {
        c.rollback();
      }
    }
  }

  @Test
  void 청구_항목의_저장된_지표명은_기본값_없는_nullable_VARCHAR다() throws Exception {
    try (var c = owner()) {
      for (String scope : new String[] {"test", "live"}) {
        assertThat(
                scalar(
                    c,
                    "SELECT count(*) FROM information_schema.columns WHERE table_schema='"
                        + scope
                        + "' AND table_name='invoice_line' AND column_name='billable_metric_name' AND data_type='character varying' AND is_nullable='YES' AND column_default IS NULL AND character_maximum_length IS NULL"))
            .isEqualTo("1");
        c.setAutoCommit(false);
        try {
          String organization =
              scalar(
                  c, "INSERT INTO shared.organization(name) VALUES ('metric-name') RETURNING id");
          String customer =
              scalar(
                  c,
                  "INSERT INTO "
                      + scope
                      + ".customer(organization_id,name) VALUES('"
                      + organization
                      + "','fixture') RETURNING id");
          String invoice =
              scalar(
                  c,
                  "INSERT INTO "
                      + scope
                      + ".invoice(organization_id,customer_id,period,supply_amount,tax_amount,total_amount,finalized_at,customer_name) VALUES('"
                      + organization
                      + "','"
                      + customer
                      + "','2026-10',0,0,0,now(),'fixture') RETURNING id");
          String insert =
              "INSERT INTO "
                  + scope
                  + ".invoice_line(organization_id,invoice_id,dimension_values,quantity,unit_price,amount,line_type,billable_metric_name) VALUES('"
                  + organization
                  + "','"
                  + invoice
                  + "','{}',1,0,0,'base_fee',?) RETURNING billable_metric_name";
          try (var statement = c.prepareStatement(insert)) {
            for (String name : new String[] {null, "저장된 지표명"}) {
              var savepoint = c.setSavepoint();
              statement.setString(1, name);
              try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).isEqualTo(name);
              }
              c.rollback(savepoint);
            }
          }
        } finally {
          c.rollback();
          c.setAutoCommit(true);
        }
      }
    }
  }
}
