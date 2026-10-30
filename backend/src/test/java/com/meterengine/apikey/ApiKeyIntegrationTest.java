package com.meterengine.apikey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.jayway.jsonpath.JsonPath;
import com.meterengine.TestCredentials;
import com.meterengine.TestcontainersConfiguration;
import com.meterengine.apikey.dto.ApiKeySecretResponse;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.security.SecureTokens;
import jakarta.persistence.EntityManager;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HexFormat;
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

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class ApiKeyIntegrationTest {

  private static final String API_KEYS = "/v1/api-keys";

  private static final String LIVE_KEY_PATTERN = "^me_live_[A-Za-z0-9]{32}$";

  private static final String TEST_KEY_PATTERN = "^me_test_[A-Za-z0-9]{32}$";

  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private EntityManager entityManager;

  private MockMvcTester mvc;

  private UUID organizationId;

  private String member;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcTester.from(
            webApplicationContext, builder -> builder.apply(springSecurity()).build());
    organizationId = UUID.randomUUID();
    member = TestCredentials.issueMemberSession(jdbcTemplate, organizationId);
  }

  // --- 발급 ---

  @Test
  void 발급하면_201과_원문을_돌려주고_해시와_끝_네_글자만_저장한다() {
    MvcTestResult result =
        createApiKey(
            member,
            "{\"label\":\"운영 서버\",\"scopes\":[\"read\",\"ingest\",\"read\"],\"mode\":\"live\"}");

    assertThat(result).hasStatus(201);
    String body = body(result);
    String secret = JsonPath.read(body, "$.secret");
    assertThat(secret).matches(LIVE_KEY_PATTERN);
    assertThat((String) JsonPath.read(body, "$.label")).isEqualTo("운영 서버");
    assertThat((String) JsonPath.read(body, "$.mode")).isEqualTo("live");
    assertThat((List<String>) JsonPath.read(body, "$.scopes")).containsExactly("read", "ingest");
    assertThat((String) JsonPath.read(body, "$.last_four"))
        .isEqualTo(secret.substring(secret.length() - 4));
    assertThat(OffsetDateTime.parse(JsonPath.read(body, "$.created_at")))
        .isBetween(OffsetDateTime.now().minusMinutes(1), OffsetDateTime.now().plusMinutes(1));
    assertThat((Object) JsonPath.read(body, "$.expires_at")).isNull();
    assertThat((Object) JsonPath.read(body, "$.revoked_at")).isNull();

    entityManager.flush();
    Map<String, Object> row =
        jdbcTemplate.queryForMap(
            "SELECT organization_id, mode, label, key_hash = sha256(convert_to(?, 'UTF8')) AS hash_matches, last_four, scopes::text AS scopes, rate_limit_per_minute FROM api_key WHERE id = ?",
            secret,
            UUID.fromString(JsonPath.read(body, "$.id")));
    assertThat(row)
        .containsEntry("organization_id", organizationId)
        .containsEntry("mode", "live")
        .containsEntry("label", "운영 서버")
        .containsEntry("hash_matches", true)
        .containsEntry("last_four", secret.substring(secret.length() - 4))
        .containsEntry("scopes", "{read,ingest}")
        .containsEntry("rate_limit_per_minute", null);
  }

  @Test
  void 발급한_키로_사용량을_조회한다() {
    String secret = secretOf(createApiKey(member, "운영 서버", "live", "read"));

    assertThat(mvc.get().uri("/v1/usage").header(AUTHORIZATION, bearer(secret)).exchange())
        .hasStatusOk();
  }

  @Test
  void test_모드로_발급하면_me_test_키이고_그_키로_요청한다() {
    MvcTestResult result = createApiKey(member, "시험 서버", "test", "admin");

    assertThat(result).hasStatus(201).bodyJson().extractingPath("$.mode").isEqualTo("test");
    String secret = secretOf(result);
    assertThat(secret).matches(TEST_KEY_PATTERN);
    assertThat(mvc.get().uri("/v1/usage").header(AUTHORIZATION, bearer(secret)).exchange())
        .hasStatusOk();
  }

  @Test
  void 발급_응답의_문자열_표현은_원문을_가린다() {
    String secret = "me_live_" + "a".repeat(32);
    ApiKeySecretResponse response =
        new ApiKeySecretResponse(
            UUID.randomUUID(),
            "가림 확인",
            "live",
            List.of("read"),
            "aaaa",
            OffsetDateTime.now(),
            null,
            null,
            secret);

    assertThat(response.toString()).doesNotContain(secret).contains("(redacted)");
  }

  @Test
  void 요청_본문이_올바르지_않으면_400이고_키를_만들지_않는다() {
    String validation = ErrorCode.VALIDATION_ERROR.getCode();
    List<Map.Entry<String, String>> cases =
        List.of(
            Map.entry("{\"label\":\"키\",\"scopes\":[],\"mode\":\"live\"}", validation),
            Map.entry("{\"label\":\"키\",\"mode\":\"live\"}", validation),
            Map.entry("{\"label\":\"키\",\"scopes\":[\"write\"],\"mode\":\"live\"}", validation),
            Map.entry("{\"label\":\"키\",\"scopes\":[\"read\",null],\"mode\":\"live\"}", validation),
            Map.entry("{\"label\":\"키\",\"scopes\":[\"read\"]}", validation),
            Map.entry("{\"label\":\"키\",\"scopes\":[\"read\"],\"mode\":\"prod\"}", validation),
            Map.entry("{\"scopes\":[\"read\"],\"mode\":\"live\"}", validation),
            Map.entry("{\"label\":\" \",\"scopes\":[\"read\"],\"mode\":\"live\"}", validation),
            Map.entry(
                "{\"label\":\"%s\",\"scopes\":[\"read\"],\"mode\":\"live\"}"
                    .formatted("가".repeat(256)),
                validation),
            Map.entry(
                "{\"label\":\"키\",\"scopes\":[\"read\"],\"mode\":\"live\",\"replaced_api_key_id\":\"not-a-uuid\"}",
                ErrorCode.MALFORMED_REQUEST_BODY.getCode()));

    for (Map.Entry<String, String> invalid : cases) {
      assertThat(createApiKey(member, invalid.getKey()))
          .as(invalid.getKey())
          .hasStatus(400)
          .bodyJson()
          .extractingPath("$.code")
          .isEqualTo(invalid.getValue());
    }
    assertThat(apiKeyCount()).isZero();
  }

  // --- 목록 ---

  @Test
  void 목록은_만든_순서로_폐기된_키를_담고_원문은_싣지_않는다() {
    MvcTestResult first = createApiKey(member, "첫 키", "live", "read");
    MvcTestResult second = createApiKey(member, "둘째 키", "test", "ingest");
    UUID firstId = idOf(first);
    UUID secondId = idOf(second);
    entityManager.flush();
    jdbcTemplate.update(
        "UPDATE api_key SET created_at = now() - interval '1 hour' WHERE id = ?", firstId);
    entityManager.clear();
    assertThat(deleteApiKey(member, firstId)).hasStatus(204);
    String otherMember = TestCredentials.issueMemberSession(jdbcTemplate, UUID.randomUUID());
    createApiKey(otherMember, "남의 키", "live", "read");

    MvcTestResult result = mvc.get().uri(API_KEYS).header(AUTHORIZATION, member).exchange();

    assertThat(result).hasStatusOk();
    String body = body(result);
    List<Map<String, Object>> apiKeys = JsonPath.read(body, "$.api_keys");
    assertThat(apiKeys)
        .extracting(apiKey -> apiKey.get("id"))
        .containsExactly(firstId.toString(), secondId.toString());
    assertThat(apiKeys.get(0).keySet())
        .containsExactlyInAnyOrder(
            "id",
            "label",
            "mode",
            "scopes",
            "last_four",
            "created_at",
            "expires_at",
            "revoked_at",
            "rate_limit_per_minute",
            "status");
    assertThat(apiKeys.get(0).get("revoked_at")).isNotNull();
    assertThat(apiKeys.get(1))
        .containsEntry("label", "둘째 키")
        .containsEntry("mode", "test")
        .containsEntry("scopes", List.of("ingest"))
        .containsEntry("revoked_at", null);
    assertThat(body).doesNotContain(secretOf(first)).doesNotContain(secretOf(second));
  }

  @Test
  void 상태별_목록은_현재_사용가능한_키와_폐기와_만료를_구분하고_도입사를_격리한다() {
    MvcTestResult active = createApiKey(member, "사용 중", "live", "read");
    MvcTestResult revoked = createApiKey(member, "폐기", "live", "read");
    MvcTestResult expired = createApiKey(member, "만료", "test", "ingest");
    MvcTestResult revokedExpired = createApiKey(member, "폐기 뒤 만료", "live", "read");
    assertThat(deleteApiKey(member, idOf(revoked))).hasStatus(204);
    assertThat(deleteApiKey(member, idOf(revokedExpired))).hasStatus(204);
    entityManager.flush();
    jdbcTemplate.update(
        "UPDATE api_key SET expires_at = now() - interval '1 second' WHERE id IN (?, ?)",
        idOf(expired),
        idOf(revokedExpired));
    entityManager.clear();
    String otherMember = TestCredentials.issueMemberSession(jdbcTemplate, UUID.randomUUID());
    createApiKey(otherMember, "다른 도입사", "live", "read");

    Map<String, List<String>> expected =
        Map.of(
            "active", List.of(idOf(active).toString()),
            "revoked", List.of(idOf(revoked).toString(), idOf(revokedExpired).toString()),
            "expired", List.of(idOf(expired).toString()),
            "all",
                List.of(
                    idOf(active).toString(),
                    idOf(revoked).toString(),
                    idOf(expired).toString(),
                    idOf(revokedExpired).toString()));
    expected.forEach(
        (status, ids) -> {
          MvcTestResult result =
              mvc.get()
                  .uri(API_KEYS + "?status=" + status)
                  .header(AUTHORIZATION, member)
                  .exchange();
          assertThat(result).hasStatusOk();
          assertThat(JsonPath.<List<String>>read(body(result), "$.api_keys[*].id"))
              .containsExactlyInAnyOrderElementsOf(ids);
          if (!status.equals("all"))
            assertThat(JsonPath.<List<String>>read(body(result), "$.api_keys[*].status"))
                .containsOnly(status);
          assertThat(body(result))
              .doesNotContain(secretOf(active), secretOf(revoked), secretOf(expired));
        });
    assertThat(
            mvc.get().uri("/v1/usage").header(AUTHORIZATION, bearer(secretOf(expired))).exchange())
        .hasStatus(401);
    assertThat(mvc.get().uri(API_KEYS).header(AUTHORIZATION, member).exchange())
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.api_keys.length()")
        .isEqualTo(4);
  }

  @Test
  void API키_목록의_알수없는_상태는_전체목록으로_무시하지_않는다() {
    assertThat(
            mvc.get().uri(API_KEYS + "?status=archived").header(AUTHORIZATION, member).exchange())
        .hasStatus(400)
        .bodyJson()
        .extractingPath("$.errors[0].field")
        .isEqualTo("status");
  }

  // --- 교체 ---

  @Test
  void 교체하면_옛_키는_유예_동안_쓰이고_유예가_끝나면_401이다() {
    MvcTestResult old = createApiKey(member, "옛 키", "live", "read");
    UUID oldId = idOf(old);
    String oldSecret = secretOf(old);

    MvcTestResult replacement =
        createApiKey(
            member,
            "{\"label\":\"새 키\",\"scopes\":[\"read\"],\"mode\":\"live\",\"replaced_api_key_id\":\"%s\"}"
                .formatted(oldId));

    assertThat(replacement).hasStatus(201);
    assertThat((Object) JsonPath.read(body(replacement), "$.expires_at")).isNull();
    assertThat(expiresAtOf(oldId))
        .isBetween(
            OffsetDateTime.now().plus(Duration.ofHours(24)).minusMinutes(1),
            OffsetDateTime.now().plus(Duration.ofHours(24)).plusMinutes(1));
    assertThat(mvc.get().uri("/v1/usage").header(AUTHORIZATION, bearer(oldSecret)).exchange())
        .hasStatusOk();

    entityManager.flush();
    entityManager.clear();
    jdbcTemplate.update(
        "UPDATE api_key SET expires_at = now() - interval '1 second' WHERE id = ?", oldId);

    assertThat(mvc.get().uri("/v1/usage").header(AUTHORIZATION, bearer(oldSecret)).exchange())
        .hasStatus(401)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo(ErrorCode.INVALID_CREDENTIALS.getCode());
    assertThat(
            mvc.get()
                .uri("/v1/usage")
                .header(AUTHORIZATION, bearer(secretOf(replacement)))
                .exchange())
        .hasStatusOk();
  }

  @Test
  void 교체_대상에_유예보다_이른_만료_시각이_있으면_그대로_둔다() {
    UUID oldId = idOf(createApiKey(member, "곧 만료될 키", "live", "read"));
    entityManager.flush();
    jdbcTemplate.update(
        "UPDATE api_key SET expires_at = now() + interval '1 hour' WHERE id = ?", oldId);
    entityManager.clear();
    OffsetDateTime earlier = expiresAtOf(oldId);

    assertThat(replace(member, oldId)).hasStatus(201);

    assertThat(expiresAtOf(oldId)).isAtSameInstantAs(earlier);
  }

  @Test
  void 다른_도입사의_키나_폐기된_키나_없는_키를_교체_대상으로_지정하면_404이고_키를_만들지_않는다() {
    String otherMember = TestCredentials.issueMemberSession(jdbcTemplate, UUID.randomUUID());
    UUID othersKey = idOf(createApiKey(otherMember, "남의 키", "live", "read"));
    UUID revokedKey = idOf(createApiKey(member, "폐기한 키", "live", "read"));
    assertThat(deleteApiKey(member, revokedKey)).hasStatus(204);
    long before = apiKeyCount();

    for (UUID target : List.of(othersKey, revokedKey, UUID.randomUUID())) {
      assertThat(replace(member, target))
          .as(target.toString())
          .hasStatus(404)
          .bodyJson()
          .extractingPath("$.code")
          .isEqualTo(ErrorCode.API_KEY_NOT_FOUND.getCode());
    }
    assertThat(apiKeyCount()).isEqualTo(before);
    assertThat(expiresAtOf(othersKey)).isNull();
  }

  // --- 폐기 ---

  @Test
  void 폐기하면_204이고_그_키는_401이며_다시_폐기해도_204이고_폐기_시각은_그대로다() {
    MvcTestResult created = createApiKey(member, "폐기할 키", "live", "read");
    UUID id = idOf(created);
    String secret = secretOf(created);

    assertThat(deleteApiKey(member, id)).hasStatus(204);

    assertThat(mvc.get().uri("/v1/usage").header(AUTHORIZATION, bearer(secret)).exchange())
        .hasStatus(401)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo(ErrorCode.INVALID_CREDENTIALS.getCode());
    entityManager.flush();
    jdbcTemplate.update(
        "UPDATE api_key SET revoked_at = revoked_at - interval '1 hour' WHERE id = ?", id);
    entityManager.clear();
    OffsetDateTime revokedAt = revokedAtOf(id);

    assertThat(deleteApiKey(member, id)).hasStatus(204);

    assertThat(revokedAtOf(id)).isAtSameInstantAs(revokedAt);
  }

  @Test
  void 다른_도입사의_키나_없는_키를_폐기하면_404_api_key_not_found이고_그_키는_그대로_쓰인다() {
    String otherMember = TestCredentials.issueMemberSession(jdbcTemplate, UUID.randomUUID());
    MvcTestResult others = createApiKey(otherMember, "남의 키", "live", "read");

    for (UUID target : List.of(idOf(others), UUID.randomUUID())) {
      assertThat(deleteApiKey(member, target))
          .as(target.toString())
          .hasStatus(404)
          .bodyJson()
          .extractingPath("$.code")
          .isEqualTo(ErrorCode.API_KEY_NOT_FOUND.getCode());
    }
    assertThat(revokedAtOf(idOf(others))).isNull();
    assertThat(
            mvc.get().uri("/v1/usage").header(AUTHORIZATION, bearer(secretOf(others))).exchange())
        .hasStatusOk();
  }

  // --- 접근 ---

  @Test
  void admin_스코프_API_키로_키_관리_경로를_부르면_403_permission_denied다() {
    String apiKey = TestCredentials.issueApiKey(jdbcTemplate, organizationId, "admin");

    for (MvcTestResult result :
        List.of(
            mvc.get().uri(API_KEYS).header(AUTHORIZATION, apiKey).exchange(),
            createApiKey(apiKey, "키로 만든 키", "live", "read"),
            deleteApiKey(apiKey, UUID.randomUUID()))) {
      assertThat(result)
          .hasStatus(403)
          .bodyJson()
          .extractingPath("$.code")
          .isEqualTo(ErrorCode.PERMISSION_DENIED.getCode());
    }
  }

  @Test
  void 도입사가_없는_operator_세션은_403_permission_denied다() {
    String operator = TestCredentials.issueOperatorSession(jdbcTemplate);

    assertThat(mvc.get().uri(API_KEYS).header(AUTHORIZATION, operator).exchange())
        .hasStatus(403)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo(ErrorCode.PERMISSION_DENIED.getCode());
  }

  @Test
  void 인증_없이_부르면_401_authentication_required다() {
    assertThat(mvc.get().uri(API_KEYS).exchange())
        .hasStatus(401)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo(ErrorCode.AUTHENTICATION_REQUIRED.getCode());
  }

  // --- 감사 ---

  @Test
  void 발급과_교체와_폐기는_감사_행을_남기고_원문과_해시는_남기지_않는다() {
    MvcTestResult old = createApiKey(member, "감사 옛 키", "live", "read");
    UUID oldId = idOf(old);
    MvcTestResult replacement = replace(member, oldId);
    UUID newId = idOf(replacement);
    MvcTestResult deleted = deleteApiKey(member, oldId);
    assertThat(deleted).hasStatus(204);
    entityManager.flush();
    UUID userAccountId =
        jdbcTemplate.queryForObject(
            "SELECT user_account_id FROM organization_member WHERE organization_id = ?",
            UUID.class,
            organizationId);

    List<Map<String, Object>> rows =
        jdbcTemplate.queryForList(
            """
            SELECT action, actor_type, actor_id, organization_id, target_type, target_id,
                   detail::text AS detail
            FROM audit_log WHERE organization_id = ? ORDER BY occurred_at, action
            """,
            organizationId);

    List<Map<String, Object>> semanticRows =
        rows.stream().filter(row -> !"api_request_completed".equals(row.get("action"))).toList();
    List<Map<String, Object>> requestRows =
        rows.stream().filter(row -> "api_request_completed".equals(row.get("action"))).toList();
    assertThat(semanticRows)
        .extracting(row -> row.get("action") + " " + row.get("target_id"))
        .containsExactlyInAnyOrder(
            "api_key_created " + oldId, "api_key_created " + newId, "api_key_deleted " + oldId);
    assertThat(semanticRows)
        .allSatisfy(
            row -> {
              assertThat(row.get("actor_type")).isEqualTo("user_account");
              assertThat(row.get("actor_id")).isEqualTo(userAccountId);
              assertThat(row.get("target_type")).isEqualTo("api_key");
            });
    Map<String, Object> replacedDetail =
        JsonPath.read(
            (String)
                semanticRows.stream()
                    .filter(row -> row.get("target_id").equals(newId.toString()))
                    .findFirst()
                    .orElseThrow()
                    .get("detail"),
            "$");
    assertThat(replacedDetail)
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(
                "label",
                "교체한 키",
                "mode",
                "live",
                "scopes",
                List.of("read"),
                "replaced_api_key_id",
                oldId.toString()));
    Map<String, Object> createdDetail =
        JsonPath.read(
            (String)
                semanticRows.stream()
                    .filter(
                        row ->
                            row.get("target_id").equals(oldId.toString())
                                && row.get("action").equals("api_key_created"))
                    .findFirst()
                    .orElseThrow()
                    .get("detail"),
            "$");
    assertThat(createdDetail).containsEntry("replaced_api_key_id", null);
    assertThat(createdDetail.keySet())
        .containsExactlyInAnyOrder("label", "mode", "scopes", "replaced_api_key_id");
    assertThat(requestRows).hasSize(3);
    for (MvcTestResult request : List.of(old, replacement, deleted)) {
      String requestId = request.getResponse().getHeader("X-Request-ID");
      Map<String, Object> requestRow =
          requestRows.stream()
              .filter(
                  row ->
                      requestId.equals(JsonPath.read((String) row.get("detail"), "$.request_id")))
              .findFirst()
              .orElseThrow();
      Map<String, Object> requestDetail = JsonPath.read((String) requestRow.get("detail"), "$");
      assertThat(requestDetail)
          .containsEntry("method", request == deleted ? "DELETE" : "POST")
          .containsEntry("route", request == deleted ? API_KEYS + "/{id}" : API_KEYS)
          .containsEntry("status", request == deleted ? 204 : 201);
      if (request == deleted) {
        assertThat(requestRow.get("target_id")).isEqualTo(oldId.toString());
        assertThat(requestDetail)
            .containsOnlyKeys("method", "route", "status", "duration_ms", "request_id", "path_ids")
            .containsEntry("path_ids", Map.of("id", oldId.toString()));
      } else {
        assertThat(requestRow.get("target_id")).isNull();
        assertThat(requestDetail)
            .containsOnlyKeys("method", "route", "status", "duration_ms", "request_id");
      }
    }
    assertThat(requestRows)
        .allSatisfy(
            row -> {
              assertThat(row.get("actor_type")).isEqualTo("user_account");
              assertThat(row.get("actor_id")).isEqualTo(userAccountId);
              assertThat(row.get("target_type")).isEqualTo("http_request");
            });
    for (MvcTestResult created : List.of(old, replacement)) {
      String secret = secretOf(created);
      String hashHex = HexFormat.of().formatHex(SecureTokens.hash(secret));
      assertThat(rows)
          .extracting(row -> (String) row.get("detail"))
          .noneMatch(detail -> detail.contains(secret) || detail.contains(hashHex));
    }
  }

  // --- 헬퍼 ---

  private MvcTestResult createApiKey(
      String authorization, String label, String mode, String... scopes) {
    String scopesJson =
        String.join(",", List.of(scopes).stream().map(scope -> "\"" + scope + "\"").toList());
    return createApiKey(
        authorization,
        "{\"label\":\"%s\",\"scopes\":[%s],\"mode\":\"%s\"}".formatted(label, scopesJson, mode));
  }

  private MvcTestResult createApiKey(String authorization, String body) {
    return mvc.post()
        .uri(API_KEYS)
        .header(AUTHORIZATION, authorization)
        .contentType(MediaType.APPLICATION_JSON)
        .content(body)
        .exchange();
  }

  private MvcTestResult replace(String authorization, UUID replacedApiKeyId) {
    return createApiKey(
        authorization,
        "{\"label\":\"교체한 키\",\"scopes\":[\"read\"],\"mode\":\"live\",\"replaced_api_key_id\":\"%s\"}"
            .formatted(replacedApiKeyId));
  }

  private MvcTestResult deleteApiKey(String authorization, UUID id) {
    return mvc.delete().uri(API_KEYS + "/{id}", id).header(AUTHORIZATION, authorization).exchange();
  }

  private OffsetDateTime expiresAtOf(UUID id) {
    entityManager.flush();
    return jdbcTemplate.queryForObject(
        "SELECT expires_at FROM api_key WHERE id = ?", OffsetDateTime.class, id);
  }

  private OffsetDateTime revokedAtOf(UUID id) {
    entityManager.flush();
    return jdbcTemplate.queryForObject(
        "SELECT revoked_at FROM api_key WHERE id = ?", OffsetDateTime.class, id);
  }

  private long apiKeyCount() {
    entityManager.flush();
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM api_key WHERE organization_id = ?", Long.class, organizationId);
  }

  private static UUID idOf(MvcTestResult created) {
    assertThat(created).hasStatus(201);
    return UUID.fromString(JsonPath.read(body(created), "$.id"));
  }

  private static String secretOf(MvcTestResult created) {
    assertThat(created).hasStatus(201);
    return JsonPath.read(body(created), "$.secret");
  }

  private static String bearer(String secret) {
    return TestCredentials.BEARER_PREFIX + secret;
  }

  private static String body(MvcTestResult result) {
    try {
      return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    } catch (UnsupportedEncodingException e) {
      throw new IllegalStateException("응답 본문을 읽지 못했다", e);
    }
  }
}
