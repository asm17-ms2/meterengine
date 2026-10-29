package com.meterengine.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.meterengine.TestCredentials;
import com.meterengine.TestcontainersConfiguration;
import com.meterengine.metric.service.BillableMetricUsageService;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.util.List;
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
class PricePlanAuditIntegrationTest {
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private EntityManager entityManager;
  @Autowired private JsonMapper jsonMapper;
  @Autowired private WebApplicationContext webApplicationContext;

  private MockMvcTester mvc;
  private UUID organizationId;
  private UUID customerId;
  private String authorization;
  private YearMonth month;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcTester.from(
            webApplicationContext, builder -> builder.apply(springSecurity()).build());
    organizationId =
        jdbcTemplate.queryForObject(
            "INSERT INTO organization(name) VALUES ('요금제 감사 도입사') RETURNING id", UUID.class);
    customerId =
        jdbcTemplate.queryForObject(
            "INSERT INTO customer(organization_id,name) VALUES (?,'가격 적용 고객') RETURNING id",
            UUID.class,
            organizationId);
    authorization = TestCredentials.issueApiKey(jdbcTemplate, organizationId);
    month = YearMonth.now(BillableMetricUsageService.BILLING_ZONE);
  }

  @Test
  void 고객별_요금제_생성_감사는_금액과_기간과_고객_범위를_보존한다() {
    String planId = create(customerId, month.plusMonths(2), 500, 1000);
    JsonNode detail = details(planId, "price_plan_created").getFirst();
    assertSnapshot(detail, customerId, month.plusMonths(2), 500, 1000);
    assertThat(detail.has("previous")).isFalse();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT actor_type || ':' || target_type FROM audit_log WHERE organization_id=? AND"
                    + " target_id=?",
                String.class,
                organizationId,
                planId))
        .isEqualTo("api_key:price_plan");
  }

  @Test
  void 같은_월_요금제를_갱신하면_이전_금액과_종료월을_함께_보존한다() {
    String planId = create(null, month.plusMonths(2), 500, 1000);
    assertThat(create(null, null, 700, 2000)).isEqualTo(planId);
    List<JsonNode> snapshots = details(planId, "price_plan_created");
    assertThat(snapshots).hasSize(2);
    JsonNode updated =
        snapshots.stream().filter(snapshot -> snapshot.has("previous")).findFirst().orElseThrow();
    assertSnapshot(updated, null, null, 700, 2000);
    assertSnapshot(updated.path("previous"), null, month.plusMonths(2), 500, 1000);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM price_plan WHERE organization_id=?",
                Integer.class,
                organizationId))
        .isEqualTo(1);
  }

  @Test
  void 요금제_삭제_감사는_삭제된_행의_고객과_금액과_기간을_보존한다() {
    String planId = create(customerId, month.plusMonths(3), 900, 3000);
    assertThat(
            mvc.delete()
                .uri("/v1/price-plans/" + planId)
                .header(AUTHORIZATION, authorization)
                .exchange())
        .hasStatus(204);
    JsonNode detail = details(planId, "price_plan_deleted").getFirst();
    assertSnapshot(detail, customerId, month.plusMonths(3), 900, 3000);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM price_plan WHERE id=?",
                Integer.class,
                UUID.fromString(planId)))
        .isZero();
    assertThat(details(planId, "price_plan_created")).hasSize(1);
  }

  @Test
  void 다른_도입사의_삭제_요청은_행과_감사_기록을_바꾸지_않는다() {
    String planId = create(null, null, 500, 1000);
    UUID otherOrganizationId = UUID.randomUUID();
    String otherAuthorization = TestCredentials.issueApiKey(jdbcTemplate, otherOrganizationId);
    assertThat(
            mvc.delete()
                .uri("/v1/price-plans/" + planId)
                .header(AUTHORIZATION, otherAuthorization)
                .exchange())
        .hasStatus(404);
    entityManager.flush();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM price_plan WHERE id=?",
                Integer.class,
                UUID.fromString(planId)))
        .isEqualTo(1);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_log WHERE organization_id=?",
                Integer.class,
                otherOrganizationId))
        .isZero();
    assertThat(details(planId, "price_plan_deleted")).isEmpty();
  }

  private String create(UUID scopedCustomerId, YearMonth until, long baseFee, long minimumAmount) {
    String body =
        "{\"customer_id\":%s,\"effective_from\":\"%s\",\"effective_until\":%s,\"base_fee\":%d,\"minimum_amount\":%d}"
            .formatted(
                scopedCustomerId == null ? "null" : '"' + scopedCustomerId.toString() + '"',
                month,
                until == null ? "null" : '"' + until.toString() + '"',
                baseFee,
                minimumAmount);
    MvcTestResult result =
        mvc.post()
            .uri("/v1/price-plans")
            .header(AUTHORIZATION, authorization)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body)
            .exchange();
    assertThat(result).hasStatus(201);
    return jsonMapper
        .readTree(new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8))
        .path("id")
        .asString();
  }

  private List<JsonNode> details(String planId, String action) {
    entityManager.flush();
    return jdbcTemplate
        .queryForList(
            "SELECT detail::text FROM audit_log WHERE organization_id=? AND target_id=? AND"
                + " action=?",
            String.class,
            organizationId,
            planId,
            action)
        .stream()
        .map(jsonMapper::readTree)
        .toList();
  }

  private void assertSnapshot(
      JsonNode detail, UUID scopedCustomerId, YearMonth until, long baseFee, long minimumAmount) {
    assertThat(detail.path("base_fee").asLong()).isEqualTo(baseFee);
    assertThat(detail.path("minimum_amount").asLong()).isEqualTo(minimumAmount);
    assertThat(detail.path("effective_from").asString()).isEqualTo(month.toString());
    if (scopedCustomerId == null) assertThat(detail.path("customer_id").isNull()).isTrue();
    else assertThat(detail.path("customer_id").asString()).isEqualTo(scopedCustomerId.toString());
    if (until == null) assertThat(detail.path("effective_until").isNull()).isTrue();
    else assertThat(detail.path("effective_until").asString()).isEqualTo(until.toString());
  }
}
