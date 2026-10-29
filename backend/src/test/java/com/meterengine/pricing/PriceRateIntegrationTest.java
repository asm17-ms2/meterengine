package com.meterengine.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.meterengine.TestCredentials;
import com.meterengine.TestcontainersConfiguration;
import com.meterengine.global.error.BusinessException;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.InvalidRequestException;
import com.meterengine.global.error.NotFoundException;
import com.meterengine.metric.service.BillableMetricUsageService;
import com.meterengine.pricing.dto.CreatePriceRateRequest;
import com.meterengine.pricing.dto.PriceRateResponse;
import com.meterengine.pricing.service.PriceRateService;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
class PriceRateIntegrationTest {

  private static final String BILLABLE_METRIC_CODE = "input-tokens";

  @Autowired private PriceRateService priceRateService;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private EntityManager entityManager;
  @Autowired private WebApplicationContext webApplicationContext;

  private final Map<UUID, String> authorizationsByOrganizationId = new HashMap<>();

  private MockMvcTester mvc;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcTester.from(
            webApplicationContext, builder -> builder.apply(springSecurity()).build());
  }

  @Test
  void 조합_단가를_등록하면_jsonb_조합과_단가가_저장된다() {
    UUID organizationId = insertOrganizationWithDimensionProperties("{model,region}");

    PriceRateResponse response =
        create(organizationId, Map.of("model", "opus", "region", "kr"), "0.007");

    assertThat(response.unitPrice()).isEqualByComparingTo("0.007");
    assertThat(storedUnitPrice(organizationId, "{\"model\": \"opus\", \"region\": \"kr\"}"))
        .isEqualByComparingTo("0.007");
  }

  @Test
  void 키_순서만_다른_같은_조합을_다시_등록하면_단가를_덮어쓰고_행은_하나다() {
    UUID organizationId = insertOrganizationWithDimensionProperties("{model,region}");
    Map<String, Object> ordered = new LinkedHashMap<>();
    ordered.put("model", "opus");
    ordered.put("region", "kr");
    create(organizationId, ordered, "0.007");
    Map<String, Object> reordered = new LinkedHashMap<>();
    reordered.put("region", "kr");
    reordered.put("model", "opus");

    PriceRateResponse response = create(organizationId, reordered, "0.009");

    assertThat(response.unitPrice()).isEqualByComparingTo("0.009");
    assertThat(storedUnitPrice(organizationId, "{\"model\": \"opus\", \"region\": \"kr\"}"))
        .isEqualByComparingTo("0.009");
    assertThat(priceRateCount(organizationId)).isEqualTo(1);
  }

  @Test
  void 키가_없는_미터는_빈_객체_단가를_저장한다() {
    UUID organizationId = insertOrganizationWithDimensionProperties("{}");

    create(organizationId, Map.of(), "120");

    assertThat(storedUnitPrice(organizationId, "{}")).isEqualByComparingTo("120");
  }

  @Test
  void 키가_있는_미터에_빈_객체_단가는_Invalid고_저장은_0건이다() {
    UUID organizationId = insertOrganizationWithDimensionProperties("{model}");

    assertThatThrownBy(() -> create(organizationId, Map.of(), "120"))
        .isInstanceOf(InvalidRequestException.class)
        .extracting(exception -> ((BusinessException) exception).getErrorCode())
        .isEqualTo(ErrorCode.INVALID_PRICE_RATE);
    assertThat(priceRateCount(organizationId)).isZero();
  }

  @Test
  void 미터가_없으면_NotFound고_저장은_0건이다() {
    UUID organizationId = insertOrganization();

    assertThatThrownBy(() -> create(organizationId, Map.of(), "1"))
        .isInstanceOf(NotFoundException.class)
        .extracting(exception -> ((BusinessException) exception).getErrorCode())
        .isEqualTo(ErrorCode.BILLABLE_METRIC_NOT_FOUND);
    assertThat(priceRateCount(organizationId)).isZero();
  }

  @Test
  void POST하면_201이고_저장된_단가가_응답이다() {
    UUID organizationId = insertOrganizationWithDimensionProperties("{model}");

    assertThat(
            post(
                organizationId,
                "{\"dimension_values\": {\"model\": \"opus\"}, \"unit_price\": 0.007}"))
        .hasStatus(201)
        .bodyJson()
        .extractingPath("$.dimension_values.model")
        .isEqualTo("opus");
    assertThat(priceRateCount(organizationId)).isEqualTo(1);
  }

  @Test
  void DELETE하면_204이고_그_조합의_이력을_보관한다() {
    UUID organizationId = insertOrganizationWithDimensionProperties("{model}");
    create(organizationId, Map.of("model", "opus"), "0.007");
    create(organizationId, Map.of("model", "haiku"), "1");

    assertThat(delete(organizationId, "{\"model\":\"opus\"}")).hasStatus(204);
    entityManager.flush();

    assertThat(priceRateCount(organizationId)).isEqualTo(2);
    assertThat(storedUnitPrice(organizationId, "{\"model\": \"haiku\"}")).isEqualByComparingTo("1");
    assertThat(archivedFrom(organizationId, "{\"model\": \"opus\"}"))
        .isEqualTo(BillableMetricUsageService.currentMonth().plusMonths(1).toString());
    assertThat(archivedFrom(organizationId, "{\"model\": \"haiku\"}")).isNull();
  }

  @Test
  void DELETE에_없는_조합이면_404_price_rate_not_found다() {
    UUID organizationId = insertOrganizationWithDimensionProperties("{model}");

    assertThat(delete(organizationId, "{\"model\":\"opus\"}"))
        .hasStatus(404)
        .bodyJson()
        .extractingPath("$.code")
        .asString()
        .isEqualTo(ErrorCode.PRICE_RATE_NOT_FOUND.getCode());
  }

  @Test
  void DELETE의_dimension_values가_JSON_객체가_아니면_400_validation_error다() {
    UUID organizationId = insertOrganizationWithDimensionProperties("{model}");

    assertThat(delete(organizationId, "opus"))
        .hasStatus(400)
        .bodyJson()
        .extractingPath("$.code")
        .asString()
        .isEqualTo(ErrorCode.VALIDATION_ERROR.getCode());
  }

  // --- 헬퍼 ---

  private String authorization(UUID organizationId) {
    return authorizationsByOrganizationId.computeIfAbsent(
        organizationId, id -> TestCredentials.issueApiKey(jdbcTemplate, id));
  }

  private MvcTestResult delete(UUID organizationId, String dimensionValues) {
    return mvc.delete()
        .uri("/v1/billable-metrics/" + BILLABLE_METRIC_CODE + "/price-rates")
        .queryParam("dimension_values", dimensionValues)
        .header(AUTHORIZATION, authorization(organizationId))
        .exchange();
  }

  private MvcTestResult post(UUID organizationId, String jsonBody) {
    return mvc.post()
        .uri("/v1/billable-metrics/" + BILLABLE_METRIC_CODE + "/price-rates")
        .header(AUTHORIZATION, authorization(organizationId))
        .contentType(MediaType.APPLICATION_JSON)
        .content(jsonBody)
        .exchange();
  }

  private PriceRateResponse create(
      UUID organizationId, Map<String, Object> dimensionValues, String unitPrice) {
    return priceRateService.create(
        organizationId,
        BILLABLE_METRIC_CODE,
        new CreatePriceRateRequest(dimensionValues, new BigDecimal(unitPrice)));
  }

  private UUID insertOrganizationWithDimensionProperties(String dimensionProperties) {
    UUID organizationId = insertOrganization();
    insertBillableMetric(organizationId);
    jdbcTemplate.update(
        "UPDATE billable_metric SET dimension_properties = ?::text[]"
            + " WHERE organization_id = ? AND code = ?",
        dimensionProperties,
        organizationId,
        BILLABLE_METRIC_CODE);
    return organizationId;
  }

  private UUID insertOrganization() {
    return jdbcTemplate.queryForObject(
        "INSERT INTO organization (name) VALUES ('테스트 도입사') RETURNING id", UUID.class);
  }

  private void insertBillableMetric(UUID organizationId) {
    jdbcTemplate.update(
        """
        INSERT INTO billable_metric
          (organization_id, code, name, event_type, aggregation, target_property)
        VALUES (?, ?, '입력 토큰', 'chat_completion', 'sum', 'input_tokens')
        """,
        organizationId,
        BILLABLE_METRIC_CODE);
  }

  private BigDecimal storedUnitPrice(UUID organizationId, String dimensionValuesJson) {
    return jdbcTemplate.queryForObject(
        """
        SELECT unit_price FROM price_rate
        WHERE organization_id = ? AND billable_metric_code = ? AND dimension_values = ?::jsonb
        """,
        BigDecimal.class,
        organizationId,
        BILLABLE_METRIC_CODE,
        dimensionValuesJson);
  }

  private String archivedFrom(UUID organizationId, String dimensionValuesJson) {
    return jdbcTemplate
        .queryForList(
            """
            SELECT archived_from FROM price_rate
            WHERE organization_id = ? AND billable_metric_code = ? AND dimension_values = ?::jsonb
              AND archived_at IS NOT NULL
            """,
            String.class,
            organizationId,
            BILLABLE_METRIC_CODE,
            dimensionValuesJson)
        .stream()
        .findFirst()
        .orElse(null);
  }

  private Integer priceRateCount(UUID organizationId) {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM price_rate WHERE organization_id = ?", Integer.class, organizationId);
  }
}
