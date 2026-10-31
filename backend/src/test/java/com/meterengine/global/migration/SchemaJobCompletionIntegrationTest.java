package com.meterengine.global.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class SchemaJobCompletionIntegrationTest {
  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:18.4-alpine").withDatabaseName("meterengine_app");

  @BeforeAll
  static void prepare() throws Exception {
    try (var c = owner()) {
      for (String scope : new String[] {"shared", "test", "live"}) {
        c.createStatement()
            .execute(
                "CREATE ROLE meterengine_"
                    + scope
                    + " LOGIN PASSWORD 'fixture-password' NOINHERIT");
      }
    }
    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .defaultSchema("shared")
        .schemas("shared")
        .locations("classpath:db/migration")
        .load()
        .migrate();
    try (var c = owner()) {
      c.createStatement()
          .execute(
              """
              INSERT INTO shared.scheduled_job_run(id,scope,job_name,scheduled_at,started_at,status,finished_at)
              VALUES
              (gen_random_uuid(),'test','completion_fixture','2026-10-30 01:00Z','2026-10-30 01:00Z','succeeded','2026-10-30 01:01Z'),
              (gen_random_uuid(),'test','completion_fixture','2026-10-30 02:00Z','2026-10-30 02:00Z','failed','2026-10-30 02:01Z'),
              (gen_random_uuid(),'live','completion_fixture','2026-10-30 03:00Z','2026-10-30 03:00Z','succeeded','2026-10-30 03:01Z'),
              (gen_random_uuid(),'live','completion_fixture','2026-10-30 04:00Z','2026-10-30 04:00Z','failed','2026-10-30 04:01Z'),
              (gen_random_uuid(),'test','completion_fixture','2026-10-30 05:00Z','2026-10-30 05:00Z','running',NULL),
              (gen_random_uuid(),'test','another_fixture','2026-10-30 06:00Z','2026-10-30 06:00Z','failed','2026-10-30 06:01Z')
              """);
    }
  }

  @Test
  void 마지막_완료는_자기_scope의_실패를_포함하고_원본표_읽기_권한을_요구하지_않는다() throws Exception {
    try (var c = owner()) {
      assertThat(
              scalar(
                  c,
                  "SELECT to_regprocedure('shared.job_last_completion(character varying)') IS NOT"
                      + " NULL"))
          .isEqualTo("t");
      assertThat(
              scalar(
                  c,
                  """
                  SELECT prosecdef AND provolatile='s' AND prorettype='timestamptz'::regtype
                    AND proconfig=ARRAY['search_path=pg_catalog, pg_temp']
                  FROM pg_proc WHERE oid='shared.job_last_completion(character varying)'::regprocedure
                  """))
          .isEqualTo("t");
    }
    for (String scope : new String[] {"test", "live"}) {
      try (var c = runtime(scope)) {
        String expected = scope.equals("test") ? "2026-10-30 02:01Z" : "2026-10-30 04:01Z";
        assertThat(
                scalar(
                    c,
                    "SELECT shared.job_last_completion('completion_fixture') = timestamptz '"
                        + expected
                        + "'"))
            .isEqualTo("t");
        assertThat(scalar(c, "SELECT shared.job_last_completion('missing_fixture') IS NULL"))
            .isEqualTo("t");
        SchemaMigratorInitialSchemaIntegrationTest.denied(
            c, "SELECT * FROM shared.scheduled_job_run");
      }
    }
    try (var c = runtime("shared")) {
      SchemaMigratorInitialSchemaIntegrationTest.denied(
          c, "SELECT shared.job_last_completion('completion_fixture')");
    }
  }

  private static String scalar(Connection c, String sql) throws Exception {
    return SchemaMigratorInitialSchemaIntegrationTest.scalar(c, sql);
  }

  private static Connection owner() throws Exception {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
  }

  private static Connection runtime(String scope) throws Exception {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(), "meterengine_" + scope, "fixture-password");
  }
}
