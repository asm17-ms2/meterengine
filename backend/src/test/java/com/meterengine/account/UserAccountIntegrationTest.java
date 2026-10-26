package com.meterengine.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.jayway.jsonpath.JsonPath;
import com.meterengine.TestCredentials;
import com.meterengine.TestcontainersConfiguration;
import com.meterengine.global.error.ErrorCode;
import jakarta.persistence.EntityManager;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class UserAccountIntegrationTest {

  private static final String USER_ACCOUNTS = "/v1/user-accounts";

  private static final String PASSWORD = "user-account-password";

  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private EntityManager entityManager;

  private MockMvcTester mvc;

  private String operator;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcTester.from(
            webApplicationContext, builder -> builder.apply(springSecurity()).build());
    operator = TestCredentials.issueOperatorSession(jdbcTemplate);
  }

  @Test
  void 운영자는_사용자_목록과_각자의_소속_도입사를_조회한다() {
    UUID first = insertOrganization("첫 도입사", "active");
    UUID second = insertOrganization("둘째 도입사", "suspended");
    UUID userAccountId =
        insertUserAccount("listed@example.com", "목록 사용자", "member", "2100-01-01T00:00:00Z");
    insertMember(second, userAccountId, "2026-01-02T00:00:00Z");
    insertMember(first, userAccountId, "2026-01-01T00:00:00Z");
    UUID lonelyId =
        insertUserAccount("lonely@example.com", "소속 없는 사용자", "operator", "2100-01-02T00:00:00Z");

    MvcTestResult result = mvc.get().uri(USER_ACCOUNTS).header(AUTHORIZATION, operator).exchange();

    assertThat(result).hasStatusOk();
    List<Map<String, Object>> userAccounts = JsonPath.read(body(result), "$.user_accounts");
    assertThat(userAccounts.subList(userAccounts.size() - 2, userAccounts.size()))
        .extracting(userAccount -> userAccount.get("id"))
        .containsExactly(userAccountId.toString(), lonelyId.toString());
    Map<String, Object> listed = userAccounts.get(userAccounts.size() - 2);
    assertThat(listed)
        .containsOnlyKeys("id", "email", "name", "role", "organizations", "created_at")
        .containsEntry("email", "listed@example.com")
        .containsEntry("name", "목록 사용자")
        .containsEntry("role", "member");
    assertThat((List<Map<String, Object>>) listed.get("organizations"))
        .containsExactly(
            Map.of("id", first.toString(), "name", "첫 도입사", "status", "active"),
            Map.of("id", second.toString(), "name", "둘째 도입사", "status", "suspended"));
    assertThat(OffsetDateTime.parse((String) listed.get("created_at")))
        .isAtSameInstantAs(OffsetDateTime.parse("2100-01-01T00:00:00Z"));
    assertThat((List<Object>) userAccounts.getLast().get("organizations")).isEmpty();
    assertThat(body(result)).doesNotContain("password");
  }

  @Test
  void 역할을_바꾸면_200과_바뀐_사용자를_돌려주고_새_로그인에서_operator_권한을_갖는다() {
    UUID organizationId = insertOrganization("역할 도입사", "active");
    UUID userAccountId =
        insertUserAccount("promote@example.com", "승격 사용자", "member", "2100-01-01T00:00:00Z");
    insertMember(organizationId, userAccountId, "2026-01-01T00:00:00Z");
    String before = login("promote@example.com");
    assertThat(mvc.get().uri("/v1/organizations").header(AUTHORIZATION, before).exchange())
        .hasStatus(403);

    MvcTestResult result = updateRole(operator, userAccountId, "operator");

    assertThat(result).hasStatusOk();
    String body = body(result);
    assertThat((String) JsonPath.read(body, "$.id")).isEqualTo(userAccountId.toString());
    assertThat((String) JsonPath.read(body, "$.role")).isEqualTo("operator");
    assertThat((List<Map<String, Object>>) JsonPath.read(body, "$.organizations"))
        .containsExactly(
            Map.of("id", organizationId.toString(), "name", "역할 도입사", "status", "active"));
    entityManager.flush();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT role FROM user_account WHERE id = ?", String.class, userAccountId))
        .isEqualTo("operator");

    String after = login("promote@example.com");
    assertThat(mvc.get().uri("/v1/organizations").header(AUTHORIZATION, after).exchange())
        .hasStatusOk();
    assertThat(mvc.get().uri(USER_ACCOUNTS).header(AUTHORIZATION, after).exchange()).hasStatusOk();
  }

  @Test
  void member로_내린_operator의_기존_세션은_다음_요청부터_운영자_경로가_403_permission_denied다() {
    String demoted = TestCredentials.issueOperatorSession(jdbcTemplate);
    UUID demotedId = userAccountIdOf(demoted);
    assertThat(mvc.get().uri("/v1/organizations").header(AUTHORIZATION, demoted).exchange())
        .hasStatusOk();

    assertThat(updateRole(operator, demotedId, "member")).hasStatusOk();

    assertThat(mvc.get().uri("/v1/organizations").header(AUTHORIZATION, demoted).exchange())
        .hasStatus(403)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo(ErrorCode.PERMISSION_DENIED.getCode());
  }

  @Test
  void 역할_변경은_도입사_없이_운영자_행위자로_감사_행을_남긴다() {
    UUID operatorId = userAccountIdOf(operator);
    UUID userAccountId =
        insertUserAccount("audit-role@example.com", "감사 사용자", "member", "2100-01-01T00:00:00Z");

    assertThat(updateRole(operator, userAccountId, "operator")).hasStatusOk();

    entityManager.flush();
    assertThat(
            jdbcTemplate.queryForMap(
                """
                SELECT actor_type, actor_id, organization_id, target_type, target_id,
                       detail->>'from' AS from_role, detail->>'to' AS to_role
                FROM audit_log WHERE action = 'user_account_role_updated' AND target_id = ?
                """,
                userAccountId.toString()))
        .containsEntry("actor_type", "user_account")
        .containsEntry("actor_id", operatorId)
        .containsEntry("organization_id", null)
        .containsEntry("target_type", "user_account")
        .containsEntry("from_role", "member")
        .containsEntry("to_role", "operator");
  }

  @Test
  void 없는_사용자의_역할을_바꾸면_404_user_account_not_found다() {
    assertThat(updateRole(operator, UUID.randomUUID(), "operator"))
        .hasStatus(404)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo(ErrorCode.USER_ACCOUNT_NOT_FOUND.getCode());
  }

  @Test
  void 역할_본문이_비거나_모르는_값이면_400_validation_error다() {
    UUID userAccountId =
        insertUserAccount("bad-role@example.com", "본문 사용자", "member", "2100-01-01T00:00:00Z");

    for (String content : List.of("{}", "{\"role\":\"admin\"}", "{\"role\":\"\"}")) {
      assertThat(
              mvc.put()
                  .uri(USER_ACCOUNTS + "/{id}/role", userAccountId)
                  .header(AUTHORIZATION, operator)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(content)
                  .exchange())
          .as(content)
          .hasStatus(400)
          .bodyJson()
          .extractingPath("$.code")
          .isEqualTo(ErrorCode.VALIDATION_ERROR.getCode());
    }
  }

  @Test
  void member_세션과_API_키로_사용자_경로를_부르면_403_permission_denied다() {
    UUID organizationId = UUID.randomUUID();
    String member = TestCredentials.issueMemberSession(jdbcTemplate, organizationId);
    String apiKey = TestCredentials.issueApiKey(jdbcTemplate, organizationId);
    UUID userAccountId = userAccountIdOf(member);

    for (String authorization : List.of(member, apiKey)) {
      for (MvcTestResult result :
          List.of(
              mvc.get().uri(USER_ACCOUNTS).header(AUTHORIZATION, authorization).exchange(),
              updateRole(authorization, userAccountId, "operator"))) {
        assertThat(result)
            .hasStatus(403)
            .bodyJson()
            .extractingPath("$.code")
            .isEqualTo(ErrorCode.PERMISSION_DENIED.getCode());
      }
    }
    entityManager.flush();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT role FROM user_account WHERE id = ?", String.class, userAccountId))
        .isEqualTo("member");
  }

  private MvcTestResult updateRole(String authorization, UUID userAccountId, String role) {
    return mvc.put()
        .uri(USER_ACCOUNTS + "/{id}/role", userAccountId)
        .header(AUTHORIZATION, authorization)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"role\":\"%s\"}".formatted(role))
        .exchange();
  }

  private String login(String email) {
    MvcTestResult result =
        mvc.post()
            .uri("/v1/sessions")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD))
            .exchange();
    assertThat(result).hasStatus(201);
    return TestCredentials.BEARER_PREFIX + JsonPath.read(body(result), "$.token");
  }

  private UUID userAccountIdOf(String authorization) {
    return jdbcTemplate.queryForObject(
        "SELECT user_account_id FROM user_session WHERE token_hash = sha256(convert_to(?, 'UTF8'))",
        UUID.class,
        TestCredentials.rawKeyOf(authorization));
  }

  private UUID insertOrganization(String name, String status) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO organization (name, status) VALUES (?, ?) RETURNING id",
        UUID.class,
        name,
        status);
  }

  private UUID insertUserAccount(String email, String name, String role, String createdAt) {
    UUID userAccountId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO user_account (id, email, password_hash, name, role, created_at)
        VALUES (?, ?, ?, ?, ?, CAST(? AS TIMESTAMPTZ))
        """,
        userAccountId,
        email,
        passwordEncoder.encode(PASSWORD),
        name,
        role,
        createdAt);
    return userAccountId;
  }

  private void insertMember(UUID organizationId, UUID userAccountId, String createdAt) {
    jdbcTemplate.update(
        "INSERT INTO organization_member (organization_id, user_account_id, created_at) VALUES (?, ?, CAST(? AS TIMESTAMPTZ))",
        organizationId,
        userAccountId,
        createdAt);
  }

  private static String body(MvcTestResult result) {
    try {
      return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    } catch (UnsupportedEncodingException e) {
      throw new IllegalStateException("응답 본문을 읽지 못했다", e);
    }
  }
}
