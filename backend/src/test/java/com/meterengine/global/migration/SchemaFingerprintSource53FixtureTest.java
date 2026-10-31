package com.meterengine.global.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.DriverManager;
import java.util.HexFormat;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class SchemaFingerprintSource53FixtureTest {
  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:18.4-alpine").withDatabaseName("source53_fixture");

  @Test
  void 고정한_원문55개로_V53을_재현하고_NOT_VALID와_원본을_보존한다() throws Exception {
    var provenance =
        (Map<?, ?>)
            SchemaFingerprint.parse(SchemaFingerprint.resource("/db/legacy53/provenance.json"));
    assertThat(provenance.get("source_commit"))
        .isEqualTo("7889d845c9f908528a156c3f5d363e14565e9582");
    assertThat(provenance.get("flyway_version")).isEqualTo("53");
    var files = (Map<?, ?>) provenance.get("files");
    assertThat(files).hasSize(55);
    for (var entry : files.entrySet()) {
      try (var stream = getClass().getResourceAsStream("/db/legacy53/" + entry.getKey())) {
        assertThat(stream).isNotNull();
        assertThat(
                HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes())))
            .isEqualTo(((Map<?, ?>) entry.getValue()).get("sha256"));
      }
    }
    var flyway =
        Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("classpath:db/legacy53")
            .load();
    assertThat(flyway.migrate().migrationsExecuted).isEqualTo(55);
    assertThat(flyway.migrate().migrationsExecuted).isZero();
    flyway.validate();
    try (var c =
        DriverManager.getConnection(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
      assertThat(SchemaMigratorInitialSchemaIntegrationTest.scalar(c, "SHOW server_version_num"))
          .isEqualTo("180004");
      assertThat(
              SchemaMigratorInitialSchemaIntegrationTest.scalar(
                  c,
                  "SELECT count(*) FROM public.flyway_schema_history WHERE success AND version IS NOT NULL"))
          .isEqualTo("54");
      assertThat(
              SchemaMigratorInitialSchemaIntegrationTest.scalar(
                  c,
                  "SELECT version FROM public.flyway_schema_history WHERE success AND version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1"))
          .isEqualTo("53");
      assertThat(
              SchemaMigratorInitialSchemaIntegrationTest.scalar(
                  c,
                  "SELECT count(*) FROM pg_constraint WHERE conrelid='public.webhook_delivery'::regclass AND conname='webhook_delivery_failure_reason_check' AND NOT convalidated AND conenforced AND pg_get_constraintdef(oid) LIKE '%NOT VALID'"))
          .isEqualTo("1");
      String hash = SchemaFingerprint.catalogHash(c, "public");
      Files.writeString(Path.of("build/r2-source53-catalog.sha256"), hash);
      c.setReadOnly(true);
      c.setAutoCommit(false);
      c.createStatement().execute("SET LOCAL search_path=pg_catalog");
      try (var statement =
          c.prepareStatement(SchemaFingerprint.resource("/db/schema-fingerprint.sql"))) {
        statement.setString(1, "public");
        try (var result = statement.executeQuery()) {
          result.next();
          Files.writeString(
              Path.of("build/r2-source53-catalog.json"),
              SchemaFingerprint.canonical(SchemaFingerprint.parse(result.getString(1))));
        }
      } finally {
        c.rollback();
        c.setAutoCommit(true);
        c.setReadOnly(false);
      }
      assertThatThrownBy(
              () ->
                  new SchemaMigrator(
                          new DriverManagerDataSource(
                              POSTGRES.getJdbcUrl(),
                              POSTGRES.getUsername(),
                              POSTGRES.getPassword()))
                      .migrate())
          .isInstanceOf(IllegalArgumentException.class);
      assertThat(SchemaFingerprint.catalogHash(c, "public")).isEqualTo(hash);
      assertThat(
              SchemaMigratorInitialSchemaIntegrationTest.scalar(
                  c,
                  "SELECT count(*) FROM public.flyway_schema_history WHERE success AND type IN ('SQL','SQL_BASELINE')"))
          .isEqualTo("55");
      var golden =
          (Map<?, ?>)
              SchemaFingerprint.parse(SchemaFingerprint.resource("/db/schema-golden.v2.json"));
      assertThat(hash)
          .isEqualTo(
              ((Map<?, ?>) ((Map<?, ?>) golden.get("sources")).get("source53"))
                  .get("schema_sha256"));
    }
  }
}
