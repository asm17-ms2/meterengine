package com.meterengine.pricing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meterengine.global.error.BusinessException;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.ErrorResponse.FieldError;
import com.meterengine.global.error.InvalidRequestException;
import com.meterengine.pricing.dto.CreatePriceRateRequest;
import com.meterengine.pricing.dto.PriceRateResponse;
import com.meterengine.pricing.entity.PricePolicy;
import com.meterengine.pricing.entity.PricePolicyId;
import com.meterengine.pricing.entity.PriceRate;
import com.meterengine.pricing.repository.PricePolicyRepository;
import com.meterengine.pricing.repository.PriceRateRepository;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class PriceRateServiceTest {

  private static final UUID ORGANIZATION_ID = UUID.randomUUID();
  private static final String BILLABLE_METRIC_CODE = "input-tokens";
  private static final BigDecimal UNIT_PRICE = new BigDecimal("0.007");

  @Mock private PriceRateRepository priceRateRepository;
  @Mock private PricePolicyRepository pricePolicyRepository;

  private PriceRateService priceRateService;

  @BeforeEach
  void setUp() {
    priceRateService =
        new PriceRateService(
            priceRateRepository, pricePolicyRepository, JsonMapper.builder().build());
  }

  @Test
  void 키_집합이_정책의_dimension_properties와_다르면_Invalid다() {
    pricePolicyDeclares(List.of("model", "region"));

    assertThatThrownBy(() -> create(Map.of("model", "opus")))
        .isInstanceOfSatisfying(
            InvalidRequestException.class,
            exception -> {
              assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_PRICE_RATE);
              assertThat(exception.getErrors())
                  .extracting(FieldError::field)
                  .containsExactly("dimension_values");
            });
    assertThatThrownBy(() -> create(Map.of("model", "opus", "region", "kr", "tier", "pro")))
        .isInstanceOf(InvalidRequestException.class);
    verify(priceRateRepository, never()).saveAndFlush(any());
  }

  @Test
  void 값이_객체나_배열이나_null이면_Invalid다() {
    pricePolicyDeclares(List.of("model"));
    Map<String, Object> nullValue = new LinkedHashMap<>();
    nullValue.put("model", null);

    assertThatThrownBy(() -> create(Map.of("model", Map.of("name", "opus"))))
        .isInstanceOf(InvalidRequestException.class)
        .extracting(exception -> ((BusinessException) exception).getErrorCode())
        .isEqualTo(ErrorCode.INVALID_PRICE_RATE);
    assertThatThrownBy(() -> create(Map.of("model", List.of("opus"))))
        .isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(() -> create(nullValue)).isInstanceOf(InvalidRequestException.class);
    verify(priceRateRepository, never()).saveAndFlush(any());
  }

  @Test
  void 정상_등록이면_조합과_단가가_저장되고_저장된_모양이_응답이_된다() {
    pricePolicyDeclares(List.of("model", "region"));
    Map<String, Object> dimensionValues = new LinkedHashMap<>();
    dimensionValues.put("model", "opus");
    dimensionValues.put("region", "kr");

    PriceRateResponse response = create(dimensionValues);

    ArgumentCaptor<PriceRate> saved = ArgumentCaptor.forClass(PriceRate.class);
    verify(priceRateRepository).saveAndFlush(saved.capture());
    assertThat(saved.getValue().getOrganizationId()).isEqualTo(ORGANIZATION_ID);
    assertThat(saved.getValue().getBillableMetricCode()).isEqualTo(BILLABLE_METRIC_CODE);
    assertThat(saved.getValue().getDimensionValues())
        .isEqualTo("{\"model\":\"opus\",\"region\":\"kr\"}");
    assertThat(saved.getValue().getUnitPrice()).isEqualByComparingTo(UNIT_PRICE);
    assertThat(saved.getValue().isNew()).isTrue();
    assertThat(response.billableMetricCode()).isEqualTo(BILLABLE_METRIC_CODE);
    assertThat(response.dimensionValues()).isEqualTo("{\"model\":\"opus\",\"region\":\"kr\"}");
    assertThat(response.unitPrice()).isEqualByComparingTo(UNIT_PRICE);
  }

  private void pricePolicyDeclares(List<String> dimensionProperties) {
    when(pricePolicyRepository.findById(new PricePolicyId(ORGANIZATION_ID, BILLABLE_METRIC_CODE)))
        .thenReturn(
            Optional.of(
                new PricePolicy(ORGANIZATION_ID, BILLABLE_METRIC_CODE, dimensionProperties)));
  }

  private PriceRateResponse create(Map<String, Object> dimensionValues) {
    return priceRateService.create(
        ORGANIZATION_ID,
        BILLABLE_METRIC_CODE,
        new CreatePriceRateRequest(dimensionValues, UNIT_PRICE));
  }
}
