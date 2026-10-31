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
class SchemaFingerprintLegacyFixtureTest {

  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:18.4-alpine").withDatabaseName("legacy_fixture");

  @Test
  void 고정한_구_SQL_원문을_검증하고_최종_객체를_동일_버전에서_재현한다() throws Exception {
    var provenance =
        (Map<?, ?>)
            SchemaFingerprint.parse(SchemaFingerprint.resource("/db/legacy49/provenance.json"));
    assertThat(provenance.get("source_commit"))
        .isEqualTo("be62d9138d10f8df69bfdbad53a9b405564eff2d");
    var files = (Map<?, ?>) provenance.get("files");
    assertThat(files).hasSize(51);
    for (var entry : files.entrySet()) {
      try (var stream = getClass().getResourceAsStream("/db/legacy49/" + entry.getKey())) {
        assertThat(
                HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes())))
            .isEqualTo(((Map<?, ?>) entry.getValue()).get("sha256"));
      }
    }
    var flyway =
        Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("classpath:db/legacy49")
            .load();
    assertThat(flyway.migrate().migrationsExecuted).isEqualTo(51);
    flyway.validate();
    try (var c =
        DriverManager.getConnection(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
      assertThat(SchemaMigratorInitialSchemaIntegrationTest.scalar(c, "SHOW server_version_num"))
          .isEqualTo("180004");
      String hash = SchemaFingerprint.catalogHash(c, "public");
      var golden =
          (Map<?, ?>)
              SchemaFingerprint.parse(SchemaFingerprint.resource("/db/schema-golden.v1.json"));
      assertThat(hash).isEqualTo(golden.get("legacy_schema_sha256"));
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
                  "SELECT count(*) FROM public.flyway_schema_history WHERE success AND type IN"
                      + " ('SQL','SQL_BASELINE')"))
          .isEqualTo("51");
      Files.writeString(Path.of("build/s1-legacy-catalog.sha256"), hash);
      c.setReadOnly(true);
      c.setAutoCommit(false);
      c.createStatement().execute("SET LOCAL search_path=pg_catalog");
      try (var statement =
          c.prepareStatement(SchemaFingerprint.resource("/db/schema-fingerprint.sql"))) {
        statement.setString(1, "public");
        try (var rs = statement.executeQuery()) {
          rs.next();
          Files.writeString(
              Path.of("build/s1-legacy-catalog.json"),
              SchemaFingerprint.canonical(SchemaFingerprint.parse(rs.getString(1))));
        }
      } finally {
        c.rollback();
      }
    }
  }
}
