package com.meterengine.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.meterengine.TestcontainersConfiguration;
import com.meterengine.account.service.IssuedSession;
import com.meterengine.account.service.SessionService;
import com.meterengine.global.error.UnauthorizedException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SessionPasswordChangeConcurrencyTest {

  private static final String PASSWORD = "initial-password";

  @Autowired private DataSource dataSource;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private SessionService sessionService;

  @Test
  void 비밀번호_확인_뒤_다른_요청이_비밀번호를_바꾸면_옛_비밀번호로_세션을_만들지_않는다() throws Exception {
    UUID organizationId =
        jdbcTemplate.queryForObject(
            "INSERT INTO organization (name, status) VALUES ('경합 도입사', 'active') RETURNING id",
            UUID.class);
    UUID userAccountId = UUID.randomUUID();
    String email = "race-" + userAccountId + "@example.com";
    jdbcTemplate.update(
        """
        INSERT INTO user_account (id, email, password_hash, name, role, created_at)
        VALUES (?, ?, ?, '경합 사용자', 'member', now())
        """,
        userAccountId,
        email,
        passwordEncoder.encode(PASSWORD));
    jdbcTemplate.update(
        "INSERT INTO organization_member (organization_id, user_account_id, created_at) VALUES (?, ?, now())",
        organizationId,
        userAccountId);

    CompletableFuture<IssuedSession> login;
    // 첫 동의처럼 사용자 행을 잠근 채 비밀번호를 바꾸는 동안 로그인이 옛 비밀번호 확인을 마친다.
    try (Connection connection = dataSource.getConnection()) {
      connection.setAutoCommit(false);
      try (PreparedStatement lock =
              connection.prepareStatement("SELECT id FROM user_account WHERE id = ? FOR UPDATE");
          PreparedStatement change =
              connection.prepareStatement(
                  "UPDATE user_account SET password_hash = ? WHERE id = ?")) {
        lock.setObject(1, userAccountId);
        lock.executeQuery().close();
        change.setString(1, passwordEncoder.encode("changed-password"));
        change.setObject(2, userAccountId);
        change.executeUpdate();
      }
      login = CompletableFuture.supplyAsync(() -> sessionService.create(email, PASSWORD));
      Thread.sleep(1000);
      assertThat(login).isNotDone();
      connection.commit();
    }

    assertThat(login)
        .failsWithin(10, TimeUnit.SECONDS)
        .withThrowableOfType(ExecutionException.class)
        .withCauseInstanceOf(UnauthorizedException.class);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM user_session WHERE user_account_id = ?",
                Integer.class,
                userAccountId))
        .isZero();
  }
}
