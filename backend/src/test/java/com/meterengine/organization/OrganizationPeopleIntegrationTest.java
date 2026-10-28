package com.meterengine.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.jayway.jsonpath.JsonPath;
import com.meterengine.TestCredentials;
import com.meterengine.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.json.JsonMapper;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class OrganizationPeopleIntegrationTest {
  private static final String PASSWORD = "new-member-password";
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
  }

  @Test
  void 직접_생성은_동의를_대리기록하지_않고_본인동의_전까지_업무를_막는다() {
    String email = UUID.randomUUID() + "@example.com";
    MvcTestResult created =
        post(
            "/v1/organization/members",
            owner,
            Map.of("email", email, "name", "새 관리자", "password", PASSWORD));
    assertThat(created).hasStatus(201);
    UUID userId = UUID.fromString(JsonPath.read(body(created), "$.user_account_id"));
    entityManager.flush();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT consent_required FROM user_account WHERE id=?", Boolean.class, userId))
        .isTrue();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM consent WHERE user_account_id=?", Integer.class, userId))
        .isZero();
    MvcTestResult login = post("/v1/sessions", null, Map.of("email", email, "password", PASSWORD));
    assertThat(login).hasStatus(201);
    String authorization = "Bearer " + JsonPath.read(body(login), "$.token");
    assertThat(
            mvc.get().uri("/v1/sessions/current").header(AUTHORIZATION, authorization).exchange())
        .hasStatusOk();
    assertThat(post("/v1/api-keys", authorization, Map.of()))
        .hasStatus(403)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("consent_required");
    assertThat(post("/v1/sessions/current/consents", authorization, Map.of("consents", CONSENTS)))
        .hasStatusOk();
    assertThat(
            mvc.get()
                .uri("/v1/organization/members")
                .header(AUTHORIZATION, authorization)
                .exchange())
        .hasStatusOk();
    assertThat(
            post(
                "/v1/organization/members",
                authorization,
                Map.of("email", "denied@example.com", "name", "권한없음", "password", PASSWORD)))
        .hasStatus(403);
    assertThat(mvc.get().uri("/v1/user-accounts").header(AUTHORIZATION, authorization).exchange())
        .hasStatus(403);
    entityManager.flush();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT role FROM user_account WHERE id=?", String.class, userId))
        .isEqualTo("member");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM consent WHERE user_account_id=?", Integer.class, userId))
        .isEqualTo(2);
  }

  @Test
  void 기존_이메일의_직접생성은_409이며_비밀번호를_바꾸지_않는다() {
    String email =
        jdbcTemplate.queryForObject(
            "SELECT email FROM user_account WHERE id=?", String.class, ownerId);
    String hash =
        jdbcTemplate.queryForObject(
            "SELECT password_hash FROM user_account WHERE id=?", String.class, ownerId);
    assertThat(
            post(
                "/v1/organization/members",
                owner,
                Map.of(
                    "email",
                    email.toUpperCase(java.util.Locale.ROOT),
                    "name",
                    "덮어쓰기",
                    "password",
                    PASSWORD)))
        .hasStatus(409);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT password_hash FROM user_account WHERE id=?", String.class, ownerId))
        .isEqualTo(hash);
  }

  @Test
  void 신규_초대는_hash만_저장하고_본인동의로_한번만_수락한다() {
    String token = invite(UUID.randomUUID() + "@example.com");
    assertThat(mvc.get().uri("/v1/organization-invitations/" + token).exchange())
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.account_exists")
        .isEqualTo(false);
    MvcTestResult accepted =
        post(
            "/v1/organization-invitations/" + token + "/accept",
            null,
            Map.of("name", "초대 사용자", "password", PASSWORD, "consents", CONSENTS));
    assertThat(accepted).hasStatus(201);
    UUID userId = UUID.fromString(JsonPath.read(body(accepted), "$.user_account_id"));
    entityManager.flush();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM consent WHERE user_account_id=?", Integer.class, userId))
        .isEqualTo(2);
    assertThat(post("/v1/organization-invitations/" + token + "/accept", null, Map.of()))
        .hasStatus(409);
    MvcTestResult listed =
        mvc.get().uri("/v1/organization/invitations").header(AUTHORIZATION, owner).exchange();
    assertThat(body(listed)).doesNotContain(token, "token_hash", PASSWORD);
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT detail::text FROM audit_log WHERE organization_id=?",
                String.class,
                organizationId))
        .allSatisfy(detail -> assertThat(detail).doesNotContain(token, PASSWORD, "password"));
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT octet_length(token_hash) FROM organization_invitation WHERE"
                    + " organization_id=?",
                Integer.class,
                organizationId))
        .isEqualTo(32);
  }

  @Test
  void 신규_초대를_빈_동의로_수락하면_400이고_계정과_수락을_남기지_않는다() {
    String email = UUID.randomUUID() + "@example.com";
    String token = invite(email);
    assertThat(
            post(
                "/v1/organization-invitations/" + token + "/accept",
                null,
                Map.of("name", "초대 사용자", "password", PASSWORD, "consents", List.of())))
        .hasStatus(400)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("validation_error");
    entityManager.flush();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM user_account WHERE lower(email)=?", Integer.class, email))
        .isZero();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT accepted_at IS NULL FROM organization_invitation WHERE"
                    + " token_hash=sha256(convert_to(?,'UTF8'))",
                Boolean.class,
                token))
        .isTrue();
  }

  @Test
  void 기존계정_초대는_같은_이메일의_로그인본인만_수락한다() {
    UUID otherOrg = UUID.randomUUID();
    String invited = TestCredentials.issueMemberSession(jdbcTemplate, otherOrg);
    UUID invitedId = userId(invited);
    String email =
        jdbcTemplate.queryForObject(
            "SELECT email FROM user_account WHERE id=?", String.class, invitedId);
    String token = invite(email);
    String path = "/v1/organization-invitations/" + token + "/accept";
    assertThat(post(path, null, Map.of())).hasStatus(403);
    assertThat(post(path, owner, Map.of())).hasStatus(403);
    assertThat(post(path, invited, Map.of())).hasStatus(201);
    entityManager.flush();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT role FROM organization_member WHERE organization_id=? AND"
                    + " user_account_id=?",
                String.class,
                organizationId,
                invitedId))
        .isEqualTo("member");
  }

  @Test
  void 취소_만료_다른도입사_초대는_거부한다() {
    String token = invite(UUID.randomUUID() + "@example.com");
    entityManager.flush();
    UUID invitationId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM organization_invitation WHERE organization_id=?",
            UUID.class,
            organizationId);
    String outsider = TestCredentials.issueMemberSession(jdbcTemplate, UUID.randomUUID());
    UUID outsiderId = userId(outsider);
    jdbcTemplate.update(
        "UPDATE organization_member SET role='owner' WHERE user_account_id=?", outsiderId);
    assertThat(
            mvc.delete()
                .uri("/v1/organization/invitations/" + invitationId)
                .header(AUTHORIZATION, outsider)
                .exchange())
        .hasStatus(404);
    assertThat(
            mvc.delete()
                .uri("/v1/organization/invitations/" + invitationId)
                .header(AUTHORIZATION, owner)
                .exchange())
        .hasStatus(204);
    assertThat(mvc.get().uri("/v1/organization-invitations/" + token).exchange()).hasStatus(409);
    entityManager.clear();
    String expired = invite(UUID.randomUUID() + "@example.com");
    entityManager.flush();
    jdbcTemplate.update(
        "UPDATE organization_invitation SET created_at=now()-interval '3"
            + " days',expires_at=now()-interval '1 day' WHERE"
            + " token_hash=sha256(convert_to(?,'UTF8'))",
        expired);
    entityManager.clear();
    assertThat(mvc.get().uri("/v1/organization-invitations/" + expired).exchange()).hasStatus(409);
  }

  @Test
  void 마지막_대표는_하향과_소속보관을_막고_관리자소속_보관은_세션을_즉시_폐기한다() {
    assertThat(
            mvc.delete()
                .uri("/v1/organization/members/" + ownerId)
                .header(AUTHORIZATION, owner)
                .exchange())
        .hasStatus(409);
    assertThat(
            mvc.put()
                .uri("/v1/organization/members/" + ownerId + "/role")
                .header(AUTHORIZATION, owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"organization_role\":\"admin\"}")
                .exchange())
        .hasStatus(409);
    String admin = TestCredentials.issueMemberSession(jdbcTemplate, organizationId);
    UUID adminId = userId(admin);
    MvcTestResult archived =
        mvc.delete()
            .uri("/v1/organization/members/" + adminId)
            .header(AUTHORIZATION, owner)
            .exchange();
    assertThat(archived).hasStatus(204);
    entityManager.clear();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT revoked_at IS NOT NULL FROM organization_member WHERE organization_id=? AND"
                    + " user_account_id=?",
                Boolean.class,
                organizationId,
                adminId))
        .isTrue();
    assertThat(mvc.get().uri("/v1/sessions/current").header(AUTHORIZATION, admin).exchange())
        .hasStatus(401);
    assertThat(mvc.get().uri("/v1/organization/members").header(AUTHORIZATION, owner).exchange())
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.members[?(@.user_account_id == '" + adminId + "')].status")
        .isEqualTo(List.of("archived"));
  }

  @Test
  void API키에는_사람관리_권한을_부여하지_않는다() {
    for (String scope : List.of("read", "admin")) {
      String key = TestCredentials.issueApiKey(jdbcTemplate, organizationId, scope);
      assertThat(mvc.get().uri("/v1/organization/members").header(AUTHORIZATION, key).exchange())
          .hasStatus(403);
      assertThat(post("/v1/organization/invitations", key, Map.of("email", "denied@example.com")))
          .hasStatus(403);
    }
  }

  @Test
  void 소속_상태별_목록은_보관한_관리자를_찾고_다른_도입사는_보이지_않는다() {
    String admin = TestCredentials.issueMemberSession(jdbcTemplate, organizationId);
    UUID adminId = userId(admin);
    TestCredentials.issueMemberSession(jdbcTemplate, UUID.randomUUID());
    assertThat(
            mvc.delete()
                .uri("/v1/organization/members/" + adminId)
                .header(AUTHORIZATION, owner)
                .exchange())
        .hasStatus(204);
    entityManager.clear();
    Map<String, List<String>> expected =
        Map.of(
            "active", List.of(ownerId.toString()),
            "archived", List.of(adminId.toString()),
            "all", List.of(ownerId.toString(), adminId.toString()));
    expected.forEach(
        (status, ids) -> {
          MvcTestResult result =
              mvc.get()
                  .uri("/v1/organization/members?status=" + status)
                  .header(AUTHORIZATION, owner)
                  .exchange();
          assertThat(result).hasStatusOk();
          assertThat(JsonPath.<List<String>>read(body(result), "$.members[*].user_account_id"))
              .containsExactlyInAnyOrderElementsOf(ids);
        });
    assertThat(mvc.get().uri("/v1/organization/members").header(AUTHORIZATION, owner).exchange())
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.members.length()")
        .isEqualTo(2);
  }

  @Test
  void 사용자_목록의_잘못된_보관상태는_400이다() {
    assertThat(
            mvc.get()
                .uri("/v1/organization/members?status=revoked")
                .header(AUTHORIZATION, owner)
                .exchange())
        .hasStatus(400)
        .bodyJson()
        .extractingPath("$.errors[0].field")
        .isEqualTo("status");
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void 같은_초대의_동시수락은_한번만_소속과_계정을_만든다() throws Exception {
    String email = UUID.randomUUID() + "@example.com";
    String token = invite(email);
    String path = "/v1/organization-invitations/" + token + "/accept";
    CountDownLatch start = new CountDownLatch(1);
    var first = CompletableFuture.supplyAsync(() -> acceptAfter(start, path));
    var second = CompletableFuture.supplyAsync(() -> acceptAfter(start, path));
    start.countDown();
    assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
        .containsExactlyInAnyOrder(201, 409);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM user_account WHERE email=?", Integer.class, email))
        .isEqualTo(1);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM organization_member WHERE organization_id=?",
                Integer.class,
                organizationId))
        .isEqualTo(2);
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void 두_대표가_동시에_자신을_하향해도_마지막_대표는_남는다() throws Exception {
    String other = TestCredentials.issueMemberSession(jdbcTemplate, organizationId);
    UUID otherId = userId(other);
    jdbcTemplate.update(
        "UPDATE organization_member SET role='owner' WHERE organization_id=?", organizationId);
    CountDownLatch start = new CountDownLatch(1);
    var first = CompletableFuture.supplyAsync(() -> demoteAfter(start, owner, ownerId));
    var second = CompletableFuture.supplyAsync(() -> demoteAfter(start, other, otherId));
    start.countDown();
    assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
        .containsExactlyInAnyOrder(200, 409);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM organization_member WHERE organization_id=? AND role='owner'"
                    + " AND revoked_at IS NULL",
                Integer.class,
                organizationId))
        .isEqualTo(1);
  }

  private int acceptAfter(CountDownLatch start, String path) {
    await(start);
    return post(path, null, Map.of("name", "동시 초대", "password", PASSWORD, "consents", CONSENTS))
        .getResponse()
        .getStatus();
  }

  private int demoteAfter(CountDownLatch start, String authorization, UUID id) {
    await(start);
    return mvc.put()
        .uri("/v1/organization/members/" + id + "/role")
        .header(AUTHORIZATION, authorization)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"organization_role\":\"admin\"}")
        .exchange()
        .getResponse()
        .getStatus();
  }

  private static void await(CountDownLatch start) {
    try {
      if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("start timeout");
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(exception);
    }
  }

  private String invite(String email) {
    MvcTestResult result = post("/v1/organization/invitations", owner, Map.of("email", email));
    assertThat(result).hasStatus(201);
    return JsonPath.read(body(result), "$.token");
  }

  private UUID userId(String authorization) {
    return jdbcTemplate.queryForObject(
        "SELECT user_account_id FROM user_session WHERE token_hash=sha256(convert_to(?,'UTF8'))",
        UUID.class,
        TestCredentials.rawKeyOf(authorization));
  }

  private MvcTestResult post(String path, String authorization, Map<String, ?> content) {
    var request =
        mvc.post()
            .uri(path)
            .contentType(MediaType.APPLICATION_JSON)
            .content(jsonMapper.writeValueAsString(content));
    if (authorization != null) request.header(AUTHORIZATION, authorization);
    return request.exchange();
  }

  private static String body(MvcTestResult result) {
    return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
  }
}
