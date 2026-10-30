package com.meterengine.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.meterengine.TestCredentials;
import com.meterengine.TestcontainersConfiguration;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Collections;
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
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties = "meterengine.webhook.enabled=false")
@Import(TestcontainersConfiguration.class)
class EventBatchIntegrationTest {
  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private JsonMapper jsonMapper;
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
    authorization = TestCredentials.issueApiKey(jdbcTemplate, organizationId, "ingest", "read");
    customerId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO customer (id, organization_id, name) VALUES (?, ?, '고객')",
        customerId,
        organizationId);
  }

  @Test
  void 원소별_형식과_고객과_기간_오류를_다른_이벤트와_분리한다() {
    String now = OffsetDateTime.now().toString();
    String request =
        "{\"events\":["
            + event("a", customerId, now, "1")
            + ","
            + event("b", customerId, now, "2")
            + ","
            + event("bad-time", customerId, "wrong", "1")
            + ","
            + event("missing", UUID.randomUUID(), now, "1")
            + ","
            + event("old", customerId, OffsetDateTime.now().minusDays(36).toString(), "1")
            + ","
            + event("a", customerId, now, "99")
            + "]}";
    MvcTestResult response = post(request);
    assertThat(response).hasStatusOk();
    JsonNode results = body(response).path("results");
    assertThat(results.get(0).path("result").asString()).isEqualTo("stored");
    assertThat(results.get(1).path("result").asString()).isEqualTo("stored");
    assertThat(results.get(2).path("error").path("code").asString()).isEqualTo("validation_error");
    assertThat(results.get(2).path("error").path("errors").get(0).path("field").asString())
        .isEqualTo("events[2].timestamp");
    assertThat(results.get(3).path("error").path("code").asString())
        .isEqualTo("customer_not_found");
    assertThat(results.get(4).path("error").path("code").asString()).isEqualTo("event_too_old");
    assertThat(results.get(5).path("result").asString()).isEqualTo("duplicate");
    JsonNode retried = body(post(request)).path("results");
    assertThat(retried.get(0).path("result").asString()).isEqualTo("duplicate");
    assertThat(retried.get(1).path("result").asString()).isEqualTo("duplicate");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT properties->>'quantity' FROM event WHERE organization_id = ? AND transaction_id = 'a'",
                String.class,
                organizationId))
        .isEqualTo("1");
  }

  @Test
  void 배치에도_큰_정수와_소수의_자릿수를_보존한다() {
    String number = "123456789012345678901234567890.123456789012345678901234567890";
    assertThat(
            post(
                "{\"events\":["
                    + event("precise", customerId, OffsetDateTime.now().toString(), number)
                    + "]}"))
        .hasStatusOk();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT properties->>'quantity' FROM event WHERE organization_id = ? AND transaction_id = 'precise'",
                String.class,
                organizationId))
        .isEqualTo(number);
  }

  @Test
  void 빈_배열과_101건은_봉투_오류이고_null_원소는_그_원소만_거절한다() {
    assertThat(post("{\"events\":[]}")).hasStatus(400);
    String row = event("limit", customerId, OffsetDateTime.now().toString(), "1");
    assertThat(post("{\"events\":[" + String.join(",", Collections.nCopies(101, row)) + "]}"))
        .hasStatus(400);
    JsonNode results = body(post("{\"events\":[null," + row + "]}")).path("results");
    assertThat(results.get(0).path("result").asString()).isEqualTo("rejected");
    assertThat(results.get(1).path("result").asString()).isEqualTo("stored");
  }

  @Test
  void 다른_도입사의_고객은_행별로_거절하고_read_키는_수집을_거절한다() {
    String other = TestCredentials.issueApiKey(jdbcTemplate, UUID.randomUUID(), "ingest");
    String request =
        "{\"events\":[" + event("foreign", customerId, OffsetDateTime.now().toString(), "1") + "]}";
    MvcTestResult foreign =
        mvc.post()
            .uri("/v1/event-batches")
            .header("Authorization", other)
            .contentType(MediaType.APPLICATION_JSON)
            .content(request)
            .exchange();
    assertThat(body(foreign).path("results").get(0).path("error").path("code").asString())
        .isEqualTo("customer_not_found");
    authorization = TestCredentials.issueApiKey(jdbcTemplate, organizationId, "read");
    assertThat(post(request)).hasStatus(403);
  }

  private static String event(String id, UUID customerId, String timestamp, String quantity) {
    return "{\"transaction_id\":\""
        + id
        + "\",\"customer_id\":\""
        + customerId
        + "\",\"type\":\"usage\",\"properties\":{\"quantity\":"
        + quantity
        + "},\"timestamp\":\""
        + timestamp
        + "\"}";
  }

  @Test
  void 중복_ID여도_없는_고객을_먼저_거절한다() {
    String now = OffsetDateTime.now().toString();
    assertThat(post("{\"events\":[" + event("known", customerId, now, "1") + "]}")).hasStatusOk();
    JsonNode result =
        body(post("{\"events\":[" + event("known", UUID.randomUUID(), now, "1") + "]}"))
            .path("results")
            .get(0);
    assertThat(result.path("result").asString()).isEqualTo("rejected");
    assertThat(result.path("error").path("code").asString()).isEqualTo("customer_not_found");
  }

  private MvcTestResult post(String json) {
    return mvc.post()
        .uri("/v1/event-batches")
        .header("Authorization", authorization)
        .contentType(MediaType.APPLICATION_JSON)
        .content(json)
        .exchange();
  }

  private JsonNode body(MvcTestResult result) {
    return jsonMapper.readTree(
        new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8));
  }
}
