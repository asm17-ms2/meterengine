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
  void 가입하면_201과_pending_도입사와_member를_돌려주고_소속과_동의_행을_남긴다() {
    MvcTestResult result = signup("New.User@Example.com", PASSWORD, BOTH_CONSENTS);

    assertThat(result).hasStatus(201);
    String body = body(result);
    UUID organizationId = UUID.fromString(JsonPath.read(body, "$.organization.id"));
    UUID userAccountId = UUID.fromString(JsonPath.read(body, "$.user_account.id"));
    assertThat((Map<String, Object>) JsonPath.read(body, "$.organization"))
        .containsExactlyInAnyOrderEntriesOf(
            Map.of("id", organizationId.toString(), "name", "가입 도입사", "status", "pending"));
    assertThat((Map<String, Object>) JsonPath.read(body, "$.user_account"))
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(
                "id", userAccountId.toString(),
                "email", "new.user@example.com",
                "name", "가입자",
                "role", "member"));
    entityManager.flush();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT status FROM organization WHERE id = ?", String.class, organizationId))
        .isEqualTo("pending");
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
  void 가입한_member는_승인_전에_로그인하면_403_organization_not_active다() {
    assertThat(signup("pending@example.com", PASSWORD, BOTH_CONSENTS)).hasStatus(201);

    assertThat(login("pending@example.com", PASSWORD))
        .hasStatus(403)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo(ErrorCode.ORGANIZATION_NOT_ACTIVE.getCode());
  }

  @Test
  void operator가_승인하면_가입한_member가_로그인하고_현재_도입사를_조회한다() {
    MvcTestResult signup = signup("approved@example.com", PASSWORD, BOTH_CONSENTS);
    assertThat(signup).hasStatus(201);
    String organizationId = JsonPath.read(body(signup), "$.organization.id");
    String operator = TestCredentials.issueOperatorSession(jdbcTemplate);

    assertThat(
            mvc.put()
                .uri("/v1/organizations/{id}/status", organizationId)
                .header(AUTHORIZATION, operator)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"active\"}")
                .exchange())
        .hasStatusOk();

    MvcTestResult login = login("approved@example.com", PASSWORD);
    assertThat(login).hasStatus(201);
    String authorization = TestCredentials.BEARER_PREFIX + JsonPath.read(body(login), "$.token");
    MvcTestResult organization =
        mvc.get().uri("/v1/organization").header(AUTHORIZATION, authorization).exchange();
    assertThat(organization)
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.id")
        .isEqualTo(organizationId);
    assertThat(organization).bodyJson().extractingPath("$.status").isEqualTo("active");
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

  private MvcTestResult signup(String email, String password, String consents) {
    return mvc.post()
        .uri(SIGNUPS)
        .contentType(MediaType.APPLICATION_JSON)
        .content(requestBody("가입 도입사", "가입자", email, password, consents))
        .exchange();
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
