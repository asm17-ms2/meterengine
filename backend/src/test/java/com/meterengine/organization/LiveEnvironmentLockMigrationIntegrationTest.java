package com.meterengine.organization;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

class LiveEnvironmentLockMigrationIntegrationTest {

  private static final Pattern MIGRATION_FILE = Pattern.compile("V([0-9]+)(_[0-9]+)?__.*\\.sql");

  private static final String LOCK_MIGRATION = "V57__lock_live_environment_until_approved.sql";

  @Test
  void V57은_쓰던_조직의_실제_환경을_열어_두고_승인_대기_조직은_active로_바꿔_잠근다() throws Exception {
    Path migrations = Files.createTempDirectory("meterengine-live-lock-upgrade-");
    try (var postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:18"))) {
      copyMigrations(migrations, 56);
      postgres.start();
      Flyway before = flyway(postgres, migrations);
      before.migrate();
      assertThat(before.info().current().getVersion().toString()).isEqualTo("56");
      try (Connection connection =
          DriverManager.getConnection(
              postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
        Map<String, UUID> organizations = new LinkedHashMap<>();
        for (String status : new String[] {"pending", "active", "rejected", "suspended"}) {
          UUID id = UUID.randomUUID();
          organizations.put(status, id);
          execute(
              connection,
              "INSERT INTO organization(id,name,status) VALUES (?,?,?)",
              id,
              status,
              status);
        }

        Files.copy(
            Path.of("src/main/resources/db/migration", LOCK_MIGRATION),
            migrations.resolve(LOCK_MIGRATION));
        Flyway after = flyway(postgres, migrations);
        assertThat(after.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(after.validateWithResult().validationSuccessful).isTrue();

        assertThat(state(connection, organizations.get("pending"))).isEqualTo("active locked");
        assertThat(state(connection, organizations.get("active"))).isEqualTo("active open");
        assertThat(state(connection, organizations.get("suspended"))).isEqualTo("suspended open");
        assertThat(state(connection, organizations.get("rejected"))).isEqualTo("rejected locked");

        UUID inserted = UUID.randomUUID();
        execute(connection, "INSERT INTO organization(id,name) VALUES (?,'SQL로 넣은 조직')", inserted);
        assertThat(state(connection, inserted)).isEqualTo("active open");
      }
    } finally {
      try (var paths = Files.walk(migrations)) {
        for (Path path : paths.sorted(Comparator.reverseOrder()).toList())
          Files.deleteIfExists(path);
      }
    }
  }

  private static String state(Connection connection, UUID organizationId) throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(
            "SELECT status || CASE WHEN live_environment_opened_at IS NULL THEN ' locked' ELSE ' open' END FROM organization WHERE id = ?")) {
      statement.setObject(1, organizationId);
      try (var result = statement.executeQuery()) {
        result.next();
        return result.getString(1);
      }
    }
  }

  private static Flyway flyway(PostgreSQLContainer postgres, Path migrations) {
    return Flyway.configure()
        .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
        .locations("filesystem:" + migrations.toAbsolutePath())
        .load();
  }

  private static void copyMigrations(Path target, int maximum) throws Exception {
    try (var paths = Files.list(Path.of("src/main/resources/db/migration"))) {
      for (Path path : paths.toList()) {
        Matcher matcher = MIGRATION_FILE.matcher(path.getFileName().toString());
        if (matcher.matches() && Integer.parseInt(matcher.group(1)) <= maximum)
          Files.copy(path, target.resolve(path.getFileName()));
      }
    }
  }

  private static void execute(Connection connection, String sql, Object... values)
      throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      for (int index = 0; index < values.length; index++)
        statement.setObject(index + 1, values[index]);
      statement.executeUpdate();
    }
  }
}
