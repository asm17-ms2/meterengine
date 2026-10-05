package com.meterengine.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meterengine.TestcontainersConfiguration;
import com.meterengine.global.error.BusinessException;
import com.meterengine.global.error.ConflictException;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.NotFoundException;
import com.meterengine.pricing.dto.CreatePriceRateRequest;
import com.meterengine.pricing.dto.PriceRateResponse;
import com.meterengine.pricing.service.PriceRateService;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class PriceRateIntegrationTest {

  private static final String BILLABLE_METRIC_CODE = "input-tokens";

  @Autowired private PriceRateService priceRateService;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void 조합_단가를_등록하면_jsonb_조합과_단가가_저장된다() {
    UUID organizationId = insertOrganizationWithPolicy("{model,region}");

    PriceRateResponse response =
        create(organizationId, Map.of("model", "opus", "region", "kr"), "0.007");

    assertThat(response.unitPrice()).isEqualByComparingTo("0.007");
    assertThat(storedUnitPrice(organizationId, "{\"model\": \"opus\", \"region\": \"kr\"}"))
        .isEqualByComparingTo("0.007");
  }

  @Test
  void 키_순서만_다른_같은_조합은_DB가_같은_행으로_보아_AlreadyExists다() {
    UUID organizationId = insertOrganizationWithPolicy("{model,region}");
    create(organizationId, Map.of("model", "opus", "region", "kr"), "0.007");
    Map<String, Object> reordered = new LinkedHashMap<>();
    reordered.put("region", "kr");
    reordered.put("model", "opus");

    assertThatThrownBy(() -> create(organizationId, reordered, "0.009"))
        .isInstanceOf(ConflictException.class)
        .extracting(exception -> ((BusinessException) exception).getErrorCode())
        .isEqualTo(ErrorCode.PRICE_RATE_ALREADY_EXISTS);
    assertThat(priceRateCount(organizationId)).isEqualTo(1);
  }

  @Test
  void 기본_단가는_빈_객체로_저장되고_청구가_읽는_기본_단가_조회에_잡힌다() {
    UUID organizationId = insertOrganizationWithPolicy("{model}");

    create(organizationId, Map.of(), "120");

    assertThat(storedUnitPrice(organizationId, "{}")).isEqualByComparingTo("120");
  }

  @Test
  void 정책이_없는_미터면_NotFound고_저장은_0건이다() {
    UUID organizationId = insertOrganization();
    insertBillableMetric(organizationId);

    assertThatThrownBy(() -> create(organizationId, Map.of(), "1"))
        .isInstanceOf(NotFoundException.class)
        .extracting(exception -> ((BusinessException) exception).getErrorCode())
        .isEqualTo(ErrorCode.PRICE_POLICY_NOT_FOUND);
    assertThat(priceRateCount(organizationId)).isZero();
  }

  private PriceRateResponse create(
      UUID organizationId, Map<String, Object> dimensionValues, String unitPrice) {
    return priceRateService.create(
        organizationId,
        BILLABLE_METRIC_CODE,
        new CreatePriceRateRequest(dimensionValues, new BigDecimal(unitPrice)));
  }

  private UUID insertOrganizationWithPolicy(String dimensionProperties) {
    UUID organizationId = insertOrganization();
    insertBillableMetric(organizationId);
    jdbcTemplate.update(
        "INSERT INTO price_policy (organization_id, billable_metric_code, dimension_properties)"
            + " VALUES (?, ?, ?::text[])",
        organizationId,
        BILLABLE_METRIC_CODE,
        dimensionProperties);
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

  private Integer priceRateCount(UUID organizationId) {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM price_rate WHERE organization_id = ?", Integer.class, organizationId);
  }
}
