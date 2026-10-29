package com.meterengine.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.meterengine.TestCredentials;
import com.meterengine.TestcontainersConfiguration;
import java.nio.charset.StandardCharsets;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class ConditionalCreationIntegrationTest {
  @Autowired private WebApplicationContext context;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private JsonMapper json;
  private MockMvcTester mvc;
  private UUID organizationId;
  private String authorization;

  @BeforeEach
  void setUp() {
    mvc = MockMvcTester.from(context, builder -> builder.apply(springSecurity()).build());
    organizationId = UUID.randomUUID();
    authorization = TestCredentials.issueApiKey(jdbc, organizationId);
  }

  @Test
  void 외부_ID가_없어도_같은_고객_생성_키는_첫_응답을_반환한다() {
    String key = UUID.randomUUID().toString();
    MvcTestResult first = create("/v1/customers", key, "{\"name\":\"한 번 생성\"}");
    MvcTestResult second = create("/v1/customers", key, "{\"name\":\"한 번 생성\"}");
    assertThat(first).hasStatus(201);
    assertThat(second).hasStatus(201);
    assertThat(data(first)).isEqualTo(data(second));
    assertThat(second.getResponse().getHeader("Idempotency-Replayed")).isEqualTo("true");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM customer WHERE organization_id = ?",
                Integer.class,
                organizationId))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT key_hash FROM idempotent_request WHERE organization_id = ?",
                String.class,
                organizationId))
        .hasSize(64)
        .isNotEqualTo(key);
    assertThat(create("/v1/customers", key, "{\"name\":\"다른 요청\"}")).hasStatus(409);
  }

  @Test
  @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
  void 같은_생성_키는_조직과_작업별로_독립한다() {
    String key = UUID.randomUUID().toString();
    MvcTestResult legacy = create("/v1/customers", key, "{\"name\":\"환경별 생성\"}");
    assertThat(create("/v1/billable-metrics", key, metricBody("metric-a", "첫 미터"))).hasStatus(201);
    UUID otherOrganization = UUID.randomUUID();
    String otherAuthorization = TestCredentials.issueApiKey(jdbc, otherOrganization);
    MvcTestResult other =
        mvc.post()
            .uri("/v1/customers")
            .header(AUTHORIZATION, otherAuthorization)
            .header("Idempotency-Key", key)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"환경별 생성\"}")
            .exchange();
    assertThat(other).hasStatus(201);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM idempotent_request WHERE key_hash = encode(sha256(convert_to(?,'UTF8')), 'hex')",
                Integer.class,
                key))
        .isEqualTo(3);
  }

  @Test
  void 미터_생성_재요청은_첫_응답을_유지하고_다른_본문은_409를_반환한다() {
    String key = UUID.randomUUID().toString();
    MvcTestResult first = create("/v1/billable-metrics", key, metricBody("metric-a", "첫 미터"));
    MvcTestResult replay = create("/v1/billable-metrics", key, metricBody("metric-a", "첫 미터"));
    assertThat(first).hasStatus(201);
    assertThat(replay).hasStatus(201);
    assertThat(data(first)).isEqualTo(data(replay));
    assertThat(create("/v1/billable-metrics", key, metricBody("metric-a", "다른 이름"))).hasStatus(409);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM billable_metric WHERE organization_id = ?",
                Integer.class,
                organizationId))
        .isEqualTo(1);
  }

  @Test
  void 오래된_고객_버전의_PATCH와_PUT은_첫_수정을_보존한다() {
    UUID customer =
        UUID.fromString(
            data(create("/v1/customers", UUID.randomUUID().toString(), "{\"name\":\"원래 이름\"}"))
                .get("id")
                .asString());
    MvcTestResult read =
        mvc.get().uri("/v1/customers/" + customer).header(AUTHORIZATION, authorization).exchange();
    assertThat(read.getResponse().getHeader("ETag")).isEqualTo("\"0\"");
    MvcTestResult changed =
        mvc.patch()
            .uri("/v1/customers/" + customer)
            .header(AUTHORIZATION, authorization)
            .header("If-Match", "\"0\"")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"먼저 바뀐 이름\"}")
            .exchange();
    assertThat(changed).hasStatusOk();
    assertThat(data(changed).get("version").asInt()).isEqualTo(1);
    assertThat(
            mvc.patch()
                .uri("/v1/customers/" + customer)
                .header(AUTHORIZATION, authorization)
                .header("If-Match", "\"0\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"오래된 폼\"}")
                .exchange())
        .hasStatus(409);
    assertThat(
            mvc.put()
                .uri("/v1/customers/" + customer)
                .header(AUTHORIZATION, authorization)
                .header("If-Match", "\"0\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"오래된 PUT\"}")
                .exchange())
        .hasStatus(409);
    assertThat(
            jdbc.queryForObject("SELECT name FROM customer WHERE id = ?", String.class, customer))
        .isEqualTo("먼저 바뀐 이름");
  }

  @Test
  void PATCH는_강한_숫자_버전을_요구하고_기존_PUT을_허용한다() {
    UUID customer =
        UUID.fromString(
            data(create("/v1/customers", UUID.randomUUID().toString(), "{\"name\":\"원래\"}"))
                .get("id")
                .asString());
    assertThat(
            mvc.patch()
                .uri("/v1/customers/" + customer)
                .header(AUTHORIZATION, authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"변경\"}")
                .exchange())
        .hasStatus(400);
    assertThat(
            mvc.patch()
                .uri("/v1/customers/" + customer)
                .header(AUTHORIZATION, authorization)
                .header("If-Match", "*")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"변경\"}")
                .exchange())
        .hasStatus(400);
    assertThat(
            mvc.put()
                .uri("/v1/customers/" + customer)
                .header(AUTHORIZATION, authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"기존 PUT\"}")
                .exchange())
        .hasStatusOk();
  }

  @Test
  void 오래된_미터_버전의_PATCH는_최신_정의를_덮어쓰지_않는다() {
    assertThat(
            create(
                "/v1/billable-metrics", UUID.randomUUID().toString(), metricBody("metric-a", "원래")))
        .hasStatus(201);
    MvcTestResult read =
        mvc.get()
            .uri("/v1/billable-metrics/metric-a")
            .header(AUTHORIZATION, authorization)
            .exchange();
    assertThat(read.getResponse().getHeader("ETag")).isEqualTo("\"0\"");
    MvcTestResult changed =
        mvc.patch()
            .uri("/v1/billable-metrics/metric-a")
            .header(AUTHORIZATION, authorization)
            .header("If-Match", "\"0\"")
            .contentType(MediaType.APPLICATION_JSON)
            .content(metricBody("metric-a", "먼저 바뀜"))
            .exchange();
    assertThat(changed).hasStatusOk();
    assertThat(data(changed).get("version").asInt()).isEqualTo(1);
    assertThat(
            mvc.patch()
                .uri("/v1/billable-metrics/metric-a")
                .header(AUTHORIZATION, authorization)
                .header("If-Match", "\"0\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content(metricBody("metric-a", "오래된 폼"))
                .exchange())
        .hasStatus(409);
    assertThat(
            jdbc.queryForObject(
                "SELECT name FROM billable_metric WHERE organization_id = ? AND code='metric-a'",
                String.class,
                organizationId))
        .isEqualTo("먼저 바뀜");
  }

  @Test
  @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
  void 같은_키의_동시_고객_생성은_하나만_저장하고_같은_응답을_반환한다() throws Exception {
    String key = UUID.randomUUID().toString();
    var ready = new java.util.concurrent.CountDownLatch(2);
    var start = new java.util.concurrent.CountDownLatch(1);
    try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
      java.util.concurrent.Callable<MvcTestResult> action =
          () -> {
            ready.countDown();
            if (!start.await(10, java.util.concurrent.TimeUnit.SECONDS))
              throw new IllegalStateException("start timeout");
            return create("/v1/customers", key, "{\"name\":\"동시 생성\"}");
          };
      var first = executor.submit(action);
      var second = executor.submit(action);
      assertThat(ready.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
      start.countDown();
      MvcTestResult left = first.get(30, java.util.concurrent.TimeUnit.SECONDS);
      MvcTestResult right = second.get(30, java.util.concurrent.TimeUnit.SECONDS);
      assertThat(left).hasStatus(201);
      assertThat(right).hasStatus(201);
      assertThat(data(left).get("id").asString()).isEqualTo(data(right).get("id").asString());
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM customer WHERE organization_id = ?",
                  Integer.class,
                  organizationId))
          .isEqualTo(1);
    }
  }

  @Test
  @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
  void 같은_버전의_동시_PATCH는_하나만_성공한다() throws Exception {
    UUID customer =
        UUID.fromString(
            data(create("/v1/customers", UUID.randomUUID().toString(), "{\"name\":\"원래\"}"))
                .get("id")
                .asString());
    var ready = new java.util.concurrent.CountDownLatch(2);
    var start = new java.util.concurrent.CountDownLatch(1);
    try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
      java.util.function.Function<String, java.util.concurrent.Callable<Integer>> patch =
          name ->
              () -> {
                ready.countDown();
                if (!start.await(10, java.util.concurrent.TimeUnit.SECONDS))
                  throw new IllegalStateException("start timeout");
                return mvc.patch()
                    .uri("/v1/customers/" + customer)
                    .header(AUTHORIZATION, authorization)
                    .header("If-Match", "\"0\"")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"" + name + "\"}")
                    .exchange()
                    .getResponse()
                    .getStatus();
              };
      var first = executor.submit(patch.apply("첫 변경"));
      var second = executor.submit(patch.apply("둘째 변경"));
      assertThat(ready.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
      start.countDown();
      assertThat(
              java.util.List.of(
                  first.get(30, java.util.concurrent.TimeUnit.SECONDS),
                  second.get(30, java.util.concurrent.TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(200, 409);
      assertThat(
              jdbc.queryForObject(
                  "SELECT version FROM customer WHERE id = ?", Long.class, customer))
          .isEqualTo(1L);
    }
  }

  private MvcTestResult create(String path, String key, String body) {
    return mvc.post()
        .uri(path)
        .header(AUTHORIZATION, authorization)
        .header("Idempotency-Key", key)
        .contentType(MediaType.APPLICATION_JSON)
        .content(body)
        .exchange();
  }

  private JsonNode data(MvcTestResult response) {
    return json.readTree(
        new String(response.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8));
  }

  private static String metricBody(String code, String name) {
    return "{\"code\":\""
        + code
        + "\",\"name\":\""
        + name
        + "\",\"event_type\":\"usage\",\"aggregation\":\"sum\",\"target_property\":\"tokens\"}";
  }
}
