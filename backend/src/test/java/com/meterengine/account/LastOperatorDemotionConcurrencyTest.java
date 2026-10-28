package com.meterengine.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.meterengine.TestCredentials;
import com.meterengine.TestcontainersConfiguration;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.context.WebApplicationContext;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@DirtiesContext
class LastOperatorDemotionConcurrencyTest {
  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private DataSource dataSource;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void 다른_운영자를_먼저_내리는_중이면_기다린_자기_하향은_409이고_역할은_그대로다() throws Exception {
    MockMvcTester mvc =
        MockMvcTester.from(
            webApplicationContext, builder -> builder.apply(springSecurity()).build());
    String operator = TestCredentials.issueOperatorSession(jdbcTemplate);
    UUID operatorId =
        jdbcTemplate.queryForObject(
            "SELECT user_account_id FROM user_session WHERE token_hash = sha256(convert_to(?, 'UTF8'))",
            UUID.class,
            TestCredentials.rawKeyOf(operator));
    UUID otherId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO user_account (id, email, password_hash, name, role, created_at)
        VALUES (?, ?, 'unused-password-hash', '다른 운영자', 'operator', now())
        """,
        otherId,
        otherId + "@example.com");
    jdbcTemplate.update(
        "UPDATE user_account SET role='member' WHERE role='operator' AND id NOT IN (?, ?)",
        operatorId,
        otherId);

    try (Connection other = dataSource.getConnection()) {
      other.setAutoCommit(false);
      try (PreparedStatement statement =
          other.prepareStatement("UPDATE user_account SET role='member' WHERE id=?")) {
        statement.setObject(1, otherId);
        statement.executeUpdate();
      }
      CompletableFuture<MvcTestResult> late =
          CompletableFuture.supplyAsync(
              () ->
                  mvc.put()
                      .uri("/v1/user-accounts/{id}/role", operatorId)
                      .header(AUTHORIZATION, operator)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("{\"role\":\"member\"}")
                      .exchange());

      assertThatThrownBy(() -> late.get(2, TimeUnit.SECONDS)).isInstanceOf(TimeoutException.class);
      other.commit();

      assertThat(late.get(10, TimeUnit.SECONDS))
          .hasStatus(409)
          .bodyJson()
          .extractingPath("$.code")
          .isEqualTo("last_operator_required");
    }

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT role FROM user_account WHERE id=?", String.class, operatorId))
        .isEqualTo("operator");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_log WHERE action='user_account_role_updated' AND target_id=?",
                Integer.class,
                operatorId.toString()))
        .isZero();
  }
}
