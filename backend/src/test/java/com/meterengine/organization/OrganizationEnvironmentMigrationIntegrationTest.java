package com.meterengine.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

class OrganizationEnvironmentMigrationIntegrationTest {
  @Test
  void V44_원장의_내용과_수신시각을_보존하고_명시_환경의_FK와_멱등_범위를_추가한다() throws Exception {
    Path migrations = Files.createTempDirectory("meterengine-environment-upgrade-");
    try (var postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:18"))) {
      copyMigrations(migrations, 44);
      postgres.start();
      Flyway before = flyway(postgres, migrations);
      before.migrate();
      assertThat(before.info().current().getVersion().toString()).isEqualTo("44");
      try (Connection connection =
          DriverManager.getConnection(
              postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
        UUID organization = UUID.randomUUID();
        UUID customer = UUID.randomUUID();
        UUID invoice = UUID.randomUUID();
        UUID key = UUID.randomUUID();
        UUID audit = UUID.randomUUID();
        execute(connection, "INSERT INTO organization(id,name) VALUES (?,'기존 도입사')", organization);
        execute(
            connection,
            "INSERT INTO customer(id,organization_id,name,billing_email,external_id) VALUES (?,?,'기존 고객','preserved@example.local','shared-external')",
            customer,
            organization);
        execute(
            connection,
            "INSERT INTO event(organization_id,transaction_id,customer_id,type,properties,occurred_at) VALUES (?,'shared-transaction',?,'tokens','{\"quantity\":123}','2026-10-10T00:00:00Z')",
            organization,
            customer);
        execute(
            connection,
            "INSERT INTO invoice(id,organization_id,customer_id,customer_name,period,supply_amount,tax_amount,total_amount,finalized_at) VALUES (?,?,?,'기존 확정 이름','2026-10',1000,100,1100,'2026-10-15T00:00:00Z')",
            invoice,
            organization,
            customer);
        execute(
            connection,
            "INSERT INTO api_key(id,organization_id,mode,label,key_hash,last_four,scopes,created_at) VALUES (?,?,'test','기존 키',sha256(convert_to('synthetic-key','UTF8')),'-key',ARRAY['read']::varchar[],now())",
            key,
            organization);
        execute(
            connection,
            "INSERT INTO audit_log(id,occurred_at,organization_id,actor_type,action,target_type,target_id,detail) VALUES (?,now(),?,'system','legacy_action','customer',?,'{}')",
            audit,
            organization,
            customer.toString());
        Map<String, String> snapshots = new LinkedHashMap<>();
        for (String table : List.of("customer", "event", "invoice", "api_key", "audit_log"))
          snapshots.put(
              table,
              scalar(
                  connection,
                  "SELECT jsonb_agg(to_jsonb(t) ORDER BY to_jsonb(t)::text)::text FROM "
                      + table
                      + " t WHERE organization_id=?",
                  organization));
        Files.copy(
            Path.of("src/main/resources/db/migration/V45__create_organization_environments.sql"),
            migrations.resolve("V45__create_organization_environments.sql"));
        Flyway after = flyway(postgres, migrations);
        assertThat(after.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(after.validateWithResult().validationSuccessful).isTrue();
        for (String table : snapshots.keySet()) {
          String strip =
              List.of("customer", "event").contains(table)
                  ? "to_jsonb(t)-'environment_id'-'environment_scope_id'"
                  : List.of("api_key", "audit_log").contains(table)
                      ? "to_jsonb(t)-'environment_id'"
                      : "to_jsonb(t)";
          assertThat(
                  scalar(
                      connection,
                      "SELECT jsonb_agg("
                          + strip
                          + " ORDER BY ("
                          + strip
                          + ")::text)::text FROM "
                          + table
                          + " t WHERE organization_id=?",
                      organization))
              .isEqualTo(snapshots.get(table));
        }
        assertThat(
                scalar(
                    connection,
                    "SELECT count(*) FROM organization_environment WHERE organization_id=?",
                    organization))
            .isEqualTo("2");
        UUID testEnvironment =
            UUID.fromString(
                scalar(
                    connection,
                    "SELECT id::text FROM organization_environment WHERE organization_id=? AND mode='test'",
                    organization));
        UUID liveEnvironment =
            UUID.fromString(
                scalar(
                    connection,
                    "SELECT id::text FROM organization_environment WHERE organization_id=? AND mode='live'",
                    organization));
        UUID newCustomer = UUID.randomUUID();
        execute(
            connection,
            "INSERT INTO customer(id,organization_id,environment_id,name,external_id) VALUES (?,?,?,'새 환경 고객','shared-external')",
            newCustomer,
            organization,
            testEnvironment);
        execute(
            connection,
            "INSERT INTO event(organization_id,environment_id,transaction_id,customer_id,type,occurred_at) VALUES (?,?,'shared-transaction',?,'tokens',now())",
            organization,
            testEnvironment,
            newCustomer);
        assertThat(
                scalar(
                    connection,
                    "SELECT count(*) FROM event WHERE organization_id=? AND transaction_id='shared-transaction'",
                    organization))
            .isEqualTo("2");
        assertThatThrownBy(
                () ->
                    execute(
                        connection,
                        "INSERT INTO event(organization_id,environment_id,transaction_id,customer_id,type,occurred_at) VALUES (?,?,'mixed-customer',?,'tokens',now())",
                        organization,
                        liveEnvironment,
                        newCustomer))
            .isInstanceOf(SQLException.class)
            .hasMessageContaining("event_customer_same_environment_fk");
        assertThatThrownBy(
                () ->
                    execute(
                        connection,
                        "INSERT INTO event(organization_id,transaction_id,customer_id,type,occurred_at) VALUES (?,'mixed-legacy',?,'tokens',now())",
                        organization,
                        newCustomer))
            .isInstanceOf(SQLException.class)
            .hasMessageContaining("event_customer_same_environment_fk");
        assertThatThrownBy(
                () ->
                    execute(
                        connection,
                        "UPDATE event SET type='changed' WHERE organization_id=?",
                        organization))
            .isInstanceOf(SQLException.class)
            .hasMessageContaining("append-only");
      }
    } finally {
      try (var paths = Files.walk(migrations)) {
        for (Path path : paths.sorted(Comparator.reverseOrder()).toList())
          Files.deleteIfExists(path);
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
        var matcher =
            java.util.regex.Pattern.compile("V([0-9]+)__.*\\.sql")
                .matcher(path.getFileName().toString());
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

  private static String scalar(Connection connection, String sql, Object... values)
      throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      for (int index = 0; index < values.length; index++)
        statement.setObject(index + 1, values[index]);
      try (var result = statement.executeQuery()) {
        result.next();
        return result.getString(1);
      }
    }
  }
}
