package com.meterengine.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.jayway.jsonpath.JsonPath;
import com.meterengine.TestCredentials;
import com.meterengine.TestcontainersConfiguration;
import com.meterengine.global.security.BusinessEnvironmentInterceptor;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.context.WebApplicationContext;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class OrganizationEnvironmentIntegrationTest {
  private static final String HEADER = BusinessEnvironmentInterceptor.HEADER;
  @Autowired private WebApplicationContext context;
  @Autowired private JdbcTemplate jdbcTemplate;
  private MockMvcTester mvc;
  private UUID organizationId;
  private String session;

  @BeforeEach
  void setUp() {
    mvc = MockMvcTester.from(context, builder -> builder.apply(springSecurity()).build());
    organizationId = UUID.randomUUID();
    session = TestCredentials.issueMemberSession(jdbcTemplate, organizationId);
  }

  @Test
  void 같은_조직의_세_범위에서_같은_외부_ID를_독립적으로_사용한다() {
    MvcTestResult environments = get(session, "legacy", "/v1/organization/environments");
    assertThat(environments).hasStatusOk();
    assertThat((List<String>) JsonPath.read(body(environments), "$.environments[*].mode"))
        .containsExactly("test", "live");
    UUID legacy = createCustomer(session, "legacy", "same-customer");
    UUID test = createCustomer(session, "test", "same-customer");
    UUID live = createCustomer(session, "live", "same-customer");
    assertThat(List.of(legacy, test, live)).doesNotHaveDuplicates();
    assertCustomerList(session, "legacy", legacy);
    assertCustomerList(session, "test", test);
    assertCustomerList(session, "live", live);
    assertThat(createCustomer(session, "test", "same-customer")).isEqualTo(test);
    assertThat(get(session, "test", "/v1/customers/" + live)).hasStatus(404);
    assertThat(get(session, "legacy", "/v1/customers/" + test)).hasStatus(404);
  }

  @Test
  void 같은_세션의_요청별_환경은_다른_요청이나_세션에_저장되지_않는다() {
    UUID test = createCustomer(session, "test", "test-customer");
    UUID live = createCustomer(session, "live", "live-customer");
    for (int index = 0; index < 3; index++) {
      assertCustomerList(session, "test", test);
      assertCustomerList(session, "live", live);
      assertThat(get(session, null, "/v1/customers"))
          .hasStatusOk()
          .bodyJson()
          .extractingPath("$.customers")
          .asArray()
          .isEmpty();
    }
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM user_session WHERE organization_id = ?",
                Integer.class,
                organizationId))
        .isEqualTo(1);
    assertThat(get(session, "test,live", "/v1/customers")).hasStatus(400);
  }

  @Test
  void 키는_발급_환경에_고정되고_기존_키는_기존_범위에_남는다() {
    UUID test = createCustomer(session, "test", "test");
    UUID live = createCustomer(session, "live", "live");
    String testKey = createKey("test", "test");
    String liveKey = createKey("live", "live");
    assertCustomerList(testKey, null, test);
    assertCustomerList(liveKey, null, live);
    assertThat(get(testKey, "live", "/v1/customers")).hasStatus(403);
    assertThat(get(liveKey, "test", "/v1/customers")).hasStatus(403);
    String legacyKey = TestCredentials.issueApiKey(jdbcTemplate, organizationId, "test", "read");
    assertThat(get(legacyKey, "test", "/v1/customers")).hasStatus(403);
    assertThat(get(legacyKey, null, "/v1/customers"))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.customers")
        .asArray()
        .isEmpty();
    assertThat(
            post(
                session,
                "test",
                "/v1/api-keys",
                "{\"label\":\"wrong\",\"mode\":\"live\",\"scopes\":[\"read\"]}"))
        .hasStatus(403);
    assertThat(get(testKey, null, "/v1/api-keys")).hasStatus(403);
    assertThat(get(session, "test", "/v1/api-keys"))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.api_keys")
        .asArray()
        .hasSize(1);
  }

  @Test
  void 같은_거래_ID의_단건과_배치_수집은_환경마다_독립적이고_다른_환경_고객은_거절한다() {
    UUID test = createCustomer(session, "test", "same");
    UUID live = createCustomer(session, "live", "same");
    UUID legacy = createCustomer(session, "legacy", "same");
    for (var entry : Map.of("test", test, "live", live, "legacy", legacy).entrySet()) {
      assertThat(post(session, entry.getKey(), "/v1/events", event("shared-id", entry.getValue())))
          .hasStatusOk()
          .bodyJson()
          .extractingPath("$.duplicate")
          .isEqualTo(false);
      assertThat(get(session, entry.getKey(), "/v1/events"))
          .hasStatusOk()
          .bodyJson()
          .extractingPath("$.total")
          .isEqualTo(1);
    }
    assertThat(post(session, "test", "/v1/events", event("shared-id", test)))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.duplicate")
        .isEqualTo(true);
    assertThat(post(session, "test", "/v1/events", event("foreign-customer", live))).hasStatus(404);
    MvcTestResult batch =
        post(
            session,
            "test",
            "/v1/event-batches",
            "{\"events\":["
                + event("batch-local", test)
                + ","
                + event("batch-foreign", live)
                + "]}");
    assertThat(batch).hasStatusOk();
    assertThat((List<String>) JsonPath.read(body(batch), "$.results[*].result"))
        .containsExactly("stored", "rejected");
    assertThat((String) JsonPath.read(body(batch), "$.results[1].error.code"))
        .isEqualTo("customer_not_found");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM event WHERE organization_id = ? AND transaction_id='shared-id'",
                Integer.class,
                organizationId))
        .isEqualTo(3);
  }

  @Test
  void 동일_환경_FK는_직접_SQL의_환경_혼합도_막는다() {
    UUID testCustomer = createCustomer(session, "test", "test");
    UUID liveEnvironment = environment("live");
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO event (organization_id, environment_id, transaction_id, customer_id, type, occurred_at) VALUES (?, ?, 'mixed-env', ?, 'usage', now())",
                    organizationId,
                    liveEnvironment,
                    testCustomer))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO event (organization_id, transaction_id, customer_id, type, occurred_at) VALUES (?, 'legacy-mixed', ?, 'usage', now())",
                    organizationId,
                    testCustomer))
        .isInstanceOf(DataIntegrityViolationException.class);
    UUID otherOrganization = UUID.randomUUID();
    TestCredentials.issueApiKey(jdbcTemplate, otherOrganization);
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO customer (id, organization_id, environment_id, name) VALUES (?, ?, ?, 'foreign')",
                    UUID.randomUUID(),
                    otherOrganization,
                    environment("test")))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void 명시_환경의_업무_조회와_공통_계정_조회가_열린다() {
    createCustomer(session, "test", "test");
    for (String path :
        List.of("/v1/usage", "/v1/invoices", "/v1/billable-metrics", "/v1/unbilled-events")) {
      assertThat(get(session, "test", path)).hasStatusOk();
    }
    assertThat(get(session, "legacy", "/v1/unbilled-events"))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.unmatched_type.event_count")
        .isEqualTo(0);
    assertThat(get(session, null, "/v1/sessions/current")).hasStatusOk();
    assertThat(get(session, "test", "/v1/sessions/current")).hasStatusOk();
    assertThat(get(session, "live", "/v1/organization/members")).hasStatusOk();
  }

  @Test
  void 공급자_설정은_환경마다_독립적이고_기존_정보를_복사하지_않는다() {
    for (String mode : List.of("legacy", "test", "live")) {
      assertThat(get(session, mode, "/v1/organization/supplier"))
          .hasStatusOk()
          .bodyJson()
          .extractingPath("$.business_registration_number")
          .isNull();
    }
    for (String mode : List.of("legacy", "test", "live")) {
      assertThat(
              mvc.put()
                  .uri("/v1/organization/supplier")
                  .header(AUTHORIZATION, session)
                  .header(HEADER, mode)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      "{\"business_registration_number\":\"1234567890\",\"representative_name\":\""
                          + mode
                          + "\",\"business_address\":\""
                          + mode
                          + " address\"}")
                  .exchange())
          .hasStatusOk();
    }
    for (String mode : List.of("legacy", "test", "live")) {
      assertThat(get(session, mode, "/v1/organization/supplier"))
          .hasStatusOk()
          .bodyJson()
          .extractingPath("$.representative_name")
          .isEqualTo(mode);
    }
  }

  @Test
  void 환경별_감사를_분리하고_기존_진단에_새_환경_이벤트를_섞지_않는다() {
    UUID test = createCustomer(session, "test", "test");
    UUID legacy = createCustomer(session, "legacy", "legacy");
    assertThat(post(session, "test", "/v1/events", event("only-test", test))).hasStatusOk();
    assertThat(get(session, "legacy", "/v1/unbilled-events"))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.unmatched_type.event_count")
        .isEqualTo(0);
    assertThat(get(session, "legacy", "/v1/audit-logs?action=customer_created"))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.audit_logs[0].target_id")
        .isEqualTo(legacy.toString());
    assertThat(get(session, "test", "/v1/audit-logs?action=customer_created"))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.audit_logs[0].target_id")
        .isEqualTo(test.toString());
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_log WHERE organization_id=? AND environment_id=? AND action='api_request_completed' AND detail->>'route'='/v1/customers' AND detail->>'method'='POST'",
                Integer.class,
                organizationId,
                environment("test")))
        .isEqualTo(1);
  }

  @Test
  void 도입사_공통_작업의_감사는_모든_환경의_감사_조회에_보인다() {
    jdbcTemplate.update(
        "UPDATE organization_member SET role='owner' WHERE organization_id=?", organizationId);
    assertThat(
            post(
                session,
                "test",
                "/v1/organization/invitations",
                "{\"email\":\"audit-common@example.com\"}"))
        .hasStatus(201);
    for (String mode : new String[] {"legacy", "test", "live"}) {
      assertThat(get(session, mode, "/v1/audit-logs?action=organization_invitation_created"))
          .hasStatusOk()
          .bodyJson()
          .extractingPath("$.total")
          .isEqualTo(1);
    }
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_log WHERE organization_id=? AND"
                    + " action='organization_invitation_created' AND environment_id IS NULL",
                Integer.class,
                organizationId))
        .isEqualTo(1);
  }

  @Test
  void 조직_고정_헤더가_공유_세션의_도입사_전환_뒤_옛_폼을_차단한다() {
    UUID otherOrganization = UUID.randomUUID();
    TestCredentials.issueApiKey(jdbcTemplate, otherOrganization);
    UUID user =
        jdbcTemplate.queryForObject(
            "SELECT user_account_id FROM user_session WHERE organization_id=?",
            UUID.class,
            organizationId);
    jdbcTemplate.update(
        "INSERT INTO organization_member(organization_id,user_account_id,created_at) VALUES (?,?,now())",
        otherOrganization,
        user);
    jdbcTemplate.update(
        "UPDATE user_session SET organization_id=? WHERE user_account_id=?",
        otherOrganization,
        user);
    assertThat(
            mvc.post()
                .uri("/v1/customers")
                .header(AUTHORIZATION, session)
                .header(
                    BusinessEnvironmentInterceptor.ORGANIZATION_HEADER, organizationId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"old form\"}")
                .exchange())
        .hasStatus(403);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM customer WHERE organization_id=?",
                Integer.class,
                otherOrganization))
        .isZero();
    assertThat(
            mvc.get()
                .uri("/v1/customers")
                .header(AUTHORIZATION, session)
                .header(
                    BusinessEnvironmentInterceptor.ORGANIZATION_HEADER,
                    otherOrganization.toString())
                .exchange())
        .hasStatusOk();
    assertThat(
            mvc.get()
                .uri("/v1/customers")
                .header(AUTHORIZATION, session)
                .header(BusinessEnvironmentInterceptor.ORGANIZATION_HEADER, "not-a-uuid")
                .exchange())
        .hasStatus(400);
    String key = TestCredentials.issueApiKey(jdbcTemplate, organizationId, "read");
    assertThat(
            mvc.get()
                .uri("/v1/customers")
                .header(AUTHORIZATION, key)
                .header(
                    BusinessEnvironmentInterceptor.ORGANIZATION_HEADER,
                    otherOrganization.toString())
                .exchange())
        .hasStatus(403);
  }

  private UUID createCustomer(String authorization, String mode, String externalId) {
    MvcTestResult result =
        post(
            authorization,
            mode,
            "/v1/customers",
            "{\"name\":\"" + mode + " customer\",\"external_id\":\"" + externalId + "\"}");
    assertThat(result).hasStatus2xxSuccessful();
    return UUID.fromString(JsonPath.read(body(result), "$.id"));
  }

  private void assertCustomerList(String authorization, String mode, UUID expected) {
    MvcTestResult result = get(authorization, mode, "/v1/customers");
    assertThat(result).hasStatusOk();
    assertThat((List<String>) JsonPath.read(body(result), "$.customers[*].id"))
        .containsExactly(expected.toString());
  }

  private String createKey(String selected, String mode) {
    MvcTestResult result =
        post(
            session,
            selected,
            "/v1/api-keys",
            "{\"label\":\"environment key\",\"mode\":\"" + mode + "\",\"scopes\":[\"admin\"]}");
    assertThat(result).hasStatus(201);
    assertThat((String) JsonPath.read(body(result), "$.environment_id"))
        .isEqualTo(environment(selected).toString());
    return "Bearer " + JsonPath.<String>read(body(result), "$.secret");
  }

  private UUID environment(String mode) {
    return jdbcTemplate.queryForObject(
        "SELECT id FROM organization_environment WHERE organization_id=? AND mode=?",
        UUID.class,
        organizationId,
        mode);
  }

  private MvcTestResult get(String authorization, String mode, String path) {
    var request = mvc.get().uri(path).header(AUTHORIZATION, authorization);
    if (mode != null) request.header(HEADER, mode);
    return request.exchange();
  }

  private MvcTestResult post(String authorization, String mode, String path, String payload) {
    var request =
        mvc.post()
            .uri(path)
            .header(AUTHORIZATION, authorization)
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload);
    if (mode != null) request.header(HEADER, mode);
    return request.exchange();
  }

  private static String event(String transactionId, UUID customerId) {
    return "{\"transaction_id\":\""
        + transactionId
        + "\",\"customer_id\":\""
        + customerId
        + "\",\"type\":\"new-environment-usage\",\"properties\":{\"tokens\":100},\"timestamp\":\""
        + OffsetDateTime.now()
        + "\"}";
  }

  private static String body(MvcTestResult result) {
    return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
  }
}
