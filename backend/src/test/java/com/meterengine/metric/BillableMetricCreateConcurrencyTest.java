package com.meterengine.metric;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meterengine.ErrorCodes;
import com.meterengine.TestcontainersConfiguration;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.context.WebApplicationContext;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class BillableMetricCreateConcurrencyTest {

  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private DataSource dataSource;
  @Autowired private JdbcTemplate jdbc;

  private MockMvcTester mvc;

  @BeforeEach
  void setUp() {
    mvc = MockMvcTester.from(webApplicationContext);
  }

  @AfterEach
  void cleanUp() {
    jdbc.update("DELETE FROM billable_metric WHERE name = '먼저 등록한 미터'");
  }

  @Test
  void 중복_확인을_통과한_등록이_겹치면_늦은_쪽은_409로_끝난다() throws Exception {
    UUID orgId = insertOrganization();

    try (Connection first = dataSource.getConnection()) {
      first.setAutoCommit(false);
      insertMetric(first, orgId);

      CompletableFuture<MvcTestResult> late = CompletableFuture.supplyAsync(() -> post(orgId));

      assertThatThrownBy(() -> late.get(2, TimeUnit.SECONDS)).isInstanceOf(TimeoutException.class);

      first.commit();

      assertThat(late.get(10, TimeUnit.SECONDS))
          .hasStatus(409)
          .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
          .bodyJson()
          .extractingPath("$.code")
          .asString()
          .isEqualTo(ErrorCodes.METRIC_ALREADY_EXISTS);
    }

    assertThat(storedName(orgId)).isEqualTo("먼저 등록한 미터");
  }

  private MvcTestResult post(UUID organizationId) {
    return mvc.post()
        .uri("/v1/billable-metrics")
        .header("X-Organization-Id", organizationId.toString())
        .contentType(MediaType.APPLICATION_JSON)
        .content(
            """
            {"code": "token-usage", "name": "늦게 등록한 미터", "event_type": "chat_completion",
             "aggregation": "SUM", "target_property": "token"}
            """)
        .exchange();
  }

  private void insertMetric(Connection connection, UUID orgId) throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(
            """
            INSERT INTO billable_metric
              (organization_id, code, name, event_type, aggregation, target_property)
            VALUES (?, 'token-usage', '먼저 등록한 미터', 'chat_completion', 'SUM', 'token')
            """)) {
      statement.setObject(1, orgId);
      statement.executeUpdate();
    }
  }

  private UUID insertOrganization() {
    return jdbc.queryForObject(
        "INSERT INTO organization (name) VALUES ('동시성 테스트 도입사') RETURNING id", UUID.class);
  }

  private String storedName(UUID orgId) {
    return jdbc.queryForObject(
        "SELECT name FROM billable_metric WHERE organization_id = ? AND code = 'token-usage'",
        String.class,
        orgId);
  }
}
