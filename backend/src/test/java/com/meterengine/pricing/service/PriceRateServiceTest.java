package com.meterengine.pricing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meterengine.audit.service.AuditLogService;
import com.meterengine.customer.repository.CustomerRepository;
import com.meterengine.global.error.BusinessException;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.ErrorResponse.FieldError;
import com.meterengine.global.error.InvalidRequestException;
import com.meterengine.metric.entity.BillableMetric;
import com.meterengine.metric.entity.BillableMetricId;
import com.meterengine.metric.repository.BillableMetricRepository;
import com.meterengine.metric.service.BillableMetricUsageService;
import com.meterengine.organization.entity.Organization;
import com.meterengine.organization.repository.OrganizationRepository;
import com.meterengine.pricing.dto.CreatePriceRateRequest;
import com.meterengine.pricing.dto.PriceRateResponse;
import com.meterengine.pricing.entity.PriceRate;
import com.meterengine.pricing.repository.PriceRateRepository;
import java.math.BigDecimal;
import java.time.YearMonth;
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
  @Mock private BillableMetricRepository billableMetricRepository;
  @Mock private AuditLogService auditLogService;
  @Mock private CustomerRepository customerRepository;
  @Mock private OrganizationRepository organizationRepository;

  private PriceRateService priceRateService;

  @BeforeEach
  void setUp() {
    org.mockito.Mockito.lenient()
        .when(organizationRepository.findForUpdateById(ORGANIZATION_ID))
        .thenReturn(Optional.of(mock(Organization.class)));
    priceRateService =
        new PriceRateService(
            priceRateRepository,
            billableMetricRepository,
            customerRepository,
            organizationRepository,
            JsonMapper.builder().build(),
            auditLogService);
  }

  @Test
  void 키_집합이_미터의_dimension_properties와_다르면_Invalid다() {
    billableMetricDeclares(List.of("model", "region"));

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
    assertThatThrownBy(() -> create(Map.of())).isInstanceOf(InvalidRequestException.class);
    verify(priceRateRepository, never()).saveAndFlush(any());
  }

  @Test
  void 값이_객체나_배열이나_null이면_Invalid다() {
    billableMetricDeclares(List.of("model"));
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
  void 종료_월이_시작_월보다_뒤가_아니면_요금제와_같은_validation_error다() {
    billableMetricDeclares(List.of("model"));
    YearMonth month = YearMonth.now(BillableMetricUsageService.BILLING_ZONE);

    assertThatThrownBy(
            () ->
                priceRateService.create(
                    ORGANIZATION_ID,
                    BILLABLE_METRIC_CODE,
                    new CreatePriceRateRequest(
                        Map.of("model", "opus"), UNIT_PRICE, null, month, month, null, null)))
        .isInstanceOfSatisfying(
            InvalidRequestException.class,
            exception -> {
              assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
              assertThat(exception.getErrors())
                  .extracting(FieldError::field)
                  .containsExactly("effective_until");
            });
    verify(priceRateRepository, never()).saveAndFlush(any());
  }

  @Test
  void 정상_등록이면_조합과_단가가_저장되고_저장된_모양이_응답이_된다() {
    billableMetricDeclares(List.of("model", "region"));
    Map<String, Object> dimensionValues = new LinkedHashMap<>();
    dimensionValues.put("model", "opus");
    dimensionValues.put("region", "kr");

    allowWrite();
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
    verify(auditLogService)
        .record(
            eq(ORGANIZATION_ID), eq("price_rate_created"), eq("price_rate"), anyString(), any());
  }

  @Test
  void 같은_조합이_있으면_그_행의_단가만_덮어쓴다() {
    billableMetricDeclares(List.of("model"));
    allowWrite();
    PriceRate existing =
        new PriceRate(
            UUID.randomUUID(),
            ORGANIZATION_ID,
            BILLABLE_METRIC_CODE,
            "{\"model\":\"opus\"}",
            new BigDecimal("1"),
            null,
            YearMonth.now(BillableMetricUsageService.BILLING_ZONE).toString(),
            null,
            "per_unit",
            null);
    when(priceRateRepository.findByOrganizationIdOrderByBillableMetricCodeAscDimensionValuesAsc(
            ORGANIZATION_ID))
        .thenReturn(List.of(existing));

    PriceRateResponse response = create(Map.of("model", "opus"));

    verify(priceRateRepository).saveAndFlush(existing);
    assertThat(existing.getUnitPrice()).isEqualByComparingTo(UNIT_PRICE);
    assertThat(response.unitPrice()).isEqualByComparingTo(UNIT_PRICE);
    verify(auditLogService)
        .record(
            eq(ORGANIZATION_ID), eq("price_rate_created"), eq("price_rate"), anyString(), any());
  }

  private void allowWrite() {
    when(organizationRepository.findForUpdateById(ORGANIZATION_ID))
        .thenReturn(Optional.of(new Organization(ORGANIZATION_ID, "도입사", "active")));
  }

  private void billableMetricDeclares(List<String> dimensionProperties) {
    BillableMetric billableMetric = mock(BillableMetric.class);
    when(billableMetric.getDimensionProperties()).thenReturn(dimensionProperties);
    when(billableMetricRepository.findById(
            new BillableMetricId(ORGANIZATION_ID, BILLABLE_METRIC_CODE)))
        .thenReturn(Optional.of(billableMetric));
  }

  private PriceRateResponse create(Map<String, Object> dimensionValues) {
    return priceRateService.create(
        ORGANIZATION_ID,
        BILLABLE_METRIC_CODE,
        new CreatePriceRateRequest(dimensionValues, UNIT_PRICE));
  }
}
