package com.meterengine.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.jayway.jsonpath.JsonPath;
import com.meterengine.TestCredentials;
import com.meterengine.TestcontainersConfiguration;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.context.WebApplicationContext;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@DirtiesContext
class EventMonthsIntegrationTest {
  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private JdbcTemplate jdbcTemplate;
  private MockMvcTester mvc;
  private UUID organizationId;
  private String authorization;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcTester.from(
            webApplicationContext, builder -> builder.apply(springSecurity()).build());
    organizationId = UUID.randomUUID();
    authorization = TestCredentials.issueApiKey(jdbcTemplate, organizationId, "read");
  }

  @Test
  void KST_월과_연도_경계로_구분하고_중복_없이_최신_월부터_반환한다() {
    UUID customer = customer(organizationId, null);
    insert(organizationId, customer, null, "october", "2026-10-31T14:59:59Z");
    insert(organizationId, customer, null, "november", "2026-10-31T15:00:00Z");
    insert(organizationId, customer, null, "same-month", "2026-11-10T00:00:00Z");
    insert(organizationId, customer, null, "year-boundary", "2025-12-31T15:00:00Z");
    assertThat(months(get(authorization, null))).containsExactly("2026-11", "2026-10", "2026-01");
  }

  @Test
  void 네_자리_연도로_열_수_없는_달은_월_목록에서_뺀다() {
    UUID customer = customer(organizationId, null);
    insert(organizationId, customer, null, "far-future", "55835-03-10T00:00:00Z");
    insert(organizationId, customer, null, "october", "2026-10-10T00:00:00Z");
    assertThat(months(get(authorization, null))).containsExactly("2026-10");
  }

  @Test
  void 같은_조직의_세_환경과_다른_조직의_월을_분리한다() {
    UUID testEnvironment = environment("test");
    UUID liveEnvironment = environment("live");
    insert(organizationId, customer(organizationId, null), null, "same-id", "2026-10-02T00:00:00Z");
    insert(
        organizationId,
        customer(organizationId, testEnvironment),
        testEnvironment,
        "same-id",
        "2026-11-02T00:00:00Z");
    insert(
        organizationId,
        customer(organizationId, liveEnvironment),
        liveEnvironment,
        "same-id",
        "2026-12-02T00:00:00Z");
    UUID otherOrganization = UUID.randomUUID();
    TestCredentials.issueApiKey(jdbcTemplate, otherOrganization, "read");
    insert(
        otherOrganization,
        customer(otherOrganization, null),
        null,
        "same-id",
        "2027-01-02T00:00:00Z");
    String testKey = key("test");
    String liveKey = key("live");
    assertThat(months(get(authorization, null))).containsExactly("2026-10");
    assertThat(months(get(testKey, null))).containsExactly("2026-11");
    assertThat(months(get(liveKey, "live"))).containsExactly("2026-12");
    assertThat(get(testKey, "live")).hasStatus(403);
    assertThat(get(authorization, "test")).hasStatus(403);
  }

  @Test
  void 수집_전용_키와_익명은_조회할_수_없고_검색_조건은_받지_않는다() {
    String ingest = TestCredentials.issueApiKey(jdbcTemplate, organizationId, "ingest");
    assertThat(get(ingest, null)).hasStatus(403);
    assertThat(mvc.get().uri("/v1/events/months").exchange()).hasStatus(401);
    assertThat(
            mvc.get()
                .uri("/v1/events/months")
                .param("q", "not-supported")
                .header(AUTHORIZATION, authorization)
                .exchange())
        .hasStatus(400);
  }

  @Test
  void 해당_환경에_저장한_이벤트가_없으면_월_목록이_빈_배열이다() {
    UUID testEnvironment = environment("test");
    insert(
        organizationId,
        customer(organizationId, testEnvironment),
        testEnvironment,
        "test-only",
        "2026-10-02T00:00:00Z");
    assertThat(months(get(authorization, null))).isEmpty();
  }

  private MvcTestResult get(String token, String environment) {
    var request = mvc.get().uri("/v1/events/months").header(AUTHORIZATION, token);
    if (environment != null) request.header("X-MeterEngine-Environment", environment);
    return request.exchange();
  }

  private UUID customer(UUID organization, UUID environment) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO customer(organization_id,environment_id,name) VALUES (?,?,'월 조회 고객') RETURNING id",
        UUID.class,
        organization,
        environment);
  }

  private UUID environment(String mode) {
    return jdbcTemplate.queryForObject(
        "SELECT id FROM organization_environment WHERE organization_id=? AND mode=?",
        UUID.class,
        organizationId,
        mode);
  }

  private String key(String mode) {
    String token = TestCredentials.issueApiKey(jdbcTemplate, organizationId, mode, "read");
    jdbcTemplate.update(
        "UPDATE api_key SET environment_id=? WHERE organization_id=? AND key_hash=sha256(convert_to(?,'UTF8'))",
        environment(mode),
        organizationId,
        TestCredentials.rawKeyOf(token));
    return token;
  }

  private void insert(
      UUID organization, UUID customer, UUID environment, String transaction, String timestamp) {
    jdbcTemplate.update(
        "INSERT INTO event(organization_id,customer_id,environment_id,transaction_id,type,properties,occurred_at) VALUES (?,?,?,?,'usage','{}',?::timestamptz)",
        organization,
        customer,
        environment,
        transaction,
        timestamp);
  }

  private static List<String> months(MvcTestResult result) {
    assertThat(result).hasStatusOk();
    return JsonPath.read(
        new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8),
        "$.months");
  }
}
