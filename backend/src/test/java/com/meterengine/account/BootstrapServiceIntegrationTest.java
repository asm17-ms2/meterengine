package com.meterengine.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.jayway.jsonpath.JsonPath;
import com.meterengine.TestCredentials;
import com.meterengine.TestcontainersConfiguration;
import com.meterengine.account.config.BootstrapProperties;
import com.meterengine.account.repository.UserAccountRepository;
import com.meterengine.account.repository.UserSessionRepository;
import com.meterengine.account.service.BootstrapService;
import com.meterengine.apikey.repository.ApiKeyRepository;
import com.meterengine.apikey.service.ApiKeyService;
import com.meterengine.audit.service.AuditLogService;
import com.meterengine.organization.repository.OrganizationRepository;
import jakarta.persistence.EntityManager;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
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
@ExtendWith(OutputCaptureExtension.class)
class BootstrapServiceIntegrationTest {

  private static final String PASSWORD = "bootstrap-password";

  private static final String NEW_PASSWORD = "bootstrap-new-password";

  private static final String ALPHABET =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

  private static final SecureRandom RANDOM = new SecureRandom();

  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private EntityManager entityManager;
  @Autowired private UserAccountRepository userAccountRepository;
  @Autowired private UserSessionRepository userSessionRepository;
  @Autowired private ApiKeyRepository apiKeyRepository;
  @Autowired private OrganizationRepository organizationRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private AuditLogService auditLogService;
  @Autowired private ApiKeyService apiKeyService;

  private MockMvcTester mvc;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcTester.from(
            webApplicationContext, builder -> builder.apply(springSecurity()).build());
  }

  @Test
  void 처음_실행하면_운영자와_콘솔_키를_만들고_그_운영자로_로그인하고_그_키로_고객을_조회한다() {
    UUID organizationId = insertOrganization();
    String email = uniqueEmail();
    String rawKey = randomKey("test");

    bootstrap(email, PASSWORD, rawKey, organizationId.toString());

    entityManager.flush();
    Map<String, Object> operator =
        jdbcTemplate.queryForMap(
            "SELECT id, email, name, role, password_hash FROM user_account WHERE email = ?",
            email.toLowerCase(Locale.ROOT));
    assertThat(operator).containsEntry("name", "운영자").containsEntry("role", "operator");
    assertThat((String) operator.get("password_hash")).startsWith("{bcrypt}");
    UUID operatorId = (UUID) operator.get("id");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM organization_member WHERE user_account_id = ?",
                Long.class,
                operatorId))
        .isZero();
    Map<String, Object> apiKey =
        jdbcTemplate.queryForMap(
            """
            SELECT id, organization_id, mode, label, last_four, array_to_string(scopes, ',') AS scopes,
                   revoked_at, expires_at
            FROM api_key WHERE key_hash = sha256(convert_to(?, 'UTF8'))
            """,
            rawKey);
    assertThat(apiKey)
        .containsEntry("organization_id", organizationId)
        .containsEntry("mode", "test")
        .containsEntry("label", "프론트 콘솔")
        .containsEntry("last_four", rawKey.substring(rawKey.length() - 4))
        .containsEntry("scopes", "admin")
        .containsEntry("revoked_at", null)
        .containsEntry("expires_at", null);
    assertThat(auditRows(operatorId.toString()))
        .containsExactly(List.of("user_account_created", "system", "null", "user_account"));
    assertThat(auditRows(apiKey.get("id").toString()))
        .containsExactly(
            List.of("api_key_created", "system", organizationId.toString(), "api_key"));

    assertThat(login(email, PASSWORD)).hasStatus(201);
    assertThat(
            mvc.get()
                .uri("/v1/customers")
                .header(AUTHORIZATION, TestCredentials.BEARER_PREFIX + rawKey)
                .exchange())
        .hasStatus(200);
  }

  @Test
  void 같은_값으로_다시_실행하면_행이_늘지_않고_비밀번호_해시도_그대로다() {
    UUID organizationId = insertOrganization();
    String email = uniqueEmail();
    String rawKey = randomKey("live");
    bootstrap(email, PASSWORD, rawKey, organizationId.toString());
    entityManager.flush();
    String hashBefore = passwordHashOf(email);
    Map<String, Long> countsBefore = rowCounts();

    bootstrap(email, PASSWORD, rawKey, organizationId.toString());

    entityManager.flush();
    assertThat(rowCounts()).isEqualTo(countsBefore);
    assertThat(passwordHashOf(email)).isEqualTo(hashBefore);
  }

  @Test
  void 비밀번호를_바꿔_다시_실행하면_새_비밀번호로_로그인되고_감사_행이_남는다() {
    String email = uniqueEmail();
    bootstrap(email, PASSWORD, "", "");
    entityManager.flush();
    UUID operatorId = operatorIdOf(email);

    bootstrap(email, NEW_PASSWORD, "", "");

    assertThat(login(email, NEW_PASSWORD)).hasStatus(201);
    assertThat(login(email, PASSWORD)).hasStatus(401);
    entityManager.flush();
    assertThat(auditRows(operatorId.toString()))
        .containsExactly(
            List.of("user_account_created", "system", "null", "user_account"),
            List.of("user_account_password_updated", "system", "null", "user_account"));
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM user_account WHERE lower(email) = lower(?)",
                Long.class,
                email))
        .isOne();
  }

  @Test
  void 비밀번호를_바꿔_다시_실행하면_그_전에_만든_세션은_폐기되어_401이고_세션마다_감사_행이_남는다() {
    String email = uniqueEmail();
    bootstrap(email, PASSWORD, "", "");
    entityManager.flush();
    String authorization = tokenOf(login(email, PASSWORD));
    UUID sessionId = sessionIdOf(authorization);
    assertThat(
            mvc.get().uri("/v1/sessions/current").header(AUTHORIZATION, authorization).exchange())
        .hasStatusOk();

    bootstrap(email, NEW_PASSWORD, "", "");
    entityManager.flush();

    assertThat(
            mvc.get().uri("/v1/sessions/current").header(AUTHORIZATION, authorization).exchange())
        .hasStatus(401)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("invalid_credentials");
    assertThat(auditRows(sessionId.toString()))
        .containsExactly(
            List.of("session_created", "user_account", "null", "user_session"),
            List.of("session_deleted", "system", "null", "user_session"));
    String newAuthorization = tokenOf(login(email, NEW_PASSWORD));
    assertThat(
            mvc.get()
                .uri("/v1/sessions/current")
                .header(AUTHORIZATION, newAuthorization)
                .exchange())
        .hasStatusOk();
  }

  @Test
  void 이미_있는_사용자는_대소문자를_가리지_않고_찾아_비밀번호만_바꾸고_역할은_그대로_둔다() {
    String email = uniqueEmail();
    UUID memberId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO user_account (id, email, password_hash, name, role, created_at)
        VALUES (?, ?, ?, '기존 사용자', 'member', now())
        """,
        memberId,
        email.toLowerCase(Locale.ROOT),
        passwordEncoder.encode(PASSWORD));

    bootstrap(email.toUpperCase(), NEW_PASSWORD, "", "");

    entityManager.flush();
    assertThat(
            jdbcTemplate.queryForMap("SELECT name, role FROM user_account WHERE id = ?", memberId))
        .containsEntry("name", "기존 사용자")
        .containsEntry("role", "member");
    assertThat(passwordEncoder.matches(NEW_PASSWORD, passwordHashOf(email))).isTrue();
    assertThat(auditRows(memberId.toString()))
        .containsExactly(
            List.of("user_account_password_updated", "system", "null", "user_account"));
  }

  @Test
  void 폐기한_콘솔_키는_다시_실행해도_폐기된_채로_남는다() {
    UUID organizationId = insertOrganization();
    String rawKey = randomKey("live");
    bootstrap("", "", rawKey, organizationId.toString());
    entityManager.flush();
    apiKeyService.delete(
        organizationId,
        jdbcTemplate.queryForObject(
            "SELECT id FROM api_key WHERE key_hash = sha256(convert_to(?, 'UTF8'))",
            UUID.class,
            rawKey));
    entityManager.flush();
    Map<String, Long> countsBefore = rowCounts();

    bootstrap("", "", rawKey, organizationId.toString());

    entityManager.flush();
    assertThat(rowCounts()).isEqualTo(countsBefore);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT revoked_at IS NOT NULL FROM api_key WHERE key_hash = sha256(convert_to(?, 'UTF8'))",
                Boolean.class,
                rawKey))
        .isTrue();
    assertThat(
            mvc.get()
                .uri("/v1/customers")
                .header(AUTHORIZATION, TestCredentials.BEARER_PREFIX + rawKey)
                .exchange())
        .hasStatus(401);
  }

  @Test
  void 형식이_틀린_키는_기동을_거부하고_예외_문구에_원문을_담지_않으며_아무_행도_만들지_않는다() {
    UUID organizationId = insertOrganization();
    String email = uniqueEmail();
    String validKey = randomKey("live");
    String secret = validKey.substring("me_live_".length());
    List<String> malformedKeys =
        List.of(
            "me_prod_" + secret,
            validKey.substring(0, validKey.length() - 1),
            validKey + "A",
            validKey + " ",
            validKey + "\n",
            " " + validKey);
    Map<String, Long> countsBefore = rowCounts();

    for (String malformedKey : malformedKeys) {
      assertThatThrownBy(() -> bootstrap(email, PASSWORD, malformedKey, organizationId.toString()))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("FRONTEND_API_KEY")
          .hasMessageNotContaining(secret.substring(0, 31))
          .hasNoCause();
    }

    entityManager.flush();
    assertThat(rowCounts()).isEqualTo(countsBefore);
  }

  @Test
  void 콘솔_키가_다른_도입사에_있으면_기동을_거부하고_아무_행도_바꾸지_않는다() {
    UUID organizationId = insertOrganization();
    UUID otherOrganizationId = insertOrganization();
    String rawKey = randomKey("live");
    bootstrap("", "", rawKey, organizationId.toString());
    entityManager.flush();
    Map<String, Long> countsBefore = rowCounts();
    String email = uniqueEmail();

    assertThatThrownBy(() -> bootstrap(email, PASSWORD, rawKey, otherOrganizationId.toString()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            "FRONTEND_API_KEY belongs to an organization other than FRONTEND_ORGANIZATION_ID");

    entityManager.flush();
    assertThat(rowCounts()).isEqualTo(countsBefore);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT organization_id FROM api_key WHERE key_hash = sha256(convert_to(?, 'UTF8'))",
                UUID.class,
                rawKey))
        .isEqualTo(organizationId);
  }

  @Test
  void 운영자_비밀번호가_UTF8로_72바이트를_넘으면_기동을_거부하고_72바이트는_받는다() {
    String tooLong = "가".repeat(24) + "a";
    Map<String, Long> countsBefore = rowCounts();

    assertThatThrownBy(() -> bootstrap(uniqueEmail(), tooLong, "", ""))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("OPERATOR_PASSWORD must be at most 72 bytes in UTF-8");
    entityManager.flush();
    assertThat(rowCounts()).isEqualTo(countsBefore);

    String email = uniqueEmail();
    String longest = "가".repeat(24);
    bootstrap(email, longest, "", "");
    assertThat(login(email, longest)).hasStatus(201);
  }

  @Test
  void 운영자가_아닌_사용자와_활성이_아닌_도입사와_폐기된_키는_경고만_남기고_기동한다(CapturedOutput output) {
    String email = uniqueEmail();
    jdbcTemplate.update(
        """
        INSERT INTO user_account (id, email, password_hash, name, role, created_at)
        VALUES (?, ?, ?, '기존 사용자', 'member', now())
        """,
        UUID.randomUUID(),
        email.toLowerCase(Locale.ROOT),
        passwordEncoder.encode(PASSWORD));
    UUID suspendedOrganizationId =
        jdbcTemplate.queryForObject(
            "INSERT INTO organization (name, status) VALUES ('정지된 도입사', 'suspended') RETURNING id",
            UUID.class);
    String rawKey = randomKey("test");

    bootstrap(email, PASSWORD, rawKey, suspendedOrganizationId.toString());
    entityManager.flush();
    apiKeyService.delete(
        suspendedOrganizationId,
        jdbcTemplate.queryForObject(
            "SELECT id FROM api_key WHERE key_hash = sha256(convert_to(?, 'UTF8'))",
            UUID.class,
            rawKey));
    entityManager.flush();
    bootstrap(email, PASSWORD, rawKey, suspendedOrganizationId.toString());

    assertThat(output.getOut())
        .contains(
            "Bootstrap operator email matches user account "
                + email.toLowerCase(Locale.ROOT)
                + " that is not an operator")
        .contains("Bootstrap console organization " + suspendedOrganizationId + " is not active")
        .contains(
            "Bootstrap left frontend console API key ending in "
                + rawKey.substring(rawKey.length() - 4)
                + " as is because it is revoked or expired")
        .doesNotContain(rawKey)
        .doesNotContain(PASSWORD);
  }

  @Test
  void 이메일과_비밀번호_가운데_하나만_있으면_기동을_거부하고_비밀번호를_예외_문구에_담지_않는다() {
    String email = uniqueEmail();

    assertThatThrownBy(() -> bootstrap(email, "", "", ""))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("OPERATOR_EMAIL")
        .hasMessageContaining("OPERATOR_PASSWORD")
        .hasMessageNotContaining(email);
    assertThatThrownBy(() -> bootstrap("", PASSWORD, "", ""))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("OPERATOR_EMAIL")
        .hasMessageContaining("OPERATOR_PASSWORD")
        .hasMessageNotContaining(PASSWORD);
  }

  @Test
  void 키가_있는데_도입사_id가_비었거나_UUID가_아니거나_없는_도입사면_기동을_거부한다() {
    String rawKey = randomKey("live");
    String missingOrganizationId = UUID.randomUUID().toString();

    for (String organizationId : List.of("", "not-a-uuid", missingOrganizationId)) {
      assertThatThrownBy(() -> bootstrap("", "", rawKey, organizationId))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("FRONTEND_ORGANIZATION_ID")
          .hasMessageNotContaining(rawKey)
          .hasNoCause();
    }
    entityManager.flush();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM api_key WHERE key_hash = sha256(convert_to(?, 'UTF8'))",
                Long.class,
                rawKey))
        .isZero();
  }

  @Test
  void 값이_모두_비면_아무_행도_만들지_않는다() {
    Map<String, Long> countsBefore = rowCounts();

    bootstrap("", "", "", "");
    bootstrap(null, null, null, null);

    entityManager.flush();
    assertThat(rowCounts()).isEqualTo(countsBefore);
  }

  private void bootstrap(String email, String password, String apiKey, String organizationId) {
    new BootstrapService(
            new BootstrapProperties(email, password, apiKey, organizationId),
            userAccountRepository,
            userSessionRepository,
            apiKeyRepository,
            organizationRepository,
            passwordEncoder,
            auditLogService)
        .run(new DefaultApplicationArguments());
  }

  private MvcTestResult login(String email, String password) {
    return mvc.post()
        .uri("/v1/sessions")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password))
        .exchange();
  }

  private String tokenOf(MvcTestResult login) {
    assertThat(login).hasStatus(201);
    try {
      return TestCredentials.BEARER_PREFIX
          + JsonPath.read(
              login.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.token");
    } catch (UnsupportedEncodingException e) {
      throw new IllegalStateException("로그인 응답 본문을 읽지 못했다", e);
    }
  }

  private UUID sessionIdOf(String authorization) {
    entityManager.flush();
    return jdbcTemplate.queryForObject(
        "SELECT id FROM user_session WHERE token_hash = sha256(convert_to(?, 'UTF8'))",
        UUID.class,
        TestCredentials.rawKeyOf(authorization));
  }

  private UUID insertOrganization() {
    return jdbcTemplate.queryForObject(
        "INSERT INTO organization (name, status) VALUES ('부트스트랩 도입사', 'active') RETURNING id",
        UUID.class);
  }

  private String passwordHashOf(String email) {
    return jdbcTemplate.queryForObject(
        "SELECT password_hash FROM user_account WHERE lower(email) = lower(?)",
        String.class,
        email);
  }

  private UUID operatorIdOf(String email) {
    return jdbcTemplate.queryForObject(
        "SELECT id FROM user_account WHERE lower(email) = lower(?)", UUID.class, email);
  }

  private List<List<String>> auditRows(String targetId) {
    return jdbcTemplate.query(
        """
        SELECT action, actor_type, organization_id, target_type FROM audit_log
        WHERE target_id = ? ORDER BY occurred_at, action
        """,
        (resultSet, rowNumber) ->
            List.of(
                resultSet.getString("action"),
                resultSet.getString("actor_type"),
                String.valueOf(resultSet.getString("organization_id")),
                resultSet.getString("target_type")),
        targetId);
  }

  private Map<String, Long> rowCounts() {
    return Map.of(
        "user_account", count("user_account"),
        "api_key", count("api_key"),
        "audit_log", count("audit_log"));
  }

  private long count(String table) {
    return jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Long.class);
  }

  private static String uniqueEmail() {
    return "Operator-" + UUID.randomUUID() + "@Bootstrap.MeterEngine.local";
  }

  private static String randomKey(String mode) {
    StringBuilder rawKey = new StringBuilder("me_" + mode + "_");
    for (int index = 0; index < 32; index++) {
      rawKey.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
    }
    return rawKey.toString();
  }
}
