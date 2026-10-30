package com.meterengine.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

class PricingMigrationIntegrationTest {

  private static final Pattern VERSIONED_MIGRATION = Pattern.compile("V([0-9]+)__.*\\.sql");

  @Test
  void 기존_DB를_업그레이드해도_가격과_확정_금액을_보존하고_새_구간과_요금제를_추가한다() throws Exception {
    Path legacyMigrations = Files.createTempDirectory("meterengine-schema-upgrade-");
    try (PostgreSQLContainer postgres =
        new PostgreSQLContainer(DockerImageName.parse("postgres:18"))) {
      copyLegacyMigrations(legacyMigrations);
      postgres.start();
      Flyway legacy =
          Flyway.configure()
              .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
              .locations("filesystem:" + legacyMigrations.toAbsolutePath())
              .load();
      legacy.migrate();
      assertThat(legacy.info().current().getVersion().toString()).isEqualTo("25");

      try (Connection connection =
          DriverManager.getConnection(
              postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
        UUID organizationId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        execute(
            connection, "INSERT INTO organization(id,name) VALUES (?,'업그레이드 도입사')", organizationId);
        execute(
            connection,
            "INSERT INTO customer(id,organization_id,name) VALUES (?,?,'기존 고객')",
            customerId,
            organizationId);
        execute(
            connection,
            "INSERT INTO"
                + " billable_metric(organization_id,code,name,event_type,aggregation,target_property)"
                + " VALUES (?,'tokens','토큰','tokens','sum','quantity')",
            organizationId);
        execute(
            connection,
            "INSERT INTO"
                + " price_rate(organization_id,billable_metric_code,dimension_values,unit_price)"
                + " VALUES (?,'tokens','{}',0.5)",
            organizationId);
        execute(
            connection,
            "INSERT INTO"
                + " event(organization_id,transaction_id,customer_id,type,occurred_at,properties)"
                + " VALUES"
                + " (?,'legacy-event',?,'tokens','2026-08-10T00:00:00Z','{\"quantity\":500}')",
            organizationId,
            customerId);
        execute(
            connection,
            "INSERT INTO"
                + " invoice(id,organization_id,customer_id,customer_name,period,supply_amount,tax_amount,total_amount,finalized_at)"
                + " VALUES (?,?,?,'확정 당시 고객','2026-08',250,25,275,'2026-09-01T00:00:00Z')",
            invoiceId,
            organizationId,
            customerId);
        execute(
            connection,
            "INSERT INTO"
                + " invoice_line(organization_id,invoice_id,billable_metric_code,dimension_values,quantity,unit_price,amount)"
                + " VALUES (?,?,'tokens','{}',500,0.5,250)",
            organizationId,
            invoiceId);
        execute(
            connection,
            "INSERT INTO"
                + " payment_attempt(id,organization_id,customer_id,invoice_id,amount,order_name,status,requested_at)"
                + " VALUES (?,?,?,?,275,'기존 결제','pending',now())",
            attemptId,
            organizationId,
            customerId,
            invoiceId);
        String invoiceBefore =
            scalar(connection, "SELECT to_jsonb(invoice)::text FROM invoice WHERE id=?", invoiceId);
        String lineBefore =
            scalar(
                connection,
                "SELECT"
                    + " jsonb_build_object('id',id,'billable_metric_code',billable_metric_code,'dimension_values',dimension_values,'quantity',quantity,'unit_price',unit_price,'amount',amount)::text"
                    + " FROM invoice_line WHERE invoice_id=?",
                invoiceId);

        Flyway latest =
            Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load();
        assertThat(latest.migrate().migrationsExecuted).isPositive();
        assertThat(latest.validateWithResult().validationSuccessful).isTrue();
        assertThat(latest.info().pending()).isEmpty();
        assertThat(
                scalar(
                    connection,
                    "SELECT"
                        + " (to_jsonb(invoice)-'display_number'-'environment_id'-'environment_scope_id')::text"
                        + " FROM invoice WHERE id=?",
                    invoiceId))
            .isEqualTo(invoiceBefore);
        for (String table :
            List.of(
                "customer",
                "billable_metric",
                "event",
                "price_rate",
                "invoice",
                "invoice_line",
                "payment_attempt")) {
          assertThat(
                  scalar(
                      connection,
                      "SELECT count(*)::text || ':' || (count(*) FILTER (WHERE environment_id IS"
                          + " NULL AND environment_scope_id ="
                          + " '00000000-0000-0000-0000-000000000000'::uuid))::text FROM "
                          + table
                          + " WHERE organization_id=?",
                      organizationId))
              .as(table)
              .isEqualTo("1:1");
        }
        String legacyDisplayNumber =
            scalar(connection, "SELECT display_number::text FROM invoice WHERE id=?", invoiceId);
        assertThat(legacyDisplayNumber).matches("[1-9][0-9]*");
        assertThat(
                scalar(
                    connection,
                    "SELECT"
                        + " jsonb_build_object('id',id,'billable_metric_code',billable_metric_code,'dimension_values',dimension_values,'quantity',quantity,'unit_price',unit_price,'amount',amount)::text"
                        + " FROM invoice_line WHERE invoice_id=?",
                    invoiceId))
            .isEqualTo(lineBefore);
        assertThat(
                scalar(
                    connection,
                    "SELECT effective_from || ':' || model || ':' || unit_price::text FROM"
                        + " price_rate WHERE organization_id=?",
                    organizationId))
            .isEqualTo("2000-01:per_unit:0.5");
        assertThat(
                scalar(
                    connection,
                    "SELECT mode || ':' || status || ':' || amount::text FROM payment_attempt WHERE"
                        + " id=?",
                    attemptId))
            .isEqualTo("test:pending:275");
        assertThat(
                scalar(
                    connection,
                    "SELECT line_type || ':' || tier_index::text FROM invoice_line WHERE"
                        + " invoice_id=?",
                    invoiceId))
            .isEqualTo("per_unit:0");
        assertThat(
                scalar(
                    connection,
                    "SELECT count(*) FROM event WHERE organization_id=? AND"
                        + " transaction_id='legacy-event'",
                    organizationId))
            .isEqualTo("1");

        execute(
            connection,
            "INSERT INTO"
                + " price_rate(organization_id,billable_metric_code,dimension_values,effective_from,model,unit_price,tiers)"
                + " VALUES"
                + " (?,'tokens','{}','2026-10','graduated',NULL,'[{\"up_to\":100,\"unit_price\":10},{\"up_to\":1000,\"unit_price\":8},{\"up_to\":null,\"unit_price\":5}]')",
            organizationId);
        execute(
            connection,
            "INSERT INTO price_plan(organization_id,effective_from,base_fee,minimum_amount) VALUES"
                + " (?,'2026-10',500,12000)",
            organizationId);
        UUID nextInvoiceId = UUID.randomUUID();
        execute(
            connection,
            "INSERT INTO"
                + " invoice(id,organization_id,customer_id,customer_name,period,supply_amount,tax_amount,total_amount,finalized_at)"
                + " VALUES (?,?,?,'신규 확정 고객','2026-10',12500,1250,13750,now())",
            nextInvoiceId,
            organizationId,
            customerId);
        execute(
            connection,
            "INSERT INTO"
                + " invoice_line(organization_id,invoice_id,billable_metric_code,dimension_values,quantity,unit_price,amount,line_type,tier_index)"
                + " VALUES"
                + " (?,?,'tokens','{}',100,10,1000,'graduated_tier',0),(?,?,'tokens','{}',900,8,7200,'graduated_tier',1),(?,?,'tokens','{}',500.5,5,2500,'graduated_tier',2),(?,?,NULL,'{}',1,1300,1300,'minimum_adjustment',0),(?,?,NULL,'{}',1,500,500,'base_fee',0)",
            organizationId,
            nextInvoiceId,
            organizationId,
            nextInvoiceId,
            organizationId,
            nextInvoiceId,
            organizationId,
            nextInvoiceId,
            organizationId,
            nextInvoiceId);
        assertThat(
                scalar(
                    connection,
                    "SELECT count(*)::text || ':' || sum(amount)::text FROM invoice_line WHERE"
                        + " invoice_id=?",
                    nextInvoiceId))
            .isEqualTo("5:12500");
        assertThat(
                scalar(
                    connection,
                    "SELECT count(*) FROM price_rate WHERE organization_id=?",
                    organizationId))
            .isEqualTo("2");
        assertThat(
                scalar(
                    connection,
                    "SELECT base_fee::text || ':' || minimum_amount::text FROM price_plan WHERE"
                        + " organization_id=?",
                    organizationId))
            .isEqualTo("500:12000");
        assertThat(
                scalar(
                    connection,
                    "SELECT"
                        + " (to_jsonb(invoice)-'display_number'-'environment_id'-'environment_scope_id')::text"
                        + " FROM invoice WHERE id=?",
                    invoiceId))
            .isEqualTo(invoiceBefore);
        assertThat(latest.migrate().migrationsExecuted).isZero();
        assertThat(
                scalar(
                    connection,
                    "SELECT count(*)::text || ':' || count(display_number)::text || ':' ||"
                        + " count(DISTINCT display_number)::text FROM invoice WHERE"
                        + " organization_id=?",
                    organizationId))
            .isEqualTo("2:2:2");
        assertThat(
                scalar(
                    connection, "SELECT display_number::text FROM invoice WHERE id=?", invoiceId))
            .isEqualTo(legacyDisplayNumber);
        assertThatThrownBy(
                () ->
                    execute(
                        connection,
                        "UPDATE invoice_line SET amount=0 WHERE invoice_id=?",
                        invoiceId))
            .isInstanceOf(SQLException.class)
            .hasMessageContaining("append-only");
      }
    } finally {
      try (Stream<Path> paths = Files.walk(legacyMigrations)) {
        for (Path path : paths.sorted(Comparator.reverseOrder()).toList())
          Files.deleteIfExists(path);
      }
    }
  }

  private static void copyLegacyMigrations(Path destination) throws Exception {
    List<Path> migrations;
    try (Stream<Path> paths = Files.list(Path.of("src/main/resources/db/migration"))) {
      migrations = paths.toList();
    }
    for (Path path : migrations) {
      Matcher matcher = VERSIONED_MIGRATION.matcher(path.getFileName().toString());
      if (matcher.matches() && Integer.parseInt(matcher.group(1)) <= 25) {
        Files.copy(path, destination.resolve(path.getFileName()));
      }
    }
  }

  private static void execute(Connection connection, String sql, Object... parameters)
      throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      for (int index = 0; index < parameters.length; index++)
        statement.setObject(index + 1, parameters[index]);
      statement.executeUpdate();
    }
  }

  private static String scalar(Connection connection, String sql, Object... parameters)
      throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      for (int index = 0; index < parameters.length; index++)
        statement.setObject(index + 1, parameters[index]);
      try (ResultSet result = statement.executeQuery()) {
        assertThat(result.next()).isTrue();
        return result.getString(1);
      }
    }
  }
}
