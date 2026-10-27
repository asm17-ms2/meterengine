package com.meterengine.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.jayway.jsonpath.JsonPath;
import com.meterengine.TestCredentials;
import com.meterengine.TestcontainersConfiguration;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.context.WebApplicationContext;

@Import({TestcontainersConfiguration.class, EventIssueIntegrationTest.FixedClock.class})
@SpringBootTest
class EventIssueIntegrationTest {
  @TestConfiguration(proxyBeanMethods = false)
  static class FixedClock {
    @Bean
    @Primary
    Clock eventIssueClock() {
      return Clock.fixed(Instant.parse("2026-10-15T03:00:00Z"), ZoneOffset.UTC);
    }
  }

  @Autowired private WebApplicationContext context;
  @Autowired private JdbcTemplate jdbcTemplate;
  private MockMvcTester mvc;
  private UUID organization;
  private UUID customer;
  private String read;

  @BeforeEach
  void setUp() {
    mvc = MockMvcTester.from(context, builder -> builder.apply(springSecurity()).build());
    organization = UUID.randomUUID();
    read = TestCredentials.issueApiKey(jdbcTemplate, organization, "read");
    customer = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO customer(id,organization_id,name,external_id) VALUES (?,?,'진단"
            + " 고객','diagnostic-client')",
        customer,
        organization);
    metric("tokens", "usage", "sum", "tokens", "{model}");
    jdbcTemplate.update(
        "INSERT INTO"
            + " price_rate(organization_id,billable_metric_code,dimension_values,unit_price,effective_from)"
            + " VALUES (?,'tokens','{\"model\":\"paid\"}',10,'2026-10')",
        organization);
    event(
        "tx-unknown",
        "mystery",
        "{\"private\":\"never-return-this-value\"}",
        "2026-10-10T01:00:00Z");
    event(
        "tx-invalid",
        "usage",
        "{\"tokens\":\"bad\",\"model\":\"free\",\"private_email\":\"hidden@example.local\"}",
        "2026-10-10T02:00:00Z");
    event("tx-unpriced", "usage", "{\"tokens\":10,\"model\":\"free\"}", "2026-10-10T03:00:00Z");
    event("tx-priced", "usage", "{\"tokens\":3,\"model\":\"paid\"}", "2026-10-10T04:00:00Z");
    event("tx-negative", "usage", "{\"tokens\":-2,\"model\":\"paid\"}", "2026-10-10T05:00:00Z");
    event("tx-future", "mystery", "{}", "2026-10-20T00:00:00Z");
  }

  @Test
  void 저장된_문제_이벤트를_가격_경고와_연결하되_원본_개인정보를_반환하지_않는다() {
    MvcTestResult result = get(read, "");
    assertThat(result).hasStatusOk();
    String json = body(result);
    assertThat(number(json, "$.total")).isEqualTo(3);
    assertThat((List<String>) JsonPath.read(json, "$.issues[*].transaction_id"))
        .containsExactly("tx-unpriced", "tx-invalid", "tx-unknown");
    assertThat((Map<String, Integer>) JsonPath.read(json, "$.summary"))
        .containsEntry("unknown_event_type", 1)
        .containsEntry("invalid_target_property", 1)
        .containsEntry("unpriced_usage", 1);
    assertThat(number(json, "$.warnings.future_event_count")).isEqualTo(1);
    assertThat(number(json, "$.warnings.negative_event_metric_count")).isEqualTo(1);
    assertThat((String) JsonPath.read(json, "$.warnings.scope")).isEqualTo("organization_month");
    assertThat(json)
        .doesNotContain(
            "never-return-this-value",
            "hidden@example.local",
            "\"properties\"",
            "\"key_hash\"",
            "\"secret\"");
    assertThat((String) JsonPath.read(json, "$.cutoff_at")).startsWith("2026-10-15T12:00");
  }

  @Test
  void 필터_전체_문제행_합계와_페이지를_분리하고_검색을_부분문자열로_처리한다() {
    MvcTestResult first = get(read, "&size=1&page=0");
    MvcTestResult second = get(read, "&size=1&page=1");
    assertThat(first).hasStatusOk();
    assertThat(second).hasStatusOk();
    assertThat(number(body(first), "$.total")).isEqualTo(3);
    assertThat((List<?>) JsonPath.read(body(first), "$.issues")).hasSize(1);
    assertThat((String) JsonPath.read(body(first), "$.issues[0].transaction_id"))
        .isNotEqualTo((String) JsonPath.read(body(second), "$.issues[0].transaction_id"));
    String reason = body(get(read, "&reason=unpriced_usage&q=tx-unpriced&customer_id=" + customer));
    assertThat(number(reason, "$.total")).isEqualTo(1);
    assertThat(number(reason, "$.summary.unknown_event_type")).isZero();
    assertThat(number(body(get(read, "&q=diagnostic-client")), "$.total")).isEqualTo(3);
    assertThat(number(body(get(read, "&q=%25")), "$.total")).isZero();
  }

  @Test
  void 한_이벤트에_복수_미터_문제가_있으면_각각_근거를_보인다() {
    metric("other_tokens", "usage", "sum", "other", "{}");
    event("multi-event", "usage", "{\"tokens\":\"bad\",\"other\":\"bad\"}", "2026-10-11T00:00:00Z");
    MvcTestResult result = get(read, "&q=multi-event");
    assertThat(result).hasStatusOk();
    assertThat(number(body(result), "$.total")).isEqualTo(2);
    assertThat((List<String>) JsonPath.read(body(result), "$.issues[*].billable_metric_code"))
        .containsExactly("other_tokens", "tokens");
    assertThat((List<String>) JsonPath.read(body(result), "$.issues[*].reason"))
        .containsOnly("invalid_target_property");
  }

  @Test
  void 실제_가격_계산이_단가를_찾으면_문제_목록에서도_제외한다() {
    jdbcTemplate.update(
        "INSERT INTO"
            + " price_rate(organization_id,billable_metric_code,dimension_values,unit_price,effective_from,customer_id)"
            + " VALUES (?,'tokens','{\"model\":\"free\"}',5,'2026-10',?)",
        organization,
        customer);
    assertThat(number(body(get(read, "&reason=unpriced_usage")), "$.total")).isZero();
    jdbcTemplate.update(
        "UPDATE price_rate SET archived_at=now(), archived_from='2026-10' WHERE organization_id=?"
            + " AND customer_id=?",
        organization,
        customer);
    assertThat(number(body(get(read, "&reason=unpriced_usage")), "$.total")).isEqualTo(1);
  }

  @Test
  void COUNT와_DISTINCT의_입력_허용_범위를_다른_집계와_구분한다() {
    metric("requests", "counted", "count", null, "{}");
    metric("users", "distinct", "count_distinct", "user", "{}");
    event("count-event", "counted", "{}", "2026-10-11T00:00:00Z");
    event("distinct-invalid", "distinct", "{\"user\":{\"nested\":1}}", "2026-10-11T00:00:00Z");
    assertThat(
            (List<String>) JsonPath.read(body(get(read, "&q=count-event")), "$.issues[*].reason"))
        .containsExactly("unpriced_usage");
    assertThat(
            (List<String>)
                JsonPath.read(body(get(read, "&q=distinct-invalid")), "$.issues[*].reason"))
        .containsExactly("invalid_target_property");
  }

  @Test
  void 조회_키만_허용하고_다른_조직과_명시_환경의_자료를_섞지_않는다() {
    String ingest = TestCredentials.issueApiKey(jdbcTemplate, organization, "ingest");
    assertThat(get(ingest, "")).hasStatus(403);
    UUID other = UUID.randomUUID();
    String otherRead = TestCredentials.issueApiKey(jdbcTemplate, other, "read");
    assertThat(number(body(get(otherRead, "")), "$.total")).isZero();
    assertThat(get(otherRead, "&customer_id=" + customer)).hasStatus(404);
  }

  @Test
  void 사유와_페이지_입력이_잘못되면_구체적인_400으로_거절한다() {
    for (String suffix :
        List.of("&reason=unknown", "&page=-1", "&size=101", "&customer_id=bad", "&from=2026-10-01"))
      assertThat(get(read, suffix)).hasStatus(400);
  }

  private void metric(
      String code, String type, String aggregation, String property, String dimensions) {
    jdbcTemplate.update(
        "INSERT INTO"
            + " billable_metric(organization_id,code,name,event_type,aggregation,target_property,dimension_properties)"
            + " VALUES (?,?,?,?,?,?,?::varchar[])",
        organization,
        code,
        code,
        type,
        aggregation,
        property,
        dimensions);
  }

  private void event(String transaction, String type, String properties, String timestamp) {
    jdbcTemplate.update(
        "INSERT INTO event(organization_id,transaction_id,customer_id,type,properties,occurred_at)"
            + " VALUES (?,?,?,?,?::jsonb,?::timestamptz)",
        organization,
        transaction,
        customer,
        type,
        properties,
        timestamp);
  }

  private MvcTestResult get(String authorization, String suffix) {
    return mvc.get()
        .uri("/v1/event-issues/search?month=2026-10" + suffix)
        .header(AUTHORIZATION, authorization)
        .exchange();
  }

  private static int number(String json, String path) {
    return JsonPath.<Number>read(json, path).intValue();
  }

  private static String body(MvcTestResult result) {
    return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
  }
}
