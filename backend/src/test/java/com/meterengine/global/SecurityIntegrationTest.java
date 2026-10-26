package com.meterengine.global;

import static com.meterengine.TestCredentials.issueApiKey;
import static com.meterengine.TestCredentials.rawKeyOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.HttpHeaders.RETRY_AFTER;
import static org.springframework.http.HttpHeaders.WWW_AUTHENTICATE;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.meterengine.TestCredentials;
import com.meterengine.TestcontainersConfiguration;
import com.meterengine.apikey.service.ApiKeyService;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.security.AuthenticatedCredential;
import com.meterengine.global.security.AuthenticatedPrincipal;
import com.meterengine.global.security.PrincipalKind;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.json.JsonMapper;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class SecurityIntegrationTest {

  private static final String VALID_SECRET = "a".repeat(32);

  private static final String LEGACY_ORGANIZATION_HEADER = "X-Organization" + "-Id";

  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private JsonMapper jsonMapper;
  @Autowired private ApiKeyService apiKeyService;

  private MockMvcTester mvc;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcTester.from(
            webApplicationContext, builder -> builder.apply(springSecurity()).build());
  }

  // --- 인증 없음 ---

  @Test
  void Authorization이_없으면_401_authentication_required와_Bearer_챌린지다() {
    MvcTestResult result = mvc.get().uri("/v1/customers").exchange();

    assertThat(result).hasStatus(401).hasHeader(WWW_AUTHENTICATE, "Bearer");
    assertThat(result)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo(ErrorCode.AUTHENTICATION_REQUIRED.getCode());
  }

  @Test
  void 도입사_헤더만_보내면_401_authentication_required다() {
    MvcTestResult result =
        mvc.get()
            .uri("/v1/customers")
            .header(LEGACY_ORGANIZATION_HEADER, insertOrganization().toString())
            .exchange();

    assertThat(result)
        .hasStatus(401)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo(ErrorCode.AUTHENTICATION_REQUIRED.getCode());
  }

  // --- 잘못된 자격 증명 ---

  @Test
  void 형식이_틀리거나_모르는_값이면_401_invalid_credentials와_Bearer_챌린지다() {
    for (String authorization :
        new String[] {
          "Bearer me_live_" + "a".repeat(31),
          "Bearer me_live_" + VALID_SECRET,
          "Bearer me_test_" + VALID_SECRET,
          "Bearer me_prod_" + VALID_SECRET,
          "Bearer abc",
          "Bearer ",
          "Basic xxx",
          ""
        }) {
      MvcTestResult result =
          mvc.get().uri("/v1/customers").header(AUTHORIZATION, authorization).exchange();

      assertThat(result).as(authorization).hasStatus(401).hasHeader(WWW_AUTHENTICATE, "Bearer");
      assertThat(result)
          .as(authorization)
          .bodyJson()
          .extractingPath("$.code")
          .isEqualTo(ErrorCode.INVALID_CREDENTIALS.getCode());
    }
  }

  @Test
  void 모르거나_형식이_틀린_세션_토큰은_401_invalid_credentials다() {
    for (String authorization :
        new String[] {"Bearer me_session_" + VALID_SECRET, "Bearer me_session_" + "a".repeat(31)}) {
      assertThat(mvc.get().uri("/v1/customers").header(AUTHORIZATION, authorization).exchange())
          .as(authorization)
          .hasStatus(401)
          .bodyJson()
          .extractingPath("$.code")
          .isEqualTo(ErrorCode.INVALID_CREDENTIALS.getCode());
    }
  }

  @Test
  void 폐기한_세션_토큰은_401_invalid_credentials다() {
    String authorization = TestCredentials.issueMemberSession(jdbcTemplate, insertOrganization());
    jdbcTemplate.update(
        "UPDATE user_session SET revoked_at = now() WHERE token_hash = sha256(convert_to(?, 'UTF8'))",
        rawKeyOf(authorization));

    assertThat(list(authorization))
        .hasStatus(401)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo(ErrorCode.INVALID_CREDENTIALS.getCode());
  }

  @Test
  void 공개_경로인_로그인은_익명_요청도_핸들러까지_간다() {
    MvcTestResult result =
        mvc.post()
            .uri("/v1/sessions")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"nobody@example.com\",\"password\":\"wrong-password\"}")
            .exchange();

    assertThat(result).hasStatus(401).hasHeader(WWW_AUTHENTICATE, "Bearer");
    assertThat(result)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo(ErrorCode.INVALID_CREDENTIALS.getCode());
  }

  @Test
  void 공개_경로도_잘못된_자격_증명이면_401이다() {
    assertThat(mvc.get().uri("/actuator/health").header(AUTHORIZATION, "Bearer abc").exchange())
        .hasStatus(401)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo(ErrorCode.INVALID_CREDENTIALS.getCode());
  }

  @Test
  void 폐기한_키는_401_invalid_credentials다() {
    String authorization = issueApiKey(jdbcTemplate, insertOrganization());
    jdbcTemplate.update(
        "UPDATE api_key SET revoked_at = now() WHERE key_hash = sha256(convert_to(?, 'UTF8'))",
        rawKeyOf(authorization));

    assertThat(list(authorization))
        .hasStatus(401)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo(ErrorCode.INVALID_CREDENTIALS.getCode());
  }

  @Test
  void 만료된_키는_401_invalid_credentials이고_만료_전이면_통과한다() {
    String expired = issueApiKey(jdbcTemplate, insertOrganization());
    String expiring = issueApiKey(jdbcTemplate, insertOrganization());
    setExpiresAt(expired, "now() - interval '1 second'");
    setExpiresAt(expiring, "now() + interval '1 hour'");

    assertThat(list(expired))
        .hasStatus(401)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo(ErrorCode.INVALID_CREDENTIALS.getCode());
    assertThat(list(expiring)).hasStatusOk();
  }

  // --- 유효한 키 ---

  @Test
  void 유효한_admin_키로_고객_목록과_현재_도입사를_조회한다() {
    UUID organizationId = insertOrganization();
    String authorization = issueApiKey(jdbcTemplate, organizationId);

    assertThat(list(authorization)).hasStatusOk();

    MvcTestResult organization =
        mvc.get().uri("/v1/organization").header(AUTHORIZATION, authorization).exchange();
    assertThat(organization)
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.id")
        .isEqualTo(organizationId.toString());
    assertThat(organization).bodyJson().extractingPath("$.name").isEqualTo("보안 테스트 도입사");
    assertThat(organization).bodyJson().extractingPath("$.status").isEqualTo("active");
    assertThat(organization).bodyJson().extractingPath("$.created_at").asString().isNotBlank();
  }

  @Test
  void 키와_도입사_헤더를_같이_보내면_헤더는_무시하고_키의_도입사만_보인다() {
    UUID organizationId = insertOrganization();
    UUID otherOrganizationId = insertOrganization();
    insertCustomer(organizationId, "내 고객");
    insertCustomer(otherOrganizationId, "남의 고객");

    assertThat(
            mvc.get()
                .uri("/v1/customers")
                .header(AUTHORIZATION, issueApiKey(jdbcTemplate, organizationId))
                .header(LEGACY_ORGANIZATION_HEADER, otherOrganizationId.toString())
                .exchange())
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.customers[*].name")
        .asArray()
        .containsExactly("내 고객");
  }

  // --- 스코프와 도입사 상태 ---

  @Test
  void read_키로_고객을_등록하면_403_permission_denied이고_저장은_0건이다() {
    UUID organizationId = insertOrganization();

    MvcTestResult result = createCustomer(issueApiKey(jdbcTemplate, organizationId, "read"));

    assertThat(result)
        .hasStatus(403)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo(ErrorCode.PERMISSION_DENIED.getCode());
    assertThat(customerCount(organizationId)).isZero();
  }

  @Test
  void ingest와_read_키로_고객을_등록하면_201이다() {
    assertThat(
            createCustomer(
                issueApiKey(jdbcTemplate, insertOrganization(), "test", "ingest", "read")))
        .hasStatus(201);
  }

  @Test
  void ingest_키로_목록을_조회하면_403_permission_denied다() {
    assertThat(list(issueApiKey(jdbcTemplate, insertOrganization(), "ingest")))
        .hasStatus(403)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo(ErrorCode.PERMISSION_DENIED.getCode());
  }

  @Test
  void 정지된_도입사의_키는_403_organization_not_active다() {
    UUID organizationId = insertOrganization();
    String authorization = issueApiKey(jdbcTemplate, organizationId);
    jdbcTemplate.update(
        "UPDATE organization SET status = 'suspended' WHERE id = ?", organizationId);

    assertThat(list(authorization))
        .hasStatus(403)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo(ErrorCode.ORGANIZATION_NOT_ACTIVE.getCode());
  }

  // --- 요청 주체 ---

  @Test
  void API_키_주체는_종류와_모드와_도입사와_스코프를_싣는다() {
    UUID organizationId = insertOrganization();

    AuthenticatedPrincipal test =
        authenticate(issueApiKey(jdbcTemplate, organizationId, "test", "read"));
    AuthenticatedPrincipal live = authenticate(issueApiKey(jdbcTemplate, organizationId));

    assertThat(test.kind()).isEqualTo(PrincipalKind.API_KEY);
    assertThat(test.mode()).isEqualTo("test");
    assertThat(test.organizationId()).isEqualTo(organizationId);
    assertThat(test.apiKeyId()).isNotNull();
    assertThat(test.userAccountId()).isNull();
    assertThat(test.userSessionId()).isNull();
    assertThat(test.role()).isNull();
    assertThat(test.scopes()).containsExactly("read");
    assertThat(live.mode()).isEqualTo("live");
    assertThat(live.scopes()).containsExactly("admin");
  }

  @Test
  void admin_키는_세_스코프_권한을_모두_받는다() {
    assertThat(authenticate(issueApiKey(jdbcTemplate, insertOrganization())).authorities())
        .extracting(GrantedAuthority::getAuthority)
        .containsExactlyInAnyOrder("SCOPE_ingest", "SCOPE_read", "SCOPE_admin");
  }

  @Test
  void 키의_분당_한도가_자격_증명에_실린다() {
    String authorization = issueApiKey(jdbcTemplate, insertOrganization());
    jdbcTemplate.update(
        "UPDATE api_key SET rate_limit_per_minute = 30 WHERE key_hash = sha256(convert_to(?, 'UTF8'))",
        rawKeyOf(authorization));

    assertThat(
            apiKeyService
                .authenticate(rawKeyOf(authorization))
                .map(AuthenticatedCredential::rateLimitPerMinute))
        .contains(30);
  }

  // --- 레이트리밋 ---

  @Test
  void 분당_한도_5인_키의_6번째_요청은_429_rate_limit_exceeded와_Retry_After다() {
    String authorization = issueApiKey(jdbcTemplate, insertOrganization());
    setRateLimitPerMinute(authorization, 5);

    for (int count = 0; count < 5; count++) {
      assertThat(list(authorization)).hasStatusOk();
    }
    MvcTestResult rejected = list(authorization);

    assertThat(rejected).hasStatus(429).hasHeader(CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
    assertThat(Integer.parseInt(rejected.getResponse().getHeader(RETRY_AFTER))).isBetween(1, 12);
    assertThat(rejected)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo(ErrorCode.RATE_LIMIT_EXCEEDED.getCode());
    assertThat(bodyKeys(rejected)).containsExactly("code", "message");
  }

  @Test
  void 한도가_없는_키는_50번_연속_통과한다() {
    String authorization = issueApiKey(jdbcTemplate, insertOrganization());

    for (int count = 0; count < 50; count++) {
      assertThat(list(authorization)).hasStatusOk();
    }
  }

  // --- 공개 경로 ---

  @Test
  void 인증_없이_헬스와_API_문서를_연다() {
    assertThat(mvc.get().uri("/actuator/health").exchange()).hasStatusOk();
    assertThat(mvc.get().uri("/v3/api-docs").exchange()).hasStatusOk();
    assertThat(mvc.get().uri("/v3/api-docs.yaml").exchange()).hasStatusOk();
  }

  @Test
  void 유효한_키로도_노출하지_않은_actuator_엔드포인트는_404다() {
    assertThat(
            mvc.get()
                .uri("/actuator/env")
                .header(AUTHORIZATION, issueApiKey(jdbcTemplate, insertOrganization()))
                .exchange())
        .hasStatus(404);
  }

  // --- 오류 본문 ---

  @Test
  void 방화벽이_거절한_경로도_400_validation_error_JSON_본문이다() {
    MvcTestResult result =
        mvc.get()
            .uri("/v1/customers/a\u0000b")
            .header(AUTHORIZATION, issueApiKey(jdbcTemplate, insertOrganization()))
            .exchange();

    assertThat(result).hasStatus(400).hasHeader(CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
    assertThat(result)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo(ErrorCode.VALIDATION_ERROR.getCode());
    assertThat(bodyKeys(result)).containsExactly("code", "message");
  }

  @Test
  void 인증_오류_본문의_Content_Type은_charset_없는_application_json이다() {
    assertThat(mvc.get().uri("/v1/customers").exchange())
        .hasStatus(401)
        .hasHeader(CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
    assertThat(mvc.get().uri("/v1/customers").header(AUTHORIZATION, "Bearer abc").exchange())
        .hasStatus(401)
        .hasHeader(CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
  }

  @Test
  void 인증과_권한_오류_본문은_code와_message뿐이다() {
    UUID organizationId = insertOrganization();

    assertThat(bodyKeys(mvc.get().uri("/v1/customers").exchange()))
        .containsExactly("code", "message");
    assertThat(
            bodyKeys(mvc.get().uri("/v1/customers").header(AUTHORIZATION, "Bearer abc").exchange()))
        .containsExactly("code", "message");
    assertThat(bodyKeys(createCustomer(issueApiKey(jdbcTemplate, organizationId, "read"))))
        .containsExactly("code", "message");
  }

  // --- 헬퍼 ---

  private AuthenticatedPrincipal authenticate(String authorization) {
    return apiKeyService.authenticate(rawKeyOf(authorization)).orElseThrow().principal();
  }

  private MvcTestResult list(String authorization) {
    return mvc.get().uri("/v1/customers").header(AUTHORIZATION, authorization).exchange();
  }

  private MvcTestResult createCustomer(String authorization) {
    return mvc.post()
        .uri("/v1/customers")
        .header(AUTHORIZATION, authorization)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"name\":\"아크메\"}")
        .exchange();
  }

  private void setExpiresAt(String authorization, String expression) {
    jdbcTemplate.update(
        "UPDATE api_key SET expires_at = "
            + expression
            + " WHERE key_hash = sha256(convert_to(?, 'UTF8'))",
        rawKeyOf(authorization));
  }

  private void setRateLimitPerMinute(String authorization, int limitPerMinute) {
    jdbcTemplate.update(
        "UPDATE api_key SET rate_limit_per_minute = ? WHERE key_hash = sha256(convert_to(?, 'UTF8'))",
        limitPerMinute,
        rawKeyOf(authorization));
  }

  private UUID insertOrganization() {
    return jdbcTemplate.queryForObject(
        "INSERT INTO organization (name) VALUES ('보안 테스트 도입사') RETURNING id", UUID.class);
  }

  private void insertCustomer(UUID organizationId, String name) {
    jdbcTemplate.update(
        "INSERT INTO customer (organization_id, name) VALUES (?, ?)", organizationId, name);
  }

  private Integer customerCount(UUID organizationId) {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM customer WHERE organization_id = ?", Integer.class, organizationId);
  }

  private Set<String> bodyKeys(MvcTestResult result) {
    try {
      Set<String> keys = new TreeSet<>();
      jsonMapper
          .readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
          .propertyNames()
          .forEach(keys::add);
      return keys;
    } catch (UnsupportedEncodingException e) {
      throw new IllegalStateException("응답 본문을 읽지 못했다", e);
    }
  }
}
