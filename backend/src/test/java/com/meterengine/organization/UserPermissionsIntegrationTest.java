package com.meterengine.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.jayway.jsonpath.JsonPath;
import com.meterengine.TestCredentials;
import com.meterengine.TestcontainersConfiguration;
import com.meterengine.global.security.SecureTokens;
import jakarta.persistence.EntityManager;
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
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.json.JsonMapper;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class UserPermissionsIntegrationTest {
  private static final String PASSWORD = "permissions-test-password";
  private static final List<Map<String, String>> CONSENTS =
      List.of(
          Map.of("document", "terms_of_service", "document_version", "2026-10-26-preview"),
          Map.of("document", "privacy_policy", "document_version", "2026-10-26-preview"));
  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private JsonMapper jsonMapper;
  @Autowired private EntityManager entityManager;
  private MockMvcTester mvc;
  private UUID organizationId;
  private String owner;
  private UUID ownerId;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcTester.from(
            webApplicationContext, builder -> builder.apply(springSecurity()).build());
    organizationId = UUID.randomUUID();
    owner = TestCredentials.issueMemberSession(jdbcTemplate, organizationId);
    ownerId = userId(owner);
    jdbcTemplate.update(
        "UPDATE organization_member SET role='owner' WHERE organization_id=? AND user_account_id=?",
        organizationId,
        ownerId);
    entityManager.clear();
  }

  @Test
  void 새_계정은_조회로_시작하고_본인동의_뒤에도_관리권한을_얻지_않는다() {
    String email = UUID.randomUUID() + "@permissions.example";
    MvcTestResult created =
        post(
            "/v1/organization/members",
            owner,
            Map.of("email", email, "name", "조회 사용자", "password", PASSWORD));
    assertThat(created).hasStatus(201);
    assertThat(scopes(created)).containsExactly("read");
    assertThat(JsonPath.<String>read(body(created), "$.organization_role")).isEqualTo("member");
    String session = login(email);
    assertThat(get("/v1/sessions/current", session)).hasStatusOk();
    assertThat(post("/v1/sessions/current/consents", session, Map.of("consents", CONSENTS)))
        .hasStatusOk();
    assertThat(get("/v1/customers", session)).hasStatusOk();
    assertThat(post("/v1/customers", session, Map.of("name", "허용 안 됨"))).hasStatus(403);
    assertThat(post("/v1/api-keys", session, keyRequest())).hasStatus(403);
  }

  @Test
  void 조회_사용자는_목록을_읽지만_자기_권한과_키를_상향할_수_없다() {
    String reader = member(List.of("read"));
    assertThat(get("/v1/customers", reader)).hasStatusOk();
    assertThat(get("/v1/organization/members", reader)).hasStatusOk();
    assertThat(get("/v1/api-keys", reader)).hasStatusOk();
    assertThat(post("/v1/customers", reader, Map.of("name", "변경 금지"))).hasStatus(403);
    assertThat(post("/v1/events", reader, event(UUID.randomUUID()))).hasStatus(403);
    assertThat(
            put(
                "/v1/organization/members/" + userId(reader) + "/scopes",
                reader,
                Map.of("scopes", List.of("admin"))))
        .hasStatus(403);
    assertThat(post("/v1/api-keys", reader, keyRequest())).hasStatus(403);
    assertThat(get("/v1/sessions/current", reader))
        .bodyJson()
        .extractingPath("$.scopes")
        .asArray()
        .containsExactly("read");
  }

  @Test
  void 수집_사용자는_고객과_이벤트를_등록하지만_조회와_수정은_할_수_없다() {
    String collector = member(List.of("ingest"));
    MvcTestResult customer = post("/v1/customers", collector, Map.of("name", "수집 고객"));
    assertThat(customer).hasStatus(201);
    UUID customerId = UUID.fromString(JsonPath.read(body(customer), "$.id"));
    assertThat(post("/v1/events", collector, event(customerId))).hasStatusOk();
    assertThat(get("/v1/customers", collector)).hasStatus(403);
    assertThat(get("/v1/organization/members", collector)).hasStatus(403);
    assertThat(get("/v1/api-keys", collector)).hasStatus(403);
    assertThat(put("/v1/customers/" + customerId, collector, Map.of("name", "변경 금지")))
        .hasStatus(403);
    assertThat(get("/v1/sessions/current", collector)).hasStatusOk();
    assertThat(
            put(
                "/v1/sessions/current/organization",
                collector,
                Map.of("organization_id", organizationId)))
        .hasStatusOk();
    assertThat(mvc.delete().uri("/v1/sessions/current").header(AUTHORIZATION, collector).exchange())
        .hasStatus(204);
  }

  @Test
  void 조회와_수집의_조합은_관리_권한을_포함하지_않는다() {
    String combined = member(List.of("read", "ingest", "read"));
    assertThat(scopes(get("/v1/sessions/current", combined))).containsExactly("read", "ingest");
    MvcTestResult customer = post("/v1/customers", combined, Map.of("name", "등록 허용"));
    assertThat(customer).hasStatus(201);
    assertThat(get("/v1/customers", combined)).hasStatusOk();
    assertThat(
            put(
                "/v1/customers/" + JsonPath.read(body(customer), "$.id"),
                combined,
                Map.of("name", "수정 금지")))
        .hasStatus(403);
    assertThat(get("/v1/sessions/current", combined))
        .bodyJson()
        .extractingPath("$.organization_role")
        .isEqualTo("member");
  }

  @Test
  void 관리_스코프는_조회와_수집을_포함하지만_대표나_플랫폼운영자는_아니다() {
    String admin = member(List.of("admin"));
    MvcTestResult customer = post("/v1/customers", admin, Map.of("name", "운영 고객"));
    assertThat(customer).hasStatus(201);
    assertThat(get("/v1/customers", admin)).hasStatusOk();
    assertThat(
            put(
                "/v1/customers/" + JsonPath.read(body(customer), "$.id"),
                admin,
                Map.of("name", "운영 변경")))
        .hasStatusOk();
    assertThat(
            post(
                "/v1/organization/members",
                admin,
                Map.of("email", "denied@example.com", "name", "금지", "password", PASSWORD)))
        .hasStatus(403);
    assertThat(get("/v1/user-accounts", admin)).hasStatus(403);
  }

  @Test
  void 같은_세션으로_다음_요청부터_권한_축소와_복원이_반영된다() {
    String session = member(List.of("admin"));
    UUID id = userId(session);
    assertThat(post("/v1/api-keys", session, keyRequest())).hasStatus(201);
    assertThat(
            put(
                "/v1/organization/members/" + id + "/scopes",
                owner,
                Map.of("scopes", List.of("read"))))
        .hasStatusOk();
    assertThat(post("/v1/api-keys", session, keyRequest())).hasStatus(403);
    assertThat(scopes(get("/v1/sessions/current", session))).containsExactly("read");
    assertThat(
            put(
                "/v1/organization/members/" + id + "/scopes",
                owner,
                Map.of("scopes", List.of("admin"))))
        .hasStatusOk();
    assertThat(post("/v1/api-keys", session, keyRequest())).hasStatus(201);
    assertThat(get("/v1/sessions/current", session))
        .bodyJson()
        .extractingPath("$.organization_role")
        .isEqualTo("admin");
  }

  @Test
  void 한_사용자의_도입사별_권한은_전환할_때_서로_섞이지_않는다() {
    String session = member(List.of("read"));
    UUID id = userId(session);
    UUID second = UUID.randomUUID();
    TestCredentials.issueMemberSession(jdbcTemplate, second);
    jdbcTemplate.update(
        "INSERT INTO organization_member(organization_id,user_account_id,created_at,role,scopes) VALUES(?,?,now(),'admin',ARRAY['admin']::varchar[])",
        second,
        id);
    assertThat(put("/v1/sessions/current/organization", session, Map.of("organization_id", second)))
        .hasStatusOk();
    assertThat(scopes(get("/v1/sessions/current", session))).containsExactly("admin");
    assertThat(post("/v1/customers", session, Map.of("name", "두 번째 도입사"))).hasStatus(201);
    assertThat(
            put(
                "/v1/sessions/current/organization",
                session,
                Map.of("organization_id", organizationId)))
        .hasStatusOk();
    assertThat(scopes(get("/v1/sessions/current", session))).containsExactly("read");
    assertThat(post("/v1/customers", session, Map.of("name", "첫 번째 거절"))).hasStatus(403);
  }

  @Test
  void 대표의_관리권한은_유지하고_빈권한과_알수없는_권한은_거절한다() {
    assertThat(
            put(
                "/v1/organization/members/" + ownerId + "/scopes",
                owner,
                Map.of("scopes", List.of("read"))))
        .hasStatus(409)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("owner_scopes_required");
    String reader = member(List.of("read"));
    for (List<String> invalid : List.of(List.<String>of(), List.of("operator"), List.of("write")))
      assertThat(
              put(
                  "/v1/organization/members/" + userId(reader) + "/scopes",
                  owner,
                  Map.of("scopes", invalid)))
          .hasStatus(400);
    assertThat(scopes(get("/v1/sessions/current", owner))).containsExactly("admin");
  }

  @Test
  void 다른_도입사의_사용자_권한과_대표관리자_역할은_바꿀_수_없다() {
    UUID otherOrganization = UUID.randomUUID();
    String other = TestCredentials.issueMemberSession(jdbcTemplate, otherOrganization);
    assertThat(
            put(
                "/v1/organization/members/" + userId(other) + "/scopes",
                owner,
                Map.of("scopes", List.of("read"))))
        .hasStatus(404);
    String admin = member(List.of("admin"));
    assertThat(
            put(
                "/v1/organization/members/" + ownerId + "/role",
                admin,
                Map.of("organization_role", "admin")))
        .hasStatus(403);
    String key = TestCredentials.issueApiKey(jdbcTemplate, organizationId, "test", "admin");
    assertThat(
            put(
                "/v1/organization/members/" + ownerId + "/scopes",
                key,
                Map.of("scopes", List.of("read"))))
        .hasStatus(403);
  }

  @Test
  void 초대의_권한은_발급때_고정되고_수락_본문으로_상향할_수_없다() {
    String email = UUID.randomUUID() + "@permissions.example";
    MvcTestResult issued =
        post(
            "/v1/organization/invitations",
            owner,
            Map.of("email", email, "scopes", List.of("read", "read")));
    assertThat(issued).hasStatus(201);
    String token = JsonPath.read(body(issued), "$.token");
    assertThat(get("/v1/organization-invitations/" + token, null))
        .bodyJson()
        .extractingPath("$.scopes")
        .asArray()
        .containsExactly("read");
    assertThat(
            post(
                "/v1/organization-invitations/" + token + "/accept",
                null,
                Map.of(
                    "name",
                    "초대된 조회 사용자",
                    "password",
                    PASSWORD,
                    "consents",
                    CONSENTS,
                    "scopes",
                    List.of("admin"))))
        .hasStatus(201);
    String session = login(email);
    assertThat(scopes(get("/v1/sessions/current", session))).containsExactly("read");
    assertThat(post("/v1/api-keys", session, keyRequest())).hasStatus(403);
  }

  @Test
  void 기존_초대의_관리권한은_수락할_때_보존한다() {
    String email = UUID.randomUUID() + "@permissions.example";
    String token = SecureTokens.generate("me_invite_");
    jdbcTemplate.update(
        "INSERT INTO organization_invitation(id,organization_id,email,token_hash,created_at,expires_at) VALUES(?,?,?,sha256(convert_to(?,'UTF8')),now(),now()+interval '48 hours')",
        UUID.randomUUID(),
        organizationId,
        email,
        token);
    assertThat(get("/v1/organization-invitations/" + token, null))
        .bodyJson()
        .extractingPath("$.scopes")
        .asArray()
        .containsExactly("admin");
    assertThat(
            post(
                "/v1/organization-invitations/" + token + "/accept",
                null,
                Map.of("name", "기존 초대 사용자", "password", PASSWORD, "consents", CONSENTS)))
        .hasStatus(201);
    assertThat(scopes(get("/v1/sessions/current", login(email)))).containsExactly("admin");
  }

  @Test
  void 보관된_관리자도_새_조회_초대로_복원하면_관리권한이_돌아오지_않는다() {
    String email = UUID.randomUUID() + "@permissions.example";
    MvcTestResult created =
        post(
            "/v1/organization/members",
            owner,
            Map.of(
                "email",
                email,
                "name",
                "복원 사용자",
                "password",
                PASSWORD,
                "scopes",
                List.of("admin")));
    String session = login(email);
    assertThat(post("/v1/sessions/current/consents", session, Map.of("consents", CONSENTS)))
        .hasStatusOk();
    UUID id = UUID.fromString(JsonPath.read(body(created), "$.user_account_id"));
    assertThat(
            mvc.delete()
                .uri("/v1/organization/members/" + id)
                .header(AUTHORIZATION, owner)
                .exchange())
        .hasStatus(204);
    MvcTestResult issued = post("/v1/organization/invitations", owner, Map.of("email", email));
    String token = JsonPath.read(body(issued), "$.token");
    String pendingSession = login(email);
    assertThat(post("/v1/organization-invitations/" + token + "/accept", pendingSession, Map.of()))
        .hasStatus(201);
    assertThat(
            put(
                "/v1/sessions/current/organization",
                pendingSession,
                Map.of("organization_id", organizationId)))
        .hasStatusOk();
    assertThat(scopes(get("/v1/sessions/current", pendingSession))).containsExactly("read");
    assertThat(post("/v1/api-keys", pendingSession, keyRequest())).hasStatus(403);
  }

  @Test
  void 플랫폼_운영자_역할과_도입사_업무_권한은_서로_대신하지_않는다() {
    String operator = TestCredentials.issueOperatorSession(jdbcTemplate);
    assertThat(scopes(get("/v1/sessions/current", operator))).isEmpty();
    assertThat(get("/v1/user-accounts", operator)).hasStatusOk();
    UUID id = userId(operator);
    jdbcTemplate.update(
        "INSERT INTO organization_member(organization_id,user_account_id,created_at,role,scopes) VALUES(?,?,now(),'member',ARRAY['read']::varchar[])",
        organizationId,
        id);
    assertThat(
            put(
                "/v1/sessions/current/organization",
                operator,
                Map.of("organization_id", organizationId)))
        .hasStatusOk();
    assertThat(get("/v1/customers", operator)).hasStatusOk();
    assertThat(post("/v1/customers", operator, Map.of("name", "상향 금지"))).hasStatus(403);
    assertThat(get("/v1/user-accounts", operator)).hasStatusOk();
  }

  @Test
  void 권한_변경_감사는_전후_권한과_대표관리자_주체를_남긴다() {
    String session = member(List.of("read"));
    UUID id = userId(session);
    assertThat(
            put(
                "/v1/organization/members/" + id + "/scopes",
                owner,
                Map.of("scopes", List.of("ingest", "read"))))
        .hasStatusOk();
    entityManager.flush();
    Map<String, Object> row =
        jdbcTemplate.queryForMap(
            "SELECT actor_type,actor_id,detail::text FROM audit_log WHERE organization_id=? AND action='organization_member_scopes_updated' AND target_id=? AND detail->'to'=?::jsonb",
            organizationId,
            id.toString(),
            "[\"ingest\",\"read\"]");
    assertThat(row.get("actor_type")).isEqualTo("user_account");
    assertThat(row.get("actor_id")).isEqualTo(ownerId);
    assertThat(JsonPath.<List<String>>read((String) row.get("detail"), "$.from"))
        .containsExactly("read");
    assertThat(JsonPath.<List<String>>read((String) row.get("detail"), "$.to"))
        .containsExactly("ingest", "read");
    assertThat((String) row.get("detail")).doesNotContain(TestCredentials.rawKeyOf(owner));
  }

  @Test
  void 플랫폼_소속추가는_조회가_기본이고_보관된_대표도_명시한_권한으로_복원된다() {
    UUID otherOrganization = UUID.randomUUID();
    String target = TestCredentials.issueMemberSession(jdbcTemplate, otherOrganization);
    UUID id = userId(target);
    String operator = TestCredentials.issueOperatorSession(jdbcTemplate);
    String path = "/v1/organizations/" + organizationId + "/members";
    MvcTestResult attached = post(path, operator, Map.of("user_account_id", id));
    assertThat(attached).hasStatus(201);
    assertThat(scopes(attached)).containsExactly("read");
    assertThat(
            put(
                "/v1/organization/members/" + id + "/role",
                owner,
                Map.of("organization_role", "owner")))
        .hasStatusOk();
    assertThat(
            mvc.delete()
                .uri("/v1/organization/members/" + id)
                .header(AUTHORIZATION, owner)
                .exchange())
        .hasStatus(204);
    MvcTestResult restored = post(path, operator, Map.of("user_account_id", id));
    assertThat(restored).hasStatus(201);
    assertThat(scopes(restored)).containsExactly("read");
    assertThat(JsonPath.<String>read(body(restored), "$.organization_role")).isEqualTo("member");
    assertThat(scopes(get("/v1/sessions/current", target))).containsExactly("admin");
  }

  @Test
  void 첫_소속은_대표관리자로_만들어_관리권한을_잃지_않는다() {
    UUID emptyOrganization = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO organization(id,name) VALUES(?, '처음 시작하는 도입사')", emptyOrganization);
    String target = TestCredentials.issueMemberSession(jdbcTemplate, organizationId);
    String operator = TestCredentials.issueOperatorSession(jdbcTemplate);
    MvcTestResult attached =
        post(
            "/v1/organizations/" + emptyOrganization + "/members",
            operator,
            Map.of("user_account_id", userId(target), "scopes", List.of("read")));
    assertThat(attached).hasStatus(201);
    assertThat(scopes(attached)).containsExactly("admin");
    assertThat(JsonPath.<String>read(body(attached), "$.organization_role")).isEqualTo("owner");
  }

  @Test
  void 새_계정과_초대의_잘못된_권한은_저장하기_전에_거절한다() {
    for (List<String> invalid : List.of(List.<String>of(), List.of("operator"))) {
      String email = UUID.randomUUID() + "@permissions.example";
      assertThat(
              post(
                  "/v1/organization/members",
                  owner,
                  Map.of(
                      "email", email, "name", "잘못된 권한", "password", PASSWORD, "scopes", invalid)))
          .hasStatus(400);
      assertThat(
              post(
                  "/v1/organization/invitations", owner, Map.of("email", email, "scopes", invalid)))
          .hasStatus(400);
      assertThat(
              jdbcTemplate.queryForObject(
                  "SELECT count(*) FROM user_account WHERE email=?", Integer.class, email))
          .isZero();
      assertThat(
              jdbcTemplate.queryForObject(
                  "SELECT count(*) FROM organization_invitation WHERE email=?",
                  Integer.class,
                  email))
          .isZero();
    }
  }

  private String member(List<String> scopes) {
    String session = TestCredentials.issueMemberSession(jdbcTemplate, organizationId);
    assertThat(
            put(
                "/v1/organization/members/" + userId(session) + "/scopes",
                owner,
                Map.of("scopes", scopes)))
        .hasStatusOk();
    return session;
  }

  private String login(String email) {
    MvcTestResult result = post("/v1/sessions", null, Map.of("email", email, "password", PASSWORD));
    assertThat(result).hasStatus(201);
    return "Bearer " + JsonPath.read(body(result), "$.token");
  }

  private UUID userId(String session) {
    return UUID.fromString(
        JsonPath.read(body(get("/v1/sessions/current", session)), "$.user_account.id"));
  }

  private MvcTestResult get(String path, String auth) {
    return auth == null
        ? mvc.get().uri(path).exchange()
        : mvc.get().uri(path).header(AUTHORIZATION, auth).exchange();
  }

  private MvcTestResult post(String path, String auth, Object data) {
    var request =
        mvc.post()
            .uri(path)
            .contentType(MediaType.APPLICATION_JSON)
            .content(jsonMapper.writeValueAsString(data));
    if (auth != null) request.header(AUTHORIZATION, auth);
    return request.exchange();
  }

  private MvcTestResult put(String path, String auth, Object data) {
    return mvc.put()
        .uri(path)
        .header(AUTHORIZATION, auth)
        .contentType(MediaType.APPLICATION_JSON)
        .content(jsonMapper.writeValueAsString(data))
        .exchange();
  }

  private static Map<String, Object> event(UUID customerId) {
    return Map.of(
        "transaction_id",
        UUID.randomUUID().toString(),
        "customer_id",
        customerId,
        "type",
        "permissions_usage",
        "timestamp",
        OffsetDateTime.now().toString(),
        "properties",
        Map.of("quantity", 1));
  }

  private static Map<String, Object> keyRequest() {
    return Map.of("label", "권한 검증", "mode", "test", "scopes", List.of("admin"));
  }

  private static List<String> scopes(MvcTestResult result) {
    return JsonPath.read(body(result), "$.scopes");
  }

  private static String body(MvcTestResult result) {
    return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
  }
}
