package com.meterengine.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.jayway.jsonpath.JsonPath;
import com.meterengine.TestCredentials;
import com.meterengine.TestcontainersConfiguration;
import com.meterengine.account.dto.CreateUserAccountRequest;
import jakarta.persistence.EntityManager;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
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
import tools.jackson.databind.json.JsonMapper;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class OperatorAccountIntegrationTest {
  private static final String USER_ACCOUNTS = "/v1/user-accounts";
  private static final String PASSWORD = " operator-password ";
  private static final List<Map<String, String>> CONSENTS =
      List.of(
          Map.of("document", "terms_of_service", "document_version", "2026-10-26-preview"),
          Map.of("document", "privacy_policy", "document_version", "2026-10-26-preview"));

  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private EntityManager entityManager;
  @Autowired private JsonMapper jsonMapper;
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
  void 운영자는_소속없는_운영자를_만들고_새_운영자는_본인동의_후_운영한다() {
    String email = UUID.randomUUID() + "@Example.com";
    MvcTestResult created = post(USER_ACCOUNTS, operator, account(email));

    assertThat(created).hasStatus(201);
    Map<String, Object> response = JsonPath.read(body(created), "$");
    assertThat(response)
        .containsOnlyKeys("id", "email", "name", "role", "organizations", "created_at")
        .containsEntry("email", email.toLowerCase(Locale.ROOT))
        .containsEntry("name", "새 운영자")
        .containsEntry("role", "operator")
        .containsEntry("organizations", List.of());
    UUID userAccountId = UUID.fromString((String) response.get("id"));
    String hash =
        jdbcTemplate.queryForObject(
            "SELECT password_hash FROM user_account WHERE id=?", String.class, userAccountId);
    assertThat(passwordEncoder.matches(PASSWORD, hash)).isTrue();
    assertThat(passwordEncoder.matches(PASSWORD.trim(), hash)).isFalse();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT consent_required FROM user_account WHERE id=?",
                Boolean.class,
                userAccountId))
        .isTrue();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM organization_member WHERE user_account_id=?",
                Integer.class,
                userAccountId))
        .isZero();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM consent WHERE user_account_id=?",
                Integer.class,
                userAccountId))
        .isZero();
    assertThat(body(created)).doesNotContain(PASSWORD, hash, "password");

    MvcTestResult login = post("/v1/sessions", null, Map.of("email", email, "password", PASSWORD));
    assertThat(login)
        .hasStatus(201)
        .bodyJson()
        .extractingPath("$.consent_required")
        .isEqualTo(true);
    assertThat((Object) JsonPath.read(body(login), "$.organization")).isNull();
    String authorization = TestCredentials.BEARER_PREFIX + JsonPath.read(body(login), "$.token");
    assertThat(get("/v1/sessions/current", authorization)).hasStatusOk();
    assertThat(get("/v1/operations", authorization))
        .hasStatus(403)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("consent_required");
    assertThat(post(USER_ACCOUNTS, authorization, account(UUID.randomUUID() + "@example.com")))
        .hasStatus(403)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("consent_required");

    assertThat(
            post(
                "/v1/sessions/current/consents",
                authorization,
                Map.of("consents", CONSENTS, "new_password", "operator-own-password")))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.consent_required")
        .isEqualTo(false);
    assertThat(get("/v1/operations", authorization)).hasStatusOk();
    assertThat(get(USER_ACCOUNTS, authorization)).hasStatusOk();
    entityManager.flush();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM consent WHERE user_account_id=?",
                Integer.class,
                userAccountId))
        .isEqualTo(2);
  }

  @Test
  void 생성_감사는_실제_운영자와_대상만_남기고_본문과_비밀번호를_담지_않는다() {
    String email = UUID.randomUUID() + "@example.com";
    MvcTestResult created = post(USER_ACCOUNTS, operator, account(email));
    assertThat(created).hasStatus(201);
    String id = JsonPath.read(body(created), "$.id");
    entityManager.flush();
    assertThat(
            jdbcTemplate.queryForMap(
                """
                SELECT actor_type, actor_id, organization_id, target_type, target_id, detail::text
                FROM audit_log WHERE action='user_account_created' AND target_id=?
                """,
                id))
        .containsEntry("actor_type", "user_account")
        .containsEntry("actor_id", userAccountId(operator))
        .containsEntry("organization_id", null)
        .containsEntry("target_type", "user_account")
        .containsEntry("target_id", id)
        .containsEntry("detail", "{\"role\": \"operator\"}");
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT detail::text FROM audit_log WHERE actor_id=?",
                String.class,
                userAccountId(operator)))
        .allSatisfy(
            detail ->
                assertThat(detail)
                    .doesNotContain(PASSWORD, "password", email, "새 운영자", "password_hash"));
    assertThat(new CreateUserAccountRequest(email, "새 운영자", PASSWORD).toString())
        .doesNotContain(email, PASSWORD, "새 운영자");
  }

  @Test
  void 기존_이메일은_대소문자와_관계없이_409이며_기존_계정을_덮어쓰지_않는다() {
    UUID organizationId = UUID.randomUUID();
    String member = TestCredentials.issueMemberSession(jdbcTemplate, organizationId);
    UUID id = userAccountId(member);
    Map<String, Object> before =
        jdbcTemplate.queryForMap("SELECT * FROM user_account WHERE id=?", id);
    String email = (String) before.get("email");
    String membership =
        jdbcTemplate.queryForObject(
            "SELECT to_jsonb(m)::text FROM organization_member m WHERE user_account_id=?",
            String.class,
            id);

    assertThat(post(USER_ACCOUNTS, operator, account(email.toUpperCase(Locale.ROOT))))
        .hasStatus(409)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("email_already_exists");
    entityManager.flush();
    assertThat(jdbcTemplate.queryForMap("SELECT * FROM user_account WHERE id=?", id))
        .isEqualTo(before);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT to_jsonb(m)::text FROM organization_member m WHERE user_account_id=?",
                String.class,
                id))
        .isEqualTo(membership);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_log WHERE action='user_account_created' AND actor_id=?",
                Integer.class,
                userAccountId(operator)))
        .isZero();
  }

  @Test
  void 도입사_대표와_관리자와_API_키는_운영자를_생성하지_못한다() {
    UUID organizationId = UUID.randomUUID();
    String owner = TestCredentials.issueMemberSession(jdbcTemplate, organizationId);
    jdbcTemplate.update(
        "UPDATE organization_member SET role='owner' WHERE user_account_id=?",
        userAccountId(owner));
    String admin = TestCredentials.issueMemberSession(jdbcTemplate, organizationId);
    String apiKey = TestCredentials.issueApiKey(jdbcTemplate, organizationId, "admin");
    String readKey = TestCredentials.issueApiKey(jdbcTemplate, organizationId, "read");
    String email = UUID.randomUUID() + "@example.com";

    for (String authorization : List.of(owner, admin, apiKey, readKey)) {
      assertThat(post(USER_ACCOUNTS, authorization, account(email)))
          .hasStatus(403)
          .bodyJson()
          .extractingPath("$.code")
          .isEqualTo("permission_denied");
    }
    assertThat(post(USER_ACCOUNTS, null, account(email)))
        .hasStatus(401)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("authentication_required");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM user_account WHERE email=?", Integer.class, email))
        .isZero();
  }

  @Test
  void 본문의_역할과_관계없이_서버가_operator로_생성한다() {
    String email = UUID.randomUUID() + "@example.com";
    assertThat(
            post(
                USER_ACCOUNTS,
                operator,
                Map.of("email", email, "name", "새 운영자", "password", PASSWORD, "role", "member")))
        .hasStatus(201)
        .bodyJson()
        .extractingPath("$.role")
        .isEqualTo("operator");
  }

  @Test
  void 이메일과_이름과_초기_비밀번호의_형식과_저장_한계를_검사한다() {
    String email = UUID.randomUUID() + "@example.com";
    for (Map<String, String> request :
        List.of(
            Map.of("email", "bad-email", "name", "새 운영자", "password", PASSWORD),
            Map.of(
                "email", "x".repeat(244) + "@example.com", "name", "새 운영자", "password", PASSWORD),
            Map.of("email", email, "name", " ", "password", PASSWORD),
            Map.of("email", email, "name", "가".repeat(256), "password", PASSWORD),
            Map.of("email", email, "name", "새 운영자", "password", "short"),
            Map.of("email", email, "name", "새 운영자", "password", "x".repeat(73)),
            Map.of("email", email, "name", "새 운영자", "password", "가".repeat(25)),
            Map.of("email", email, "name", "새 운영자", "password", "invalid\u0000password"),
            Map.of("email", email, "name", "새\u0000운영자", "password", PASSWORD))) {
      assertThat(post(USER_ACCOUNTS, operator, request))
          .hasStatus(400)
          .bodyJson()
          .extractingPath("$.code")
          .isEqualTo("validation_error");
    }
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM user_account WHERE email=?", Integer.class, email))
        .isZero();
  }

  private Map<String, String> account(String email) {
    return Map.of("email", email, "name", "새 운영자", "password", PASSWORD);
  }

  private UUID userAccountId(String authorization) {
    return jdbcTemplate.queryForObject(
        "SELECT user_account_id FROM user_session WHERE token_hash = sha256(convert_to(?, 'UTF8'))",
        UUID.class,
        TestCredentials.rawKeyOf(authorization));
  }

  private MvcTestResult get(String uri, String authorization) {
    return mvc.get().uri(uri).header(AUTHORIZATION, authorization).exchange();
  }

  private MvcTestResult post(String uri, String authorization, Map<String, ?> request) {
    var post = mvc.post().uri(uri).contentType(MediaType.APPLICATION_JSON);
    if (authorization != null) post.header(AUTHORIZATION, authorization);
    return post.content(jsonMapper.writeValueAsString(request)).exchange();
  }

  private static String body(MvcTestResult result) {
    try {
      return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    } catch (UnsupportedEncodingException exception) {
      throw new IllegalStateException("응답 본문을 읽지 못했다", exception);
    }
  }
}
