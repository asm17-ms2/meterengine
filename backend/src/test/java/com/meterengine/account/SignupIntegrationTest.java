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
class SignupIntegrationTest {

  private static final String SIGNUPS = "/v1/signups";

  private static final String PASSWORD = "signup-password";

  private static final String TERMS =
      "{\"document\":\"terms_of_service\",\"document_version\":\"2026-10-01\"}";

  private static final String PRIVACY =
      "{\"document\":\"privacy_policy\",\"document_version\":\"2026-09-15\"}";

  private static final String BOTH_CONSENTS = "[" + TERMS + "," + PRIVACY + "]";

  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private EntityManager entityManager;

  private MockMvcTester mvc;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcTester.from(
            webApplicationContext, builder -> builder.apply(springSecurity()).build());
  }

  @Test
  void 가입하면_201과_active_조직과_member를_돌려주고_소속과_동의_행을_남긴다() {
    MvcTestResult result = signup("New.User@Example.com", PASSWORD, BOTH_CONSENTS);

    assertThat(result).hasStatus(201);
    String body = body(result);
    UUID organizationId = UUID.fromString(JsonPath.read(body, "$.organization.id"));
    UUID userAccountId = UUID.fromString(JsonPath.read(body, "$.user_account.id"));
    assertThat((Map<String, Object>) JsonPath.read(body, "$.organization"))
        .containsExactlyInAnyOrderEntriesOf(
            Map.of("id", organizationId.toString(), "name", "가입 도입사", "status", "active"));
    assertThat((Map<String, Object>) JsonPath.read(body, "$.user_account"))
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(
                "id", userAccountId.toString(),
                "email", "new.user@example.com",
                "name", "가입자",
                "role", "member",
                "email_verified", true));
    entityManager.flush();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT status FROM organization WHERE id = ?", String.class, organizationId))
        .isEqualTo("active");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT live_environment_opened_at IS NULL FROM organization WHERE id = ?",
                Boolean.class,
                organizationId))
        .isTrue();
    Map<String, Object> userAccount =
        jdbcTemplate.queryForMap(
            "SELECT email, name, role, password_hash FROM user_account WHERE id = ?",
            userAccountId);
    assertThat(userAccount)
        .containsEntry("email", "new.user@example.com")
        .containsEntry("name", "가입자")
        .containsEntry("role", "member");
    String passwordHash = (String) userAccount.get("password_hash");
    assertThat(passwordHash).startsWith("{bcrypt}").doesNotContain(PASSWORD);
    assertThat(passwordEncoder.matches(PASSWORD, passwordHash)).isTrue();
    assertThat(
            jdbcTemplate.queryForMap(
                "SELECT role, array_to_string(scopes, ',') AS scopes FROM organization_member WHERE organization_id = ? AND user_account_id = ?",
                organizationId,
                userAccountId))
        .containsEntry("role", "owner")
        .containsEntry("scopes", "admin");
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT document, document_version FROM consent WHERE user_account_id = ? ORDER BY document",
                userAccountId))
        .containsExactly(
            Map.of("document", "privacy_policy", "document_version", "2026-09-15"),
            Map.of("document", "terms_of_service", "document_version", "2026-10-01"));
  }

  @Test
  void 가입은_signup_created_감사_행을_새_사용자_행위자로_남기고_비밀번호를_남기지_않는다() {
    MvcTestResult result = signup("audit-signup@example.com", PASSWORD, BOTH_CONSENTS);

    assertThat(result).hasStatus(201);
    String body = body(result);
    UUID organizationId = UUID.fromString(JsonPath.read(body, "$.organization.id"));
    UUID userAccountId = UUID.fromString(JsonPath.read(body, "$.user_account.id"));
    entityManager.flush();
    List<Map<String, Object>> rows =
        jdbcTemplate.queryForList(
            """
            SELECT actor_type, actor_id, organization_id, target_type, target_id,
                   detail::text AS detail
            FROM audit_log WHERE action = 'signup_created' AND organization_id = ?
            """,
            organizationId);
    assertThat(rows).hasSize(1);
    assertThat(rows.getFirst())
        .containsEntry("actor_type", "user_account")
        .containsEntry("actor_id", userAccountId)
        .containsEntry("target_type", "organization")
        .containsEntry("target_id", organizationId.toString());
    assertThat((String) rows.getFirst().get("detail")).doesNotContain(PASSWORD);
  }

  @Test
  void 가입한_member는_운영자_승인_없이_바로_로그인하고_실제_환경이_잠긴_조직을_받는다() {
    MvcTestResult signup = signup("instant@example.com", PASSWORD, BOTH_CONSENTS);
    assertThat(signup).hasStatus(201);
    String organizationId = JsonPath.read(body(signup), "$.organization.id");

    MvcTestResult login = login("instant@example.com", PASSWORD);

    assertThat(login).hasStatus(201);
    assertThat(login).bodyJson().extractingPath("$.organization.id").isEqualTo(organizationId);
    assertThat(login).bodyJson().extractingPath("$.organization.status").isEqualTo("active");
    assertThat(login)
        .bodyJson()
        .extractingPath("$.organization.live_environment_opened_at")
        .isNull();
  }

  @Test
  void 가입한_조직은_테스트_환경만_쓰고_실제_환경과_환경_헤더_없는_요청은_403_live_environment_locked다() {
    assertThat(signup("locked@example.com", PASSWORD, BOTH_CONSENTS)).hasStatus(201);
    String authorization =
        TestCredentials.BEARER_PREFIX
            + JsonPath.read(body(login("locked@example.com", PASSWORD)), "$.token");

    assertThat(customers(authorization, "test")).hasStatusOk();
    for (String environment : new String[] {"live", "legacy", null}) {
      assertThat(customers(authorization, environment))
          .as(String.valueOf(environment))
          .hasStatus(403)
          .bodyJson()
          .extractingPath("$.code")
          .isEqualTo(ErrorCode.LIVE_ENVIRONMENT_LOCKED.getCode());
    }
  }

  @Test
  void 가입한_조직은_실제_환경과_기존_데이터_범위의_API_키를_발급하지_못한다() {
    assertThat(signup("no-live-key@example.com", PASSWORD, BOTH_CONSENTS)).hasStatus(201);
    String authorization =
        TestCredentials.BEARER_PREFIX
            + JsonPath.read(body(login("no-live-key@example.com", PASSWORD)), "$.token");

    assertThat(createApiKey(authorization, "test", "test")).hasStatus(201);
    assertThat(createApiKey(authorization, "live", "live"))
        .hasStatus(403)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo(ErrorCode.LIVE_ENVIRONMENT_LOCKED.getCode());
    assertThat(createApiKey(authorization, null, "live"))
        .hasStatus(403)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo(ErrorCode.LIVE_ENVIRONMENT_LOCKED.getCode());
  }

  @Test
  void 필수_동의가_빠지거나_겹치거나_모르는_문서면_400_validation_error이고_아무것도_만들지_않는다() {
    List<String> invalidConsents =
        List.of(
            "[" + TERMS + "]",
            "[" + PRIVACY + "]",
            "[]",
            "[" + TERMS + "," + TERMS + "]",
            "[" + TERMS + "," + PRIVACY + "," + PRIVACY + "]",
            "[" + TERMS + ",{\"document\":\"marketing\",\"document_version\":\"1\"}]",
            "[" + TERMS + ",{\"document\":\"privacy_policy\",\"document_version\":\"\"}]",
            "[null," + TERMS + "," + PRIVACY + "]",
            "[" + TERMS + ",null]");

    for (String consents : invalidConsents) {
      assertThat(signup("consent@example.com", PASSWORD, consents))
          .as(consents)
          .hasStatus(400)
          .bodyJson()
          .extractingPath("$.code")
          .isEqualTo(ErrorCode.VALIDATION_ERROR.getCode());
    }
    entityManager.flush();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM user_account WHERE email = 'consent@example.com'",
                Integer.class))
        .isZero();
  }

  @Test
  void 동의_목록이_없으면_400_validation_error다() {
    assertThat(
            mvc.post()
                .uri(SIGNUPS)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"organization_name":"가입 도입사","name":"가입자",
                     "email":"no-consents@example.com","password":"%s"}
                    """
                        .formatted(PASSWORD))
                .exchange())
        .hasStatus(400)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo(ErrorCode.VALIDATION_ERROR.getCode());
  }

  @Test
  void 같은_이메일이면_대소문자를_가리지_않고_409_email_already_exists다() {
    assertThat(signup("Taken@Example.com", PASSWORD, BOTH_CONSENTS)).hasStatus(201);

    assertThat(signup("taken@EXAMPLE.COM", PASSWORD, BOTH_CONSENTS))
        .hasStatus(409)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS.getCode());
    entityManager.flush();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM user_account WHERE lower(email) = 'taken@example.com'",
                Integer.class))
        .isOne();
  }

  @Test
  void 비밀번호가_8자보다_짧거나_72자나_72바이트보다_길면_400_validation_error다() {
    List<String> invalidPasswords = List.of("a".repeat(7), "a".repeat(73), "가".repeat(25));

    for (String password : invalidPasswords) {
      assertThat(signup("password@example.com", password, BOTH_CONSENTS))
          .as(password)
          .hasStatus(400)
          .bodyJson()
          .extractingPath("$.code")
          .isEqualTo(ErrorCode.VALIDATION_ERROR.getCode());
    }
  }

  @Test
  void 비밀번호는_8자와_72자를_받는다() {
    assertThat(signup("eight@example.com", "a".repeat(8), BOTH_CONSENTS)).hasStatus(201);
    assertThat(signup("seventy-two@example.com", "a".repeat(72), BOTH_CONSENTS)).hasStatus(201);
  }

  @Test
  void 이메일_형식이_틀리거나_이름이_비면_400_validation_error다() {
    for (String content :
        List.of(
            requestBody("가입 도입사", "가입자", "not-an-email", PASSWORD, BOTH_CONSENTS),
            requestBody("", "가입자", "blank-org@example.com", PASSWORD, BOTH_CONSENTS),
            requestBody("가입 도입사", "", "blank-name@example.com", PASSWORD, BOTH_CONSENTS))) {
      assertThat(
              mvc.post()
                  .uri(SIGNUPS)
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
  void 이메일_인증과_캡차가_꺼져_있으면_turnstile_token_없이_가입하고_바로_인증된_계정이_된다() {
    MvcTestResult result = signup("no-captcha@example.com", PASSWORD, BOTH_CONSENTS);

    assertThat(result).hasStatus(201);
    entityManager.flush();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT email_verified_at IS NOT NULL FROM user_account WHERE email = ?",
                Boolean.class,
                "no-captcha@example.com"))
        .isTrue();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM email_verification v JOIN user_account u ON u.id = v.user_account_id WHERE u.email = ?",
                Integer.class,
                "no-captcha@example.com"))
        .isZero();
  }

  private MvcTestResult signup(String email, String password, String consents) {
    return mvc.post()
        .uri(SIGNUPS)
        .contentType(MediaType.APPLICATION_JSON)
        .content(requestBody("가입 도입사", "가입자", email, password, consents))
        .exchange();
  }

  private MvcTestResult customers(String authorization, String environment) {
    var request = mvc.get().uri("/v1/customers").header(AUTHORIZATION, authorization);
    if (environment != null) request = request.header("X-MeterEngine-Environment", environment);
    return request.exchange();
  }

  private MvcTestResult createApiKey(String authorization, String environment, String mode) {
    var request =
        mvc.post()
            .uri("/v1/api-keys")
            .header(AUTHORIZATION, authorization)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"label\":\"키\",\"mode\":\"%s\",\"scopes\":[\"read\"]}".formatted(mode));
    if (environment != null) request = request.header("X-MeterEngine-Environment", environment);
    return request.exchange();
  }

  private static String requestBody(
      String organizationName, String name, String email, String password, String consents) {
    return """
        {"organization_name":"%s","name":"%s","email":"%s","password":"%s","consents":%s}
        """
        .formatted(organizationName, name, email, password, consents);
  }

  private MvcTestResult login(String email, String password) {
    return mvc.post()
        .uri("/v1/sessions")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password))
        .exchange();
  }

  private static String body(MvcTestResult result) {
    try {
      return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    } catch (UnsupportedEncodingException e) {
      throw new IllegalStateException("응답 본문을 읽지 못했다", e);
    }
  }
}
