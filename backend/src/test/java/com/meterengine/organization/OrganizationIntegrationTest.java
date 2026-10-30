package com.meterengine.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.jayway.jsonpath.JsonPath;
import com.meterengine.TestCredentials;
import com.meterengine.TestcontainersConfiguration;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.security.BusinessEnvironmentInterceptor;
import jakarta.persistence.EntityManager;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class OrganizationIntegrationTest {

  private static final String ORGANIZATIONS = "/v1/organizations";

  private static final List<String> STATUSES =
      List.of("pending", "active", "rejected", "suspended");

  private static final Set<List<String>> ALLOWED_TRANSITIONS =
      Set.of(
          List.of("pending", "active"),
          List.of("pending", "rejected"),
          List.of("active", "suspended"),
          List.of("suspended", "active"));

  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private JdbcTemplate jdbcTemplate;
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

  // --- 목록 ---

  @Test
  void 운영자는_도입사_목록을_만든_순서로_조회한다() {
    UUID third = insertOrganization("셋째 도입사", "active", "2100-01-03T00:00:00Z");
    UUID first = insertOrganization("첫째 도입사", "pending", "2100-01-01T00:00:00Z");
    UUID second = insertOrganization("둘째 도입사", "suspended", "2100-01-02T00:00:00Z");

    MvcTestResult result = mvc.get().uri(ORGANIZATIONS).header(AUTHORIZATION, operator).exchange();

    assertThat(result).hasStatusOk();
    List<Map<String, Object>> organizations = JsonPath.read(body(result), "$.organizations");
    assertThat(organizations.subList(organizations.size() - 3, organizations.size()))
        .extracting(organization -> organization.get("id"))
        .containsExactly(first.toString(), second.toString(), third.toString());
    Map<String, Object> last = organizations.getLast();
    assertThat(last).containsOnlyKeys("id", "name", "status", "created_at");
    assertThat(last).containsEntry("name", "셋째 도입사").containsEntry("status", "active");
    assertThat(OffsetDateTime.parse((String) last.get("created_at")))
        .isAtSameInstantAs(OffsetDateTime.parse("2100-01-03T00:00:00Z"));
  }

  @Test
  void status를_주면_그_상태의_도입사만_돌려준다() {
    UUID pending = insertOrganization("대기 도입사", "pending", "2100-01-01T00:00:00Z");
    UUID active = insertOrganization("활성 도입사", "active", "2100-01-02T00:00:00Z");

    MvcTestResult result =
        mvc.get()
            .uri(ORGANIZATIONS)
            .param("status", "pending")
            .header(AUTHORIZATION, operator)
            .exchange();

    assertThat(result).hasStatusOk();
    List<Map<String, Object>> organizations = JsonPath.read(body(result), "$.organizations");
    assertThat(organizations)
        .extracting(organization -> organization.get("status"))
        .containsOnly("pending");
    assertThat(organizations)
        .extracting(organization -> organization.get("id"))
        .contains(pending.toString())
        .doesNotContain(active.toString());
  }

  @Test
  void 모르는_status로_조회하면_400_validation_error다() {
    assertThat(
            mvc.get()
                .uri(ORGANIZATIONS)
                .param("status", "deleted")
                .header(AUTHORIZATION, operator)
                .exchange())
        .hasStatus(400)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo(ErrorCode.VALIDATION_ERROR.getCode());
  }

  // --- 상태 전이 ---

  @Test
  void 허용된_네_전이는_200과_바뀐_도입사를_돌려주고_감사_행을_남긴다() {
    UUID operatorId = userAccountIdOf(operator);

    for (List<String> transition : ALLOWED_TRANSITIONS) {
      String from = transition.get(0);
      String to = transition.get(1);
      UUID organizationId = insertOrganization("전이 도입사", from, "2100-01-01T00:00:00Z");

      MvcTestResult result = updateStatus(operator, organizationId, to);

      assertThat(result).as(from + " -> " + to).hasStatusOk();
      String body = body(result);
      assertThat((String) JsonPath.read(body, "$.id")).isEqualTo(organizationId.toString());
      assertThat((String) JsonPath.read(body, "$.status")).isEqualTo(to);
      assertThat((String) JsonPath.read(body, "$.name")).isEqualTo("전이 도입사");
      assertThat((String) JsonPath.read(body, "$.created_at")).isNotBlank();
      entityManager.flush();
      assertThat(
              jdbcTemplate.queryForObject(
                  "SELECT status FROM organization WHERE id = ?", String.class, organizationId))
          .isEqualTo(to);
      assertThat(
              jdbcTemplate.queryForMap(
                  """
                  SELECT actor_type, actor_id, target_type, target_id,
                         detail->>'from' AS from_status, detail->>'to' AS to_status
                  FROM audit_log
                  WHERE action = 'organization_status_updated' AND organization_id = ?
                  """,
                  organizationId))
          .containsEntry("actor_type", "user_account")
          .containsEntry("actor_id", operatorId)
          .containsEntry("target_type", "organization")
          .containsEntry("target_id", organizationId.toString())
          .containsEntry("from_status", from)
          .containsEntry("to_status", to);
    }
  }

  @Test
  void 허용되지_않은_전이는_같은_상태를_포함해_409_organization_status_transition_invalid이고_상태가_그대로다() {
    for (String from : STATUSES) {
      for (String to : STATUSES) {
        if (ALLOWED_TRANSITIONS.contains(List.of(from, to))) {
          continue;
        }
        UUID organizationId = insertOrganization("거부 도입사", from, "2100-01-01T00:00:00Z");

        assertThat(updateStatus(operator, organizationId, to))
            .as(from + " -> " + to)
            .hasStatus(409)
            .bodyJson()
            .extractingPath("$.code")
            .isEqualTo(ErrorCode.ORGANIZATION_STATUS_TRANSITION_INVALID.getCode());
        entityManager.flush();
        assertThat(
                jdbcTemplate.queryForObject(
                    "SELECT status FROM organization WHERE id = ?", String.class, organizationId))
            .as(from + " -> " + to)
            .isEqualTo(from);
        assertThat(
                jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM audit_log WHERE organization_id = ?",
                    Integer.class,
                    organizationId))
            .isZero();
      }
    }
  }

  @Test
  void 없는_도입사의_상태를_바꾸면_404_organization_not_found다() {
    assertThat(updateStatus(operator, UUID.randomUUID(), "active"))
        .hasStatus(404)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo(ErrorCode.ORGANIZATION_NOT_FOUND.getCode());
  }

  @Test
  void 상태_본문이_비거나_모르는_값이면_400_validation_error다() {
    UUID organizationId = insertOrganization("본문 도입사", "pending", "2100-01-01T00:00:00Z");

    for (String content : List.of("{}", "{\"status\":\"deleted\"}", "{\"status\":\"\"}")) {
      assertThat(
              mvc.put()
                  .uri(ORGANIZATIONS + "/{id}/status", organizationId)
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
  void 정지하면_그_도입사의_키와_세션이_403_organization_not_active이고_재개하면_200이다() {
    UUID organizationId = UUID.randomUUID();
    String apiKey = TestCredentials.issueApiKey(jdbcTemplate, organizationId);
    String member = TestCredentials.issueMemberSession(jdbcTemplate, organizationId);

    assertThat(updateStatus(operator, organizationId, "suspended")).hasStatusOk();

    for (String authorization : List.of(apiKey, member)) {
      assertThat(mvc.get().uri("/v1/customers").header(AUTHORIZATION, authorization).exchange())
          .hasStatus(403)
          .bodyJson()
          .extractingPath("$.code")
          .isEqualTo(ErrorCode.ORGANIZATION_NOT_ACTIVE.getCode());
    }

    assertThat(updateStatus(operator, organizationId, "active")).hasStatusOk();

    for (String authorization : List.of(apiKey, member)) {
      assertThat(mvc.get().uri("/v1/customers").header(AUTHORIZATION, authorization).exchange())
          .hasStatusOk();
    }
  }

  // --- 소속 추가 ---

  @Test
  void 소속을_더하면_201이고_member가_그_도입사로_전환해_고객_목록이_바뀐다() {
    UUID first = UUID.randomUUID();
    String member = TestCredentials.issueMemberSession(jdbcTemplate, first);
    UUID userAccountId = userAccountIdOf(member);
    UUID second = insertOrganization("둘째 도입사", "active", "2100-01-01T00:00:00Z");
    insertCustomer(first, "첫 도입사 고객");
    insertCustomer(second, "둘째 도입사 고객");

    MvcTestResult result = createMember(operator, second, userAccountId);

    assertThat(result).hasStatus(201);
    String body = body(result);
    assertThat((Map<String, Object>) JsonPath.read(body, "$"))
        .containsOnlyKeys(
            "organization_id", "user_account_id", "created_at", "organization_role", "scopes")
        .containsEntry("organization_id", second.toString())
        .containsEntry("user_account_id", userAccountId.toString())
        .containsEntry("organization_role", "owner")
        .containsEntry("scopes", List.of("admin"));
    assertThat(OffsetDateTime.parse(JsonPath.read(body, "$.created_at")))
        .isBetween(OffsetDateTime.now().minusMinutes(1), OffsetDateTime.now().plusMinutes(1));

    assertThat(
            mvc.put()
                .uri("/v1/sessions/current/organization")
                .header(AUTHORIZATION, member)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"organization_id\":\"%s\"}".formatted(second))
                .exchange())
        .hasStatusOk();
    assertThat(mvc.get().uri("/v1/customers").header(AUTHORIZATION, member).exchange())
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.customers[*].name")
        .asArray()
        .containsExactly("둘째 도입사 고객");
  }

  @Test
  void 소속_추가는_대상_도입사의_감사_행을_운영자_행위자로_남긴다() {
    UUID operatorId = userAccountIdOf(operator);
    UUID userAccountId =
        userAccountIdOf(TestCredentials.issueMemberSession(jdbcTemplate, UUID.randomUUID()));
    UUID organizationId = insertOrganization("소속 감사 도입사", "active", "2100-01-01T00:00:00Z");

    assertThat(createMember(operator, organizationId, userAccountId)).hasStatus(201);

    entityManager.flush();
    assertThat(
            jdbcTemplate.queryForMap(
                """
                SELECT actor_type, actor_id, organization_id, target_type, target_id
                FROM audit_log WHERE action = 'organization_member_created' AND organization_id = ?
                """,
                organizationId))
        .containsEntry("actor_type", "user_account")
        .containsEntry("actor_id", operatorId)
        .containsEntry("organization_id", organizationId)
        .containsEntry("target_type", "user_account")
        .containsEntry("target_id", userAccountId.toString());
  }

  @Test
  void 이미_소속된_사용자를_다시_소속시키면_409_organization_member_already_exists다() {
    UUID organizationId = UUID.randomUUID();
    UUID userAccountId =
        userAccountIdOf(TestCredentials.issueMemberSession(jdbcTemplate, organizationId));

    assertThat(createMember(operator, organizationId, userAccountId))
        .hasStatus(409)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo(ErrorCode.ORGANIZATION_MEMBER_ALREADY_EXISTS.getCode());
  }

  @Test
  void 없는_도입사나_사용자를_소속시키면_404다() {
    UUID organizationId = insertOrganization("소속 도입사", "active", "2100-01-01T00:00:00Z");
    UUID userAccountId =
        userAccountIdOf(TestCredentials.issueMemberSession(jdbcTemplate, UUID.randomUUID()));

    assertThat(createMember(operator, UUID.randomUUID(), userAccountId))
        .hasStatus(404)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo(ErrorCode.ORGANIZATION_NOT_FOUND.getCode());
    assertThat(createMember(operator, organizationId, UUID.randomUUID()))
        .hasStatus(404)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo(ErrorCode.USER_ACCOUNT_NOT_FOUND.getCode());
  }

  @Test
  void 소속_본문에_사용자가_없으면_400_validation_error다() {
    UUID organizationId = insertOrganization("소속 도입사", "active", "2100-01-01T00:00:00Z");

    assertThat(
            mvc.post()
                .uri(ORGANIZATIONS + "/{id}/members", organizationId)
                .header(AUTHORIZATION, operator)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .exchange())
        .hasStatus(400)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo(ErrorCode.VALIDATION_ERROR.getCode());
  }

  // --- 접근 ---

  @Test
  void member_세션과_API_키로_운영자_경로를_부르면_403_permission_denied다() {
    UUID organizationId = UUID.randomUUID();
    String member = TestCredentials.issueMemberSession(jdbcTemplate, organizationId);
    String apiKey = TestCredentials.issueApiKey(jdbcTemplate, organizationId);
    UUID userAccountId = userAccountIdOf(member);

    for (String authorization : List.of(member, apiKey)) {
      for (MvcTestResult result :
          List.of(
              mvc.get().uri(ORGANIZATIONS).header(AUTHORIZATION, authorization).exchange(),
              updateStatus(authorization, organizationId, "suspended"),
              createMember(authorization, organizationId, userAccountId))) {
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
                "SELECT status FROM organization WHERE id = ?", String.class, organizationId))
        .isEqualTo("active");
  }

  @Test
  void 인증_없이_운영자_경로를_부르면_401_authentication_required다() {
    assertThat(mvc.get().uri(ORGANIZATIONS).exchange())
        .hasStatus(401)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo(ErrorCode.AUTHENTICATION_REQUIRED.getCode());
  }

  // --- 헬퍼 ---

  @Test
  void 소속이_있는_운영자가_test_환경을_고른_채_다른_도입사_상태를_바꿔도_200이고_감사는_환경_없이_남는다() {
    String homeOperator = TestCredentials.issueMemberSession(jdbcTemplate, UUID.randomUUID());
    jdbcTemplate.update(
        "UPDATE user_account SET role='operator' WHERE id=?", userAccountIdOf(homeOperator));
    UUID target = insertOrganization("다른 도입사", "pending", "2100-01-01T00:00:00Z");

    MvcTestResult result =
        mvc.put()
            .uri(ORGANIZATIONS + "/{id}/status", target)
            .header(AUTHORIZATION, homeOperator)
            .header(BusinessEnvironmentInterceptor.HEADER, "test")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"status\":\"active\"}")
            .exchange();

    assertThat(result).hasStatusOk();
    entityManager.flush();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_log WHERE organization_id=? AND"
                    + " action='organization_status_updated' AND environment_id IS NULL",
                Integer.class,
                target))
        .isEqualTo(1);
  }

  private MvcTestResult updateStatus(String authorization, UUID organizationId, String status) {
    return mvc.put()
        .uri(ORGANIZATIONS + "/{id}/status", organizationId)
        .header(AUTHORIZATION, authorization)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"status\":\"%s\"}".formatted(status))
        .exchange();
  }

  private MvcTestResult createMember(
      String authorization, UUID organizationId, UUID userAccountId) {
    return mvc.post()
        .uri(ORGANIZATIONS + "/{id}/members", organizationId)
        .header(AUTHORIZATION, authorization)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"user_account_id\":\"%s\"}".formatted(userAccountId))
        .exchange();
  }

  private UUID userAccountIdOf(String authorization) {
    return jdbcTemplate.queryForObject(
        "SELECT user_account_id FROM user_session WHERE token_hash = sha256(convert_to(?, 'UTF8'))",
        UUID.class,
        TestCredentials.rawKeyOf(authorization));
  }

  private UUID insertOrganization(String name, String status, String createdAt) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO organization (name, status, created_at) VALUES (?, ?, CAST(? AS TIMESTAMPTZ)) RETURNING id",
        UUID.class,
        name,
        status,
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
