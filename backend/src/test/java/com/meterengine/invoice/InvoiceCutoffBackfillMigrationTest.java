package com.meterengine.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

class InvoiceCutoffBackfillMigrationTest {

  private static final ZoneOffset SEOUL = ZoneOffset.ofHours(9);

  @Test
  void 기존_인보이스의_마감_시각은_그때_확정한_코드가_읽은_범위로_채운다() throws Exception {
    try (PostgreSQLContainer postgres =
        new PostgreSQLContainer(DockerImageName.parse("postgres:18"))) {
      postgres.start();
      flyway(postgres, "53").migrate();

      try (Connection connection = connect(postgres)) {
        UUID organizationId = insertOrganization(connection);
        UUID lateFinalizeBeforeDeploy =
            insertInvoice(connection, organizationId, "2026-09", "2026-10-05T10:00:00+09:00");
        UUID earlyFinalizeBeforeDeploy =
            insertInvoice(connection, organizationId, "2026-08", "2026-09-01T09:00:00+09:00");
        UUID unfinishedMonthAfterDeploy =
            insertInvoice(connection, organizationId, "2026-10", "2026-10-31T10:00:00+09:00");
        UUID pastMonthAfterDeploy =
            insertInvoice(connection, organizationId, "2026-09", "2026-10-30T16:00:00+09:00");
        UUID decemberAfterDeploy =
            insertInvoice(connection, organizationId, "2026-12", "2027-01-03T10:00:00+09:00");
        UUID duringDeployWithoutLateEvents =
            insertInvoice(connection, organizationId, "2026-07", "2026-10-30T15:09:50+09:00");

        flyway(postgres, "latest").migrate();

        assertThat(cutoffAt(connection, lateFinalizeBeforeDeploy))
            .isEqualTo(OffsetDateTime.parse("2026-10-05T10:00:00+09:00"));
        assertThat(cutoffAt(connection, earlyFinalizeBeforeDeploy))
            .isEqualTo(OffsetDateTime.parse("2026-09-01T09:00:00+09:00"));
        assertThat(cutoffAt(connection, unfinishedMonthAfterDeploy))
            .isEqualTo(OffsetDateTime.parse("2026-10-31T10:00:00+09:00"));
        assertThat(cutoffAt(connection, pastMonthAfterDeploy))
            .isEqualTo(OffsetDateTime.parse("2026-10-01T13:00:00+09:00"));
        assertThat(cutoffAt(connection, decemberAfterDeploy))
            .isEqualTo(OffsetDateTime.parse("2027-01-01T13:00:00+09:00"));
        assertThat(cutoffAt(connection, duringDeployWithoutLateEvents))
            .isEqualTo(OffsetDateTime.parse("2026-08-01T13:00:00+09:00"));
      }
    }
  }

  @Test
  void 배포_구간에_확정한_지난_달에_마감_뒤_받은_이벤트가_있으면_마이그레이션이_멈춘다() throws Exception {
    try (PostgreSQLContainer postgres =
        new PostgreSQLContainer(DockerImageName.parse("postgres:18"))) {
      postgres.start();
      flyway(postgres, "53").migrate();

      try (Connection connection = connect(postgres)) {
        UUID organizationId = insertOrganization(connection);
        UUID invoiceId =
            insertInvoice(connection, organizationId, "2026-07", "2026-10-30T15:09:50+09:00");
        UUID customerId =
            UUID.fromString(
                scalar(connection, "SELECT customer_id::text FROM invoice WHERE id=?", invoiceId));
        execute(connection, "ALTER TABLE event DISABLE TRIGGER event_set_received_at");
        execute(
            connection,
            "INSERT INTO"
                + " event(organization_id,transaction_id,customer_id,type,occurred_at,received_at,properties)"
                + " VALUES (?,'late-event',?,'tokens','2026-07-20T10:00:00+09:00',"
                + "'2026-10-29T10:00:00+09:00','{\"quantity\":1}')",
            organizationId,
            customerId);
        execute(connection, "ALTER TABLE event ENABLE TRIGGER event_set_received_at");

        assertThatThrownBy(() -> flyway(postgres, "latest").migrate())
            .isInstanceOf(FlywayException.class)
            .hasMessageContaining("cutoff_at을 정할 수 없는 인보이스 1건");
        assertThat(
                scalar(
                    connection,
                    "SELECT count(*)::text FROM information_schema.columns"
                        + " WHERE table_name='invoice' AND column_name='cutoff_at'"))
            .isEqualTo("0");
      }
    }
  }

  private static Flyway flyway(PostgreSQLContainer postgres, String target) {
    return Flyway.configure()
        .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
        .locations("classpath:db/migration")
        .target(target)
        .load();
  }

  private static Connection connect(PostgreSQLContainer postgres) throws SQLException {
    return DriverManager.getConnection(
        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
  }

  private static UUID insertOrganization(Connection connection) throws SQLException {
    UUID organizationId = UUID.randomUUID();
    execute(connection, "INSERT INTO organization(id,name) VALUES (?,'채움 시험 도입사')", organizationId);
    return organizationId;
  }

  private static UUID insertInvoice(
      Connection connection, UUID organizationId, String period, String finalizedAt)
      throws SQLException {
    UUID customerId = UUID.randomUUID();
    UUID invoiceId = UUID.randomUUID();
    execute(
        connection,
        "INSERT INTO customer(id,organization_id,name) VALUES (?,?,'채움 시험 고객')",
        customerId,
        organizationId);
    execute(
        connection,
        "INSERT INTO"
            + " invoice(id,organization_id,customer_id,customer_name,period,supply_amount,tax_amount,total_amount,finalized_at)"
            + " VALUES (?,?,?,'채움 시험 고객',?,250,25,275,?)",
        invoiceId,
        organizationId,
        customerId,
        period,
        OffsetDateTime.parse(finalizedAt));
    return invoiceId;
  }

  private static OffsetDateTime cutoffAt(Connection connection, UUID invoiceId)
      throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement("SELECT cutoff_at FROM invoice WHERE id=?")) {
      statement.setObject(1, invoiceId);
      try (ResultSet result = statement.executeQuery()) {
        assertThat(result.next()).isTrue();
        return result.getObject(1, OffsetDateTime.class).withOffsetSameInstant(SEOUL);
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
