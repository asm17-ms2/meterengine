package com.meterengine.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.meterengine.TestCredentials;
import com.meterengine.TestcontainersConfiguration;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Locale;
import java.util.Map;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.json.JsonMapper;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@DirtiesContext
class OperatorAccountCreateConcurrencyTest {
  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private DataSource dataSource;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private JsonMapper jsonMapper;

  @Test
  void 다른_생성이_먼저_이메일을_점유하면_기다린_운영자_생성은_409이고_기존_계정은_그대로다() throws Exception {
    MockMvcTester mvc =
        MockMvcTester.from(
            webApplicationContext, builder -> builder.apply(springSecurity()).build());
    String operator = TestCredentials.issueOperatorSession(jdbcTemplate);
    UUID firstId = UUID.randomUUID();
    String email = firstId + "@example.com";
    String firstHash = passwordEncoder.encode("first-account-password");

    try (Connection first = dataSource.getConnection()) {
      first.setAutoCommit(false);
      try (PreparedStatement statement =
          first.prepareStatement(
              """
              INSERT INTO user_account (id, email, password_hash, name, role, created_at)
              VALUES (?, ?, ?, '먼저 생성한 사용자', 'member', now())
              """)) {
        statement.setObject(1, firstId);
        statement.setString(2, email);
        statement.setString(3, firstHash);
        statement.executeUpdate();
      }
      CompletableFuture<MvcTestResult> late =
          CompletableFuture.supplyAsync(
              () ->
                  mvc.post()
                      .uri("/v1/user-accounts")
                      .header(AUTHORIZATION, operator)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(
                          jsonMapper.writeValueAsString(
                              Map.of(
                                  "email", email.toUpperCase(Locale.ROOT),
                                  "name", "늦게 생성한 운영자",
                                  "password", "second-account-password")))
                      .exchange());

      assertThatThrownBy(() -> late.get(2, TimeUnit.SECONDS)).isInstanceOf(TimeoutException.class);
      first.commit();

      assertThat(late.get(10, TimeUnit.SECONDS))
          .hasStatus(409)
          .bodyJson()
          .extractingPath("$.code")
          .isEqualTo("email_already_exists");
    }

    assertThat(
            jdbcTemplate.queryForMap(
                "SELECT id, email, password_hash, name, role, consent_required FROM user_account WHERE lower(email)=?",
                email))
        .containsEntry("id", firstId)
        .containsEntry("email", email)
        .containsEntry("password_hash", firstHash)
        .containsEntry("name", "먼저 생성한 사용자")
        .containsEntry("role", "member")
        .containsEntry("consent_required", false);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM user_account WHERE lower(email)=?", Integer.class, email))
        .isEqualTo(1);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_log WHERE action='user_account_created' AND target_id=?",
                Integer.class,
                firstId.toString()))
        .isZero();
  }
}
