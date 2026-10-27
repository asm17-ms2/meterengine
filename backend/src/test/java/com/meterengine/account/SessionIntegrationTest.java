package com.meterengine.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpHeaders.WWW_AUTHENTICATE;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.jayway.jsonpath.JsonPath;
import com.meterengine.TestCredentials;
import com.meterengine.TestcontainersConfiguration;
import com.meterengine.account.service.SessionService;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.security.AuthenticatedCredential;
import com.meterengine.global.security.AuthenticatedPrincipal;
import com.meterengine.global.security.PrincipalKind;
import jakarta.persistence.EntityManager;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class SessionIntegrationTest {

  private static final String PASSWORD = "correct-password";

  private static final String SESSIONS = "/v1/sessions";

  private static final String CURRENT_SESSION = "/v1/sessions/current";

  private static final String CURRENT_SESSION_ORGANIZATION = "/v1/sessions/current/organization";

  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private SessionService sessionService;
  @Autowired private EntityManager entityManager;

  private MockMvcTester mvc;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcTester.from(
            webApplicationContext, builder -> builder.apply(springSecurity()).build());
  }

  // --- 로그인 ---

  @Test
  void 로그인하면_201과_세션_토큰과_사용자와_가장_먼저_소속된_활성_도입사를_돌려준다() {
    UUID suspended = insertOrganization("정지된 도입사", "suspended");
    UUID first = insertOrganization("첫 활성 도입사", "active");
    UUID second = insertOrganization("둘째 활성 도입사", "active");
    UUID userAccountId = insertUserAccount("first@example.com", "member");
    insertMember(suspended, userAccountId, "2026-01-01T00:00:00Z");
    insertMember(second, userAccountId, "2026-01-03T00:00:00Z");
    insertMember(first, userAccountId, "2026-01-02T00:00:00Z");

    MvcTestResult result = login("first@example.com", PASSWORD);

    assertThat(result).hasStatus(201);
    String body = body(result);
    String token = JsonPath.read(body, "$.token");
    assertThat(token).matches("^me_session_[A-Za-z0-9]{32}$");
    assertThat(OffsetDateTime.parse(JsonPath.read(body, "$.expires_at")))
        .isBetween(
            OffsetDateTime.now().plus(Duration.ofHours(12)).minusMinutes(1),
            OffsetDateTime.now().plus(Duration.ofHours(12)).plusMinutes(1));
    assertThat((Map<String, Object>) JsonPath.read(body, "$.user_account"))
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(
                "id", userAccountId.toString(),
                "email", "first@example.com",
                "name", "세션 테스트 사용자",
                "role", "member"));
    assertThat((Map<String, Object>) JsonPath.read(body, "$.organization"))
        .containsExactlyInAnyOrderEntriesOf(
            Map.of("id", first.toString(), "name", "첫 활성 도입사", "status", "active"));
    entityManager.flush();
    assertThat(
            jdbcTemplate.queryForMap(
                "SELECT user_account_id, organization_id, revoked_at FROM user_session WHERE token_hash = sha256(convert_to(?, 'UTF8'))",
                token))
        .containsEntry("user_account_id", userAccountId)
        .containsEntry("organization_id", first)
        .containsEntry("revoked_at", null);
  }

  @Test
  void 틀린_비밀번호와_없는_이메일은_같은_401_invalid_credentials이고_감사하지_않는다() {
    UUID organizationId = insertOrganization("로그인 실패 도입사", "active");
    UUID userAccountId = insertUserAccount("wrong@example.com", "member");
    insertMember(organizationId, userAccountId, "2026-01-01T00:00:00Z");

    MvcTestResult wrongPassword = login("wrong@example.com", "not-the-password");
    MvcTestResult unknownEmail = login("nobody@example.com", PASSWORD);

    for (MvcTestResult result : List.of(wrongPassword, unknownEmail)) {
      assertThat(result).hasStatus(401).hasHeader(WWW_AUTHENTICATE, "Bearer");
      assertThat(result)
          .bodyJson()
          .extractingPath("$.code")
          .isEqualTo(ErrorCode.INVALID_CREDENTIALS.getCode());
    }
    assertThat((String) JsonPath.read(body(unknownEmail), "$.message"))
        .isEqualTo(JsonPath.read(body(wrongPassword), "$.message"));
    entityManager.flush();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_log WHERE actor_id = ? OR organization_id = ?",
                Integer.class,
                userAccountId,
                organizationId))
        .isZero();
  }

  @Test
  void 로그인_이메일은_대소문자를_가리지_않는다() {
    UUID organizationId = insertOrganization("대소문자 도입사", "active");
    insertMember(
        organizationId, insertUserAccount("Mixed.Case@Example.com", "member"), "2026-01-01T00:00Z");

    assertThat(login("mixed.case@EXAMPLE.com", PASSWORD)).hasStatus(201);
  }

  @Test
  void 로그인_본문이_비면_400_validation_error다() {
    assertThat(
            mvc.post()
                .uri(SESSIONS)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .exchange())
        .hasStatus(400)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo(ErrorCode.VALIDATION_ERROR.getCode());
  }

  @Test
  void 활성_소속이_없는_member는_로그인하면_403_organization_not_active다() {
    UUID suspended = insertOrganization("정지된 도입사", "suspended");
    UUID suspendedMember = insertUserAccount("suspended@example.com", "member");
    insertMember(suspended, suspendedMember, "2026-01-01T00:00:00Z");
    insertUserAccount("lonely@example.com", "member");

    for (String email : List.of("suspended@example.com", "lonely@example.com")) {
      assertThat(login(email, PASSWORD))
          .as(email)
          .hasStatus(403)
          .bodyJson()
          .extractingPath("$.code")
          .isEqualTo(ErrorCode.ORGANIZATION_NOT_ACTIVE.getCode());
    }
  }

  @Test
  void operator_로그인은_도입사가_null이고_고객_목록은_403_permission_denied다() {
    insertUserAccount("operator@example.com", "operator");

    MvcTestResult result = login("operator@example.com", PASSWORD);

    assertThat(result).hasStatus(201).bodyJson().extractingPath("$.organization").isNull();
    assertThat(result).bodyJson().extractingPath("$.user_account.role").isEqualTo("operator");
    assertThat(mvc.get().uri("/v1/customers").header(AUTHORIZATION, tokenOf(result)).exchange())
        .hasStatus(403)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo(ErrorCode.PERMISSION_DENIED.getCode());
  }

  // --- 세션으로 요청 ---

  @Test
  void 로그인한_세션_토큰으로_고객_목록을_조회한다() {
    UUID organizationId = insertOrganization("세션 도입사", "active");
    insertMember(
        organizationId, insertUserAccount("customers@example.com", "member"), "2026-01-01T00:00Z");
    insertCustomer(organizationId, "세션 고객");

    String authorization = tokenOf(login("customers@example.com", PASSWORD));

    assertThat(mvc.get().uri("/v1/customers").header(AUTHORIZATION, authorization).exchange())
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.customers[*].name")
        .asArray()
        .containsExactly("세션 고객");
  }

  @Test
  void 세션_주체는_SESSION_종류이고_모드가_없고_세_스코프와_역할_권한을_갖는다() {
    UUID organizationId = insertOrganization("주체 도입사", "active");
    UUID userAccountId = insertUserAccount("principal@example.com", "member");
    insertMember(organizationId, userAccountId, "2026-01-01T00:00:00Z");
    String authorization = tokenOf(login("principal@example.com", PASSWORD));

    AuthenticatedCredential credential =
        sessionService.authenticate(TestCredentials.rawKeyOf(authorization)).orElseThrow();
    AuthenticatedPrincipal principal = credential.principal();

    assertThat(principal.kind()).isEqualTo(PrincipalKind.SESSION);
    assertThat(principal.mode()).isNull();
    assertThat(principal.apiKeyId()).isNull();
    assertThat(principal.userAccountId()).isEqualTo(userAccountId);
    assertThat(principal.userSessionId()).isEqualTo(sessionIdOf(authorization));
    assertThat(principal.organizationId()).isEqualTo(organizationId);
    assertThat(principal.role()).isEqualTo("member");
    assertThat(principal.scopes()).containsExactly("admin");
    assertThat(principal.authorities())
        .extracting(GrantedAuthority::getAuthority)
        .containsExactlyInAnyOrder("SCOPE_ingest", "SCOPE_read", "SCOPE_admin", "ROLE_member");
  }

  @Test
  void 모르거나_형식이_틀린_세션_토큰은_인증하지_않는다() {
    assertThat(sessionService.authenticate("me_session_" + "a".repeat(32))).isEmpty();
    assertThat(sessionService.authenticate("me_session_" + "a".repeat(31))).isEmpty();
    assertThat(sessionService.authenticate("me_live_" + "a".repeat(32))).isEmpty();
  }

  @Test
  void 만료된_세션_토큰은_401_invalid_credentials다() {
    UUID organizationId = insertOrganization("만료 도입사", "active");
    insertMember(
        organizationId, insertUserAccount("expired@example.com", "member"), "2026-01-01T00:00Z");
    String authorization = tokenOf(login("expired@example.com", PASSWORD));
    entityManager.flush();
    entityManager.clear();
    jdbcTemplate.update(
        "UPDATE user_session SET expires_at = now() - interval '1 second' WHERE token_hash = sha256(convert_to(?, 'UTF8'))",
        TestCredentials.rawKeyOf(authorization));

    for (String uri : List.of("/v1/customers", CURRENT_SESSION)) {
      assertThat(mvc.get().uri(uri).header(AUTHORIZATION, authorization).exchange())
          .as(uri)
          .hasStatus(401)
          .bodyJson()
          .extractingPath("$.code")
          .isEqualTo(ErrorCode.INVALID_CREDENTIALS.getCode());
    }
  }

  // --- 현재 세션 ---

  @Test
  void 현재_세션은_사용자와_도입사와_소속_도입사_목록과_만료_시각을_돌려준다() {
    UUID first = insertOrganization("첫 도입사", "active");
    UUID second = insertOrganization("둘째 도입사", "suspended");
    UUID userAccountId = insertUserAccount("current@example.com", "member");
    insertMember(first, userAccountId, "2026-01-01T00:00:00Z");
    insertMember(second, userAccountId, "2026-01-02T00:00:00Z");
    MvcTestResult login = login("current@example.com", PASSWORD);

    MvcTestResult result =
        mvc.get().uri(CURRENT_SESSION).header(AUTHORIZATION, tokenOf(login)).exchange();

    assertThat(result).hasStatusOk();
    String body = body(result);
    assertThat((Map<String, Object>) JsonPath.read(body, "$.user_account"))
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(
                "id", userAccountId.toString(),
                "email", "current@example.com",
                "name", "세션 테스트 사용자",
                "role", "member"));
    assertThat((String) JsonPath.read(body, "$.organization.id")).isEqualTo(first.toString());
    assertThat((List<Map<String, Object>>) JsonPath.read(body, "$.organizations"))
        .containsExactly(
            Map.of("id", first.toString(), "name", "첫 도입사", "status", "active"),
            Map.of("id", second.toString(), "name", "둘째 도입사", "status", "suspended"));
    assertThat(OffsetDateTime.parse(JsonPath.read(body, "$.expires_at")))
        .isAtSameInstantAs(OffsetDateTime.parse(JsonPath.read(body(login), "$.expires_at")));
  }

  @Test
  void 인증_없이_현재_세션을_조회하면_401_authentication_required다() {
    assertThat(mvc.get().uri(CURRENT_SESSION).exchange())
        .hasStatus(401)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo(ErrorCode.AUTHENTICATION_REQUIRED.getCode());
  }

  @Test
  void API_키로_세션_경로를_부르면_403_permission_denied다() {
    String authorization = TestCredentials.issueApiKey(jdbcTemplate, UUID.randomUUID());

    for (MvcTestResult result :
        List.of(
            mvc.get().uri(CURRENT_SESSION).header(AUTHORIZATION, authorization).exchange(),
            mvc.delete().uri(CURRENT_SESSION).header(AUTHORIZATION, authorization).exchange(),
            switchOrganization(authorization, UUID.randomUUID()))) {
      assertThat(result)
          .hasStatus(403)
          .bodyJson()
          .extractingPath("$.code")
          .isEqualTo(ErrorCode.PERMISSION_DENIED.getCode());
    }
  }

  // --- 로그아웃 ---

  @Test
  void 로그아웃하면_204이고_같은_토큰은_401_invalid_credentials다() {
    UUID organizationId = insertOrganization("로그아웃 도입사", "active");
    insertMember(
        organizationId, insertUserAccount("logout@example.com", "member"), "2026-01-01T00:00Z");
    String authorization = tokenOf(login("logout@example.com", PASSWORD));

    assertThat(mvc.delete().uri(CURRENT_SESSION).header(AUTHORIZATION, authorization).exchange())
        .hasStatus(204);

    for (String uri : List.of("/v1/customers", CURRENT_SESSION)) {
      assertThat(mvc.get().uri(uri).header(AUTHORIZATION, authorization).exchange())
          .as(uri)
          .hasStatus(401)
          .bodyJson()
          .extractingPath("$.code")
          .isEqualTo(ErrorCode.INVALID_CREDENTIALS.getCode());
    }
    entityManager.flush();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT revoked_at FROM user_session WHERE id = ?",
                OffsetDateTime.class,
                sessionIdOf(authorization)))
        .isNotNull();
  }

  // --- 도입사 전환 ---

  @Test
  void 두_도입사에_소속된_member가_도입사를_바꾸면_고객_목록이_바뀐다() {
    UUID first = insertOrganization("첫 도입사", "active");
    UUID second = insertOrganization("둘째 도입사", "active");
    UUID userAccountId = insertUserAccount("switch@example.com", "member");
    insertMember(first, userAccountId, "2026-01-01T00:00:00Z");
    insertMember(second, userAccountId, "2026-01-02T00:00:00Z");
    insertCustomer(first, "첫 도입사 고객");
    insertCustomer(second, "둘째 도입사 고객");
    String authorization = tokenOf(login("switch@example.com", PASSWORD));
    assertThat(customerNames(authorization)).containsExactly("첫 도입사 고객");

    MvcTestResult result = switchOrganization(authorization, second);

    assertThat(result)
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.organization.id")
        .isEqualTo(second.toString());
    assertThat(result).bodyJson().extractingPath("$.organizations").asArray().hasSize(2);
    assertThat(result)
        .bodyJson()
        .extractingPath("$.user_account.id")
        .isEqualTo(userAccountId.toString());
    entityManager.flush();
    entityManager.clear();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT organization_id FROM user_session WHERE user_account_id = ?",
                UUID.class,
                userAccountId))
        .isEqualTo(second);
    assertThat(customerNames(authorization)).containsExactly("둘째 도입사 고객");
  }

  @Test
  void 소속이_아닌_도입사로_바꾸면_403_permission_denied이고_세션의_도입사는_그대로다() {
    UUID mine = insertOrganization("내 도입사", "active");
    UUID other = insertOrganization("남의 도입사", "active");
    insertMember(mine, insertUserAccount("stranger@example.com", "member"), "2026-01-01T00:00Z");
    insertCustomer(mine, "내 고객");
    String authorization = tokenOf(login("stranger@example.com", PASSWORD));

    for (UUID target : List.of(other, UUID.randomUUID())) {
      assertThat(switchOrganization(authorization, target))
          .hasStatus(403)
          .bodyJson()
          .extractingPath("$.code")
          .isEqualTo(ErrorCode.PERMISSION_DENIED.getCode());
    }
    assertThat(customerNames(authorization)).containsExactly("내 고객");
  }

  @Test
  void 활성이_아닌_소속_도입사로도_바꾸고_도입사_범위_경로는_403_organization_not_active다() {
    UUID active = insertOrganization("활성 도입사", "active");
    UUID suspended = insertOrganization("정지된 도입사", "suspended");
    UUID userAccountId = insertUserAccount("inactive-switch@example.com", "member");
    insertMember(active, userAccountId, "2026-01-01T00:00:00Z");
    insertMember(suspended, userAccountId, "2026-01-02T00:00:00Z");
    String authorization = tokenOf(login("inactive-switch@example.com", PASSWORD));

    assertThat(switchOrganization(authorization, suspended)).hasStatusOk();

    assertThat(mvc.get().uri("/v1/customers").header(AUTHORIZATION, authorization).exchange())
        .hasStatus(403)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo(ErrorCode.ORGANIZATION_NOT_ACTIVE.getCode());
    assertThat(mvc.get().uri(CURRENT_SESSION).header(AUTHORIZATION, authorization).exchange())
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.organization.status")
        .isEqualTo("suspended");
  }

  @Test
  void 전환_본문에_도입사가_없으면_400_validation_error다() {
    String authorization = TestCredentials.issueMemberSession(jdbcTemplate, UUID.randomUUID());

    assertThat(
            mvc.put()
                .uri(CURRENT_SESSION_ORGANIZATION)
                .header(AUTHORIZATION, authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .exchange())
        .hasStatus(400)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo(ErrorCode.VALIDATION_ERROR.getCode());
  }

  // --- 감사 ---

  @Test
  void 로그인과_도입사_전환과_로그아웃은_감사_행을_남기고_토큰_원문은_남기지_않는다() {
    UUID first = insertOrganization("첫 도입사", "active");
    UUID second = insertOrganization("둘째 도입사", "active");
    UUID userAccountId = insertUserAccount("audit@example.com", "member");
    insertMember(first, userAccountId, "2026-01-01T00:00:00Z");
    insertMember(second, userAccountId, "2026-01-02T00:00:00Z");
    String authorization = tokenOf(login("audit@example.com", PASSWORD));
    UUID sessionId = sessionIdOf(authorization);

    assertThat(switchOrganization(authorization, second)).hasStatusOk();
    assertThat(mvc.delete().uri(CURRENT_SESSION).header(AUTHORIZATION, authorization).exchange())
        .hasStatus(204);
    entityManager.flush();

    List<Map<String, Object>> rows =
        jdbcTemplate.queryForList(
            """
            SELECT action, actor_type, actor_id, organization_id, target_type, target_id,
                   detail::text AS detail
            FROM audit_log WHERE actor_id = ? AND action <> 'api_request_completed' ORDER BY occurred_at, action
            """,
            userAccountId);
    assertThat(rows)
        .extracting(
            row -> row.get("action"),
            row -> row.get("actor_type"),
            row -> row.get("organization_id"),
            row -> row.get("target_type"),
            row -> row.get("target_id"))
        .containsExactlyInAnyOrder(
            tuple("session_created", "user_account", first, "user_session", sessionId.toString()),
            tuple(
                "session_organization_updated",
                "user_account",
                first,
                "user_session",
                sessionId.toString()),
            tuple("session_deleted", "user_account", second, "user_session", sessionId.toString()));
    assertThat(rows)
        .extracting(row -> (String) row.get("detail"))
        .noneMatch(detail -> detail.contains(TestCredentials.rawKeyOf(authorization)));
  }

  // --- 테스트 자격 증명 ---

  @Test
  void TestCredentials의_member_세션과_operator_세션이_동작한다() {
    UUID organizationId = UUID.randomUUID();
    String member = TestCredentials.issueMemberSession(jdbcTemplate, organizationId);
    String operator = TestCredentials.issueOperatorSession(jdbcTemplate);

    assertThat(mvc.get().uri("/v1/customers").header(AUTHORIZATION, member).exchange())
        .hasStatusOk();
    assertThat(mvc.get().uri(CURRENT_SESSION).header(AUTHORIZATION, member).exchange())
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.organization.id")
        .isEqualTo(organizationId.toString());
    MvcTestResult operatorSession =
        mvc.get().uri(CURRENT_SESSION).header(AUTHORIZATION, operator).exchange();
    assertThat(operatorSession).hasStatusOk().bodyJson().extractingPath("$.organization").isNull();
    assertThat(operatorSession)
        .bodyJson()
        .extractingPath("$.user_account.role")
        .isEqualTo("operator");
  }

  // --- 헬퍼 ---

  private MvcTestResult login(String email, String password) {
    return mvc.post()
        .uri(SESSIONS)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password))
        .exchange();
  }

  private MvcTestResult switchOrganization(String authorization, UUID organizationId) {
    return mvc.put()
        .uri(CURRENT_SESSION_ORGANIZATION)
        .header(AUTHORIZATION, authorization)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"organization_id\":\"%s\"}".formatted(organizationId))
        .exchange();
  }

  private List<String> customerNames(String authorization) {
    MvcTestResult result =
        mvc.get().uri("/v1/customers").header(AUTHORIZATION, authorization).exchange();
    assertThat(result).hasStatusOk();
    return JsonPath.read(body(result), "$.customers[*].name");
  }

  private String tokenOf(MvcTestResult login) {
    assertThat(login).hasStatus(201);
    return TestCredentials.BEARER_PREFIX + JsonPath.read(body(login), "$.token");
  }

  private UUID sessionIdOf(String authorization) {
    entityManager.flush();
    return jdbcTemplate.queryForObject(
        "SELECT id FROM user_session WHERE token_hash = sha256(convert_to(?, 'UTF8'))",
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

  private UUID insertUserAccount(String email, String role) {
    UUID userAccountId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO user_account (id, email, password_hash, name, role, created_at)
        VALUES (?, ?, ?, '세션 테스트 사용자', ?, now())
        """,
        userAccountId,
        email,
        passwordEncoder.encode(PASSWORD),
        role);
    return userAccountId;
  }

  private void insertMember(UUID organizationId, UUID userAccountId, String createdAt) {
    jdbcTemplate.update(
        "INSERT INTO organization_member (organization_id, user_account_id, created_at) VALUES (?, ?, CAST(? AS TIMESTAMPTZ))",
        organizationId,
        userAccountId,
        createdAt);
  }

  private void insertCustomer(UUID organizationId, String name) {
    jdbcTemplate.update(
        "INSERT INTO customer (organization_id, name) VALUES (?, ?)", organizationId, name);
  }

  private static String body(MvcTestResult result) {
    try {
      return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    } catch (UnsupportedEncodingException e) {
      throw new IllegalStateException("응답 본문을 읽지 못했다", e);
    }
  }
}
