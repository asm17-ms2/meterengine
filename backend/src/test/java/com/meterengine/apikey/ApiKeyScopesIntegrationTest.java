package com.meterengine.apikey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.jayway.jsonpath.JsonPath;
import com.meterengine.TestCredentials;
import com.meterengine.TestcontainersConfiguration;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.security.SecureTokens;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
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
import org.springframework.web.context.WebApplicationContext;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ApiKeyScopesIntegrationTest {
  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private JdbcTemplate jdbcTemplate;
  private MockMvcTester mvc;
  private UUID organizationId;
  private String admin;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcTester.from(
            webApplicationContext, builder -> builder.apply(springSecurity()).build());
    organizationId = UUID.randomUUID();
    admin = TestCredentials.issueMemberSession(jdbcTemplate, organizationId);
  }

  @Test
  void 관리_키를_조회로_낮추면_같은_원문의_다음_요청부터_쓰기를_차단한다() {
    Key key = create(admin, "admin");
    Map<String, Object> before = identity(key.id());
    assertThat(update(admin, key.id(), "[\"read\"]"))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.scopes")
        .isEqualTo(List.of("read"));
    assertThat(
            mvc.get().uri("/v1/customers").header(AUTHORIZATION, bearer(key.secret())).exchange())
        .hasStatusOk();
    assertThat(
            mvc.post()
                .uri("/v1/customers")
                .header(AUTHORIZATION, bearer(key.secret()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"금지\"}")
                .exchange())
        .hasStatus(403);
    assertThat(identity(key.id())).containsExactlyInAnyOrderEntriesOf(before);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT key_hash=sha256(convert_to(?, 'UTF8')) FROM api_key WHERE id=?",
                Boolean.class,
                key.secret(),
                key.id()))
        .isTrue();
    String body = body(update(admin, key.id(), "[\"read\"]"));
    assertThat(body).doesNotContain("\"secret\"", "\"key_hash\"", key.secret());
  }

  @Test
  void 수집과_조회_권한을_같은_키에_각각_적용하고_조합할_수_있다() {
    Key key = create(admin, "read");
    UUID customerId = customer();
    assertThat(update(admin, key.id(), "[\"ingest\",\"ingest\"]"))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.scopes")
        .isEqualTo(List.of("ingest"));
    assertThat(ingest(key.secret(), customerId)).hasStatusOk();
    assertThat(
            mvc.get().uri("/v1/customers").header(AUTHORIZATION, bearer(key.secret())).exchange())
        .hasStatus(403);
    assertThat(update(admin, key.id(), "[\"read\",\"ingest\"]")).hasStatusOk();
    assertThat(
            mvc.get().uri("/v1/customers").header(AUTHORIZATION, bearer(key.secret())).exchange())
        .hasStatusOk();
    assertThat(ingest(key.secret(), customerId)).hasStatusOk();
    assertThat(
            mvc.put()
                .uri("/v1/customers/{id}", customerId)
                .header(AUTHORIZATION, bearer(key.secret()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"금지\"}")
                .exchange())
        .hasStatus(403);
  }

  @Test
  void 조회_사용자는_키를_읽을_수_있지만_생성과_수정과_폐기를_할_수_없다() {
    Key key = create(admin, "read");
    String reader = TestCredentials.issueMemberSession(jdbcTemplate, organizationId);
    scopes(reader, "read");
    assertThat(mvc.get().uri("/v1/api-keys").header(AUTHORIZATION, reader).exchange())
        .hasStatusOk();
    assertThat(createResult(reader, "admin")).hasStatus(403);
    assertThat(update(reader, key.id(), "[\"admin\"]")).hasStatus(403);
    assertThat(
            mvc.delete()
                .uri("/v1/api-keys/{id}", key.id())
                .header(AUTHORIZATION, reader)
                .exchange())
        .hasStatus(403);
    scopes(reader, "ingest");
    assertThat(mvc.get().uri("/v1/api-keys").header(AUTHORIZATION, reader).exchange())
        .hasStatus(403);
    assertThat(update(reader, key.id(), "[\"admin\"]")).hasStatus(403);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT scopes::text FROM api_key WHERE id=?", String.class, key.id()))
        .isEqualTo("{read}");
  }

  @Test
  void 모든_스코프의_API_키는_자신이나_다른_키의_권한을_바꿀_수_없다() {
    Key target = create(admin, "read");
    for (String scope : List.of("read", "ingest", "admin")) {
      Key caller = create(admin, scope);
      assertThat(update(bearer(caller.secret()), caller.id(), "[\"admin\"]")).hasStatus(403);
      assertThat(update(bearer(caller.secret()), target.id(), "[\"admin\"]")).hasStatus(403);
      assertThat(createResult(bearer(caller.secret()), "admin")).hasStatus(403);
    }
    assertThat(
            mvc.put()
                .uri("/v1/api-keys/{id}/scopes", target.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"scopes\":[\"admin\"]}")
                .exchange())
        .hasStatus(401);
  }

  @Test
  void 다른_도입사와_없는_키는_404이고_잘못된_권한은_400이다() {
    Key key = create(admin, "read");
    String other = TestCredentials.issueMemberSession(jdbcTemplate, UUID.randomUUID());
    assertThat(update(other, key.id(), "[\"admin\"]"))
        .hasStatus(404)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo(ErrorCode.API_KEY_NOT_FOUND.getCode());
    assertThat(update(admin, UUID.randomUUID(), "[\"read\"]")).hasStatus(404);
    for (String invalid :
        List.of("[]", "null", "[null]", "[\"operator\"]", "[\"\"]", "[\"read\\u0000\"]"))
      assertThat(update(admin, key.id(), invalid)).hasStatus(400);
    assertThat(
            mvc.put()
                .uri("/v1/api-keys/{id}/scopes", key.id())
                .header(AUTHORIZATION, admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .exchange())
        .hasStatus(400);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT scopes::text FROM api_key WHERE id=?", String.class, key.id()))
        .isEqualTo("{read}");
  }

  @Test
  void 폐기되거나_만료된_키의_권한은_변경하지_않고_재활성화하지_않는다() {
    Key revoked = create(admin, "read");
    assertThat(
            mvc.delete()
                .uri("/v1/api-keys/{id}", revoked.id())
                .header(AUTHORIZATION, admin)
                .exchange())
        .hasStatus(204);
    Key expired = create(admin, "read");
    jdbcTemplate.update(
        "UPDATE api_key SET expires_at=now()-interval '1 second' WHERE id=?", expired.id());
    for (Key key : List.of(revoked, expired)) {
      Map<String, Object> before = identity(key.id());
      assertThat(update(admin, key.id(), "[\"admin\"]"))
          .hasStatus(409)
          .bodyJson()
          .extractingPath("$.code")
          .isEqualTo(ErrorCode.API_KEY_UNAVAILABLE.getCode());
      assertThat(identity(key.id())).containsExactlyInAnyOrderEntriesOf(before);
      assertThat(
              mvc.get().uri("/v1/customers").header(AUTHORIZATION, bearer(key.secret())).exchange())
          .hasStatus(401);
    }
  }

  @Test
  void 권한_변경은_이전과_이후를_한번_감사하고_원문과_해시를_남기지_않는다() {
    Key key = create(admin, "admin");
    assertThat(update(admin, key.id(), "[\"read\",\"ingest\",\"read\"]")).hasStatusOk();
    assertThat(update(admin, key.id(), "[\"ingest\",\"read\"]")).hasStatusOk();
    List<Map<String, Object>> rows =
        jdbcTemplate.queryForList(
            "SELECT actor_type,actor_id,organization_id,target_type,target_id,detail::text AS detail FROM audit_log WHERE organization_id=? AND action='api_key_scopes_updated'",
            organizationId);
    assertThat(rows).hasSize(1);
    Map<String, Object> row = rows.getFirst();
    assertThat(row)
        .containsEntry("actor_type", "user_account")
        .containsEntry("organization_id", organizationId)
        .containsEntry("target_type", "api_key")
        .containsEntry("target_id", key.id().toString());
    assertThat((String) row.get("detail"))
        .doesNotContain(key.secret(), HexFormat.of().formatHex(SecureTokens.hash(key.secret())));
    Map<String, Object> detail = JsonPath.read((String) row.get("detail"), "$");
    assertThat(detail)
        .containsExactlyInAnyOrderEntriesOf(
            Map.of("from", List.of("admin"), "to", List.of("read", "ingest")));
  }

  @Test
  void 권한_변경과_폐기가_겹쳐도_폐기한_키를_되살리지_않는다() throws Exception {
    Key key = create(admin, "read");
    CountDownLatch start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var update =
          executor.submit(
              () -> {
                start.await();
                return update(admin, key.id(), "[\"admin\"]");
              });
      var revoke =
          executor.submit(
              () -> {
                start.await();
                return mvc.delete()
                    .uri("/v1/api-keys/{id}", key.id())
                    .header(AUTHORIZATION, admin)
                    .exchange();
              });
      start.countDown();
      assertThat(update.get(10, TimeUnit.SECONDS).getResponse().getStatus()).isIn(200, 409);
      assertThat(revoke.get(10, TimeUnit.SECONDS)).hasStatus(204);
    }
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT revoked_at FROM api_key WHERE id=?", OffsetDateTime.class, key.id()))
        .isNotNull();
    assertThat(
            mvc.get().uri("/v1/customers").header(AUTHORIZATION, bearer(key.secret())).exchange())
        .hasStatus(401);
  }

  private Key create(String authorization, String scope) {
    MvcTestResult result = createResult(authorization, scope);
    assertThat(result).hasStatus(201);
    return new Key(
        UUID.fromString(JsonPath.read(body(result), "$.id")),
        JsonPath.read(body(result), "$.secret"));
  }

  private MvcTestResult createResult(String authorization, String scope) {
    return mvc.post()
        .uri("/v1/api-keys")
        .header(AUTHORIZATION, authorization)
        .contentType(MediaType.APPLICATION_JSON)
        .content(
            "{\"label\":\"권한 검증\",\"scopes\":[\"%s\"],\"mode\":\"test\",\"rate_limit_per_minute\":1000}"
                .formatted(scope))
        .exchange();
  }

  private MvcTestResult update(String authorization, UUID id, String scopes) {
    return mvc.put()
        .uri("/v1/api-keys/{id}/scopes", id)
        .header(AUTHORIZATION, authorization)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"scopes\":" + scopes + "}")
        .exchange();
  }

  private Map<String, Object> identity(UUID id) {
    return jdbcTemplate.queryForMap(
        "SELECT organization_id,mode,label,last_four,created_at,expires_at,revoked_at,rate_limit_per_minute FROM api_key WHERE id=?",
        id);
  }

  private UUID customer() {
    MvcTestResult result =
        mvc.post()
            .uri("/v1/customers")
            .header(AUTHORIZATION, admin)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"수집 고객\"}")
            .exchange();
    assertThat(result).hasStatus(201);
    return UUID.fromString(JsonPath.read(body(result), "$.id"));
  }

  private MvcTestResult ingest(String secret, UUID customerId) {
    return mvc.post()
        .uri("/v1/events")
        .header(AUTHORIZATION, bearer(secret))
        .contentType(MediaType.APPLICATION_JSON)
        .content(
            "{\"transaction_id\":\"%s\",\"customer_id\":\"%s\",\"type\":\"scope_test\",\"timestamp\":\"%s\",\"properties\":{\"quantity\":1}}"
                .formatted(UUID.randomUUID(), customerId, OffsetDateTime.now()))
        .exchange();
  }

  private void scopes(String authorization, String scope) {
    jdbcTemplate.update(
        "UPDATE organization_member SET role='member',scopes=CAST(? AS VARCHAR[]) WHERE organization_id=? AND user_account_id=(SELECT user_account_id FROM user_session WHERE token_hash=sha256(convert_to(?,'UTF8')))",
        "{" + scope + "}",
        organizationId,
        TestCredentials.rawKeyOf(authorization));
  }

  private static String bearer(String secret) {
    return TestCredentials.BEARER_PREFIX + secret;
  }

  private static String body(MvcTestResult result) {
    try {
      return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    } catch (UnsupportedEncodingException exception) {
      throw new IllegalStateException("응답을 읽지 못했습니다", exception);
    }
  }

  private record Key(UUID id, String secret) {}
}
