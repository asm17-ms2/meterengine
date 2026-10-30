package com.meterengine.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.meterengine.TestCredentials;
import com.meterengine.TestcontainersConfiguration;
import com.meterengine.event.repository.EventRepository;
import com.meterengine.event.service.EventSearch;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.context.WebApplicationContext;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@DirtiesContext
class EventExportIntegrationTest {
  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private JdbcTemplate jdbcTemplate;
  @MockitoSpyBean private EventRepository eventRepository;
  private MockMvcTester mvc;
  private UUID organizationId;
  private UUID customerId;
  private String authorization;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcTester.from(
            webApplicationContext, builder -> builder.apply(springSecurity()).build());
    organizationId = UUID.randomUUID();
    authorization = TestCredentials.issueApiKey(jdbcTemplate, organizationId, "read");
    customerId = customer(organizationId, null, "내보내기 고객");
  }

  @Test
  void 목록의_고객과_유형과_속성과_검색과_정렬_조건으로_전체를_내보낸다() {
    insert(
        "included-z",
        customerId,
        null,
        "usage",
        "{\"model\":\"서울\",\"secret\":\"omit-this-value\"}",
        "2026-10-02T00:00:00+09:00");
    insert(
        "included-a", customerId, null, "usage", "{\"model\":\"서울\"}", "2026-10-03T00:00:00+09:00");
    insert(
        "wrong-type", customerId, null, "other", "{\"model\":\"서울\"}", "2026-10-03T00:00:00+09:00");
    insert(
        "wrong-property",
        customerId,
        null,
        "usage",
        "{\"model\":\"부산\"}",
        "2026-10-03T00:00:00+09:00");
    UUID otherCustomer = customer(organizationId, null, "다른 고객");
    insert(
        "wrong-customer",
        otherCustomer,
        null,
        "usage",
        "{\"model\":\"서울\"}",
        "2026-10-03T00:00:00+09:00");
    MvcTestResult result =
        get(
            authorization,
            null,
            "&customer_id="
                + customerId
                + "&type=usage&q=included&properties={\"model\":\"서울\"}&sort=transaction_id&order=asc&page=1&size=1");
    assertThat(result)
        .hasStatusOk()
        .hasContentTypeCompatibleWith(MediaType.parseMediaType("text/csv"));
    String csv = body(result);
    assertThat(csv)
        .startsWith("\ufeff\"transaction_id\"")
        .contains("included-a", "included-z")
        .doesNotContain("wrong-", "omit-this-value", "\"properties\"");
    assertThat(csv.indexOf("included-a")).isLessThan(csv.indexOf("included-z"));
    assertThat(csv.split("\r\n")).hasSize(3);
    assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
    assertThat(result.getResponse().getHeader("Content-Disposition"))
        .isEqualTo("attachment; filename=\"events.csv\"");
    assertThat(result.getResponse().getContentAsByteArray())
        .startsWith((byte) 0xef, (byte) 0xbb, (byte) 0xbf);
  }

  @Test
  void 자유_기간은_KST_종료일을_포함하고_종료_시각을_제외한다() {
    insert("before", customerId, null, "usage", "{}", "2026-10-01T23:59:59+09:00");
    insert("start", customerId, null, "usage", "{}", "2026-10-02T00:00:00+09:00");
    insert("last", customerId, null, "usage", "{}", "2026-10-02T23:59:59+09:00");
    insert("after", customerId, null, "usage", "{}", "2026-10-03T00:00:00+09:00");
    MvcTestResult result =
        mvc.get()
            .uri("/v1/events/export?from=2026-10-02&to=2026-10-02")
            .header(AUTHORIZATION, authorization)
            .exchange();
    assertThat(result).hasStatusOk();
    assertThat(body(result))
        .contains("\"start\"", "\"last\"")
        .doesNotContain("\"before\"", "\"after\"");
    MvcTestResult exact =
        mvc.get()
            .uri("/v1/events/export?from=2026-10-02T00:00:00&to=2026-10-02T23:59:59")
            .header(AUTHORIZATION, authorization)
            .exchange();
    assertThat(exact).hasStatusOk();
    assertThat(body(exact)).contains("\"start\"").doesNotContain("\"last\"");
  }

  @Test
  void 선택한_원문_JSON은_숫자_정밀도를_보존하고_문자열_수식은_이스케이프한다() {
    jdbcTemplate.update("UPDATE customer SET name=? WHERE id=?", "=SUM(\"x,y\")", customerId);
    insert(
        " \t@SUM(\"x\")",
        customerId,
        null,
        "+usage",
        "{\"integer\":9007199254740993,\"decimal\":0.1234567890123456789,\"private\":\"chosen-json\"}",
        "2026-10-02T00:00:00+09:00");
    MvcTestResult result = get(authorization, null, "&include_properties=true");
    assertThat(result).hasStatusOk();
    assertThat(body(result))
        .contains(
            "\"properties\"",
            "9007199254740993",
            "0.1234567890123456789",
            "chosen-json",
            "\"' \t@SUM(\"\"x\"\")\"",
            "\"'=SUM(\"\"x,y\"\")\"",
            "\"'+usage\"");
    assertThat(body(get(authorization, null, ""))).doesNotContain("chosen-json", "\"properties\"");
  }

  @Test
  void 페이지보다_많은_행을_빠짐없이_내보내고_검색의_퍼센트는_문자다() {
    insertMany(201);
    insert("literal%value", customerId, null, "usage", "{}", "2026-10-02T00:00:00+09:00");
    String csv = body(get(authorization, null, "&size=1&page=99"));
    assertThat(csv.split("\r\n")).hasSize(203);
    assertThat(csv).contains("bulk-00001", "bulk-00201");
    String filtered = body(get(authorization, null, "&q=%25"));
    assertThat(filtered).contains("literal%value").doesNotContain("bulk-");
  }

  @Test
  void 같은_조직의_legacy와_test와_live를_각각_격리한다() {
    UUID testEnvironment = environment("test");
    UUID liveEnvironment = environment("live");
    UUID testCustomer = customer(organizationId, testEnvironment, "테스트 고객");
    UUID liveCustomer = customer(organizationId, liveEnvironment, "실제 고객");
    insert("shared", customerId, null, "legacy", "{}", "2026-10-02T00:00:00+09:00");
    insert("shared", testCustomer, testEnvironment, "test", "{}", "2026-10-02T00:00:00+09:00");
    insert("shared", liveCustomer, liveEnvironment, "live", "{}", "2026-10-02T00:00:00+09:00");
    String testKey = key("test", "read");
    String liveKey = key("live", "read");
    assertThat(body(get(authorization, null, "")))
        .contains("\"legacy\"")
        .doesNotContain("테스트 고객", testCustomer.toString(), liveCustomer.toString());
    assertThat(body(get(testKey, null, "")))
        .contains(testCustomer.toString())
        .doesNotContain(customerId.toString(), liveCustomer.toString());
    assertThat(body(get(liveKey, "live", "")))
        .contains(liveCustomer.toString())
        .doesNotContain(customerId.toString(), testCustomer.toString());
    assertThat(get(testKey, "live", "")).hasStatus(403);
    assertThat(get(authorization, "test", "")).hasStatus(403);
    assertThat(get(testKey, "test", "&customer_id=" + customerId)).hasStatus(404);
  }

  @Test
  void 다른_조직의_행과_고객은_내보내지_않는다() {
    UUID otherOrganization = UUID.randomUUID();
    TestCredentials.issueApiKey(jdbcTemplate, otherOrganization, "read");
    UUID otherCustomer = customer(otherOrganization, null, "다른 조직 고객");
    jdbcTemplate.update(
        "INSERT INTO event(organization_id,transaction_id,customer_id,type,properties,occurred_at) VALUES (?,'foreign',?,'usage','{}','2026-10-02T00:00:00+09:00')",
        otherOrganization,
        otherCustomer);
    assertThat(body(get(authorization, null, "")))
        .doesNotContain("foreign", otherCustomer.toString());
    assertThat(get(authorization, null, "&customer_id=" + otherCustomer)).hasStatus(404);
    assertThat(
            mvc.get()
                .uri("/v1/events/export?month=2026-10")
                .header(AUTHORIZATION, authorization)
                .header("X-MeterEngine-Organization-Id", otherOrganization.toString())
                .exchange())
        .hasStatus(403);
  }

  @Test
  void 조회_권한_없는_키와_익명_요청은_거절한다() {
    String ingest = TestCredentials.issueApiKey(jdbcTemplate, organizationId, "ingest");
    assertThat(get(ingest, null, "")).hasStatus(403);
    assertThat(mvc.get().uri("/v1/events/export").exchange()).hasStatus(401);
  }

  @Test
  void 만_행까지_허용하고_초과하면_잘린_CSV_대신_오류를_반환한다() {
    insertMany(10000);
    MvcTestResult allowed = get(authorization, null, "");
    assertThat(allowed).hasStatusOk();
    assertThat(body(allowed).split("\r\n")).hasSize(10001);
    insert("over-limit", customerId, null, "usage", "{}", "2026-10-02T00:00:00+09:00");
    assertThat(get(authorization, null, ""))
        .hasStatus(400)
        .bodyJson()
        .extractingPath("$.errors[0].field")
        .isEqualTo("export");
  }

  @Test
  void 원문_속성을_포함할_때만_합계가_상한을_넘으면_큰_본문_대신_오류를_반환한다() {
    jdbcTemplate.update(
        "INSERT INTO event(organization_id,transaction_id,customer_id,type,properties,occurred_at) SELECT ?, 'large-' || sequence, ?, 'usage', jsonb_build_object('blob', repeat('x', 9 * 1024 * 1024)), '2026-10-02T00:00:00+09:00'::timestamptz FROM generate_series(1,4) sequence",
        organizationId,
        customerId);
    assertThat(get(authorization, null, "")).hasStatusOk();
    assertThat(get(authorization, null, "&include_properties=true"))
        .hasStatus(400)
        .bodyJson()
        .extractingPath("$.errors[0].field")
        .isEqualTo("export");
  }

  @Test
  void 원문_속성을_빼고_내보낼_때는_속성을_읽는_조회를_쓰지_않는다() {
    insert(
        "without-properties",
        customerId,
        null,
        "usage",
        "{\"blob\":\"x\"}",
        "2026-10-02T00:00:00+09:00");

    String csv = body(get(authorization, null, ""));

    assertThat(csv).contains("without-properties").doesNotContain("blob");
    verify(eventRepository, never())
        .findPage(
            eq(organizationId),
            nullable(UUID.class),
            nullable(String.class),
            any(EventSearch.class),
            anyInt(),
            anyInt());
  }

  @Test
  void 발생과_수신_시각을_KST_오프셋이_붙은_초_단위_형식으로_쓴다() {
    insert("kst-boundary", customerId, null, "usage", "{}", "2026-10-01T00:00:00+09:00");
    String csv = body(get(authorization, null, "&q=kst-boundary"));
    assertThat(csv).contains("\"2026-10-01T00:00:00+09:00\"").doesNotContain("2026-09-30T15:00Z");
  }

  @Test
  void 잘못된_속성과_기간과_정렬과_미지원_조건을_거절한다() {
    for (String suffix :
        new String[] {
          "&properties=[]",
          "&properties=invalid",
          "&sort=properties",
          "&order=invalid",
          "&unsupported=ignored",
          "&size=0"
        }) {
      assertThat(get(authorization, null, suffix))
          .hasStatus(400)
          .bodyJson()
          .extractingPath("$.code")
          .isEqualTo("validation_error");
    }
    assertThat(
            mvc.get()
                .uri("/v1/events/export?from=2026-10-01")
                .header(AUTHORIZATION, authorization)
                .exchange())
        .hasStatus(400);
    assertThat(get(authorization, null, "&from=2026-10-01&to=2026-10-31")).hasStatus(400);
  }

  @Test
  void 빈_결과도_원문_선택에_맞는_고정_열을_반환한다() {
    String csv = body(get(authorization, null, "&include_properties=true"));
    assertThat(csv).startsWith("\ufeff\"transaction_id\"").endsWith("\"properties\"\r\n");
    assertThat(csv.split("\r\n")).hasSize(1);
  }

  @Test
  void 건수_확인_뒤_커밋된_이벤트는_같은_내보내기에_섞이지_않는다() throws Exception {
    insert("snapshot-original", customerId, null, "usage", "{}", "2026-10-02T00:00:00+09:00");
    CountDownLatch counted = new CountDownLatch(1);
    CountDownLatch inserted = new CountDownLatch(1);
    doAnswer(
            invocation -> {
              Object total = invocation.callRealMethod();
              counted.countDown();
              if (!inserted.await(10, TimeUnit.SECONDS))
                throw new IllegalStateException("동시 삽입을 기다리지 못했다");
              return total;
            })
        .when(eventRepository)
        .count(
            eq(organizationId),
            nullable(UUID.class),
            nullable(String.class),
            any(EventSearch.class));
    CompletableFuture<MvcTestResult> response =
        CompletableFuture.supplyAsync(() -> get(authorization, null, ""));
    try {
      assertThat(counted.await(10, TimeUnit.SECONDS)).isTrue();
      insert("snapshot-late", customerId, null, "usage", "{}", "2026-10-02T00:00:00+09:00");
    } finally {
      inserted.countDown();
    }
    MvcTestResult result = response.get(10, TimeUnit.SECONDS);
    assertThat(result).hasStatusOk();
    assertThat(body(result)).contains("snapshot-original").doesNotContain("snapshot-late");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM event WHERE organization_id=?",
                Integer.class,
                organizationId))
        .isEqualTo(2);
  }

  private MvcTestResult get(String token, String environment, String suffix) {
    var request =
        mvc.get().uri("/v1/events/export").param("month", "2026-10").header(AUTHORIZATION, token);
    for (String parameter : suffix.split("&")) {
      if (parameter.isEmpty()) continue;
      String[] pair = parameter.split("=", 2);
      request.param(
          pair[0], URLDecoder.decode(pair.length > 1 ? pair[1] : "", StandardCharsets.UTF_8));
    }
    if (environment != null) request.header("X-MeterEngine-Environment", environment);
    return request.exchange();
  }

  private UUID customer(UUID organization, UUID environment, String name) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO customer(organization_id,environment_id,name) VALUES (?,?,?) RETURNING id",
        UUID.class,
        organization,
        environment,
        name);
  }

  private UUID environment(String mode) {
    return jdbcTemplate.queryForObject(
        "SELECT id FROM organization_environment WHERE organization_id=? AND mode=?",
        UUID.class,
        organizationId,
        mode);
  }

  private String key(String mode, String scope) {
    String token = TestCredentials.issueApiKey(jdbcTemplate, organizationId, mode, scope);
    jdbcTemplate.update(
        "UPDATE api_key SET environment_id=? WHERE organization_id=? AND key_hash=sha256(convert_to(?,'UTF8'))",
        environment(mode),
        organizationId,
        TestCredentials.rawKeyOf(token));
    return token;
  }

  private void insert(
      String transaction,
      UUID customer,
      UUID environment,
      String type,
      String properties,
      String timestamp) {
    jdbcTemplate.update(
        "INSERT INTO event(organization_id,transaction_id,customer_id,environment_id,type,properties,occurred_at) VALUES (?,?,?,?,?,?::jsonb,?::timestamptz)",
        organizationId,
        transaction,
        customer,
        environment,
        type,
        properties,
        timestamp);
  }

  private void insertMany(int count) {
    jdbcTemplate.update(
        "INSERT INTO event(organization_id,transaction_id,customer_id,type,properties,occurred_at) SELECT ?, 'bulk-' || lpad(sequence::text,5,'0'), ?, 'bulk', '{}', '2026-10-02T00:00:00+09:00'::timestamptz + sequence * INTERVAL '1 second' FROM generate_series(1,?) sequence",
        organizationId,
        customerId,
        count);
  }

  private static String body(MvcTestResult result) {
    return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
  }
}
