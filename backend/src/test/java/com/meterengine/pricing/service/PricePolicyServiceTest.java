package com.meterengine.pricing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meterengine.metric.entity.BillableMetric;
import com.meterengine.metric.entity.BillableMetricId;
import com.meterengine.metric.repository.BillableMetricRepository;
import com.meterengine.pricing.dto.BillableMetricPricePolicyResponse;
import com.meterengine.pricing.dto.CreatePricePolicyRequest;
import com.meterengine.pricing.dto.ListPricePoliciesResponse;
import com.meterengine.pricing.dto.PricePolicyResponse;
import com.meterengine.pricing.entity.PricePolicy;
import com.meterengine.pricing.entity.PricePolicyId;
import com.meterengine.pricing.exception.InvalidPricePolicyException;
import com.meterengine.pricing.exception.MetricNotFoundException;
import com.meterengine.pricing.exception.PricePolicyAlreadyExistsException;
import com.meterengine.pricing.repository.PricePolicyRepository;
import com.meterengine.pricing.repository.PriceRateRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class PricePolicyServiceTest {

  private static final UUID ORG_ID = UUID.randomUUID();
  private static final String BILLABLE_METRIC_CODE = "token-usage";

  @Mock private PricePolicyRepository pricePolicyRepository;
  @Mock private BillableMetricRepository billableMetricRepository;
  @Mock private PriceRateRepository priceRateRepository;

  private PricePolicyService service;

  @BeforeEach
  void setUp() {
    service =
        new PricePolicyService(
            pricePolicyRepository, billableMetricRepository, priceRateRepository);
  }

  @Test
  void 미터가_없으면_MetricNotFound다() {
    when(billableMetricRepository.existsById(new BillableMetricId(ORG_ID, BILLABLE_METRIC_CODE)))
        .thenReturn(false);

    assertThatThrownBy(() -> create(List.of())).isInstanceOf(MetricNotFoundException.class);
    verify(pricePolicyRepository, never()).saveAndFlush(any());
  }

  @Test
  void 정책이_이미_있으면_AlreadyExists다() {
    billableMetricExists();
    when(pricePolicyRepository.existsById(new PricePolicyId(ORG_ID, BILLABLE_METRIC_CODE)))
        .thenReturn(true);

    assertThatThrownBy(() -> create(List.of()))
        .isInstanceOf(PricePolicyAlreadyExistsException.class);
    verify(pricePolicyRepository, never()).saveAndFlush(any());
  }

  @Test
  void 확인과_INSERT_사이의_경합도_AlreadyExists로_바뀐다() {
    billableMetricExists();
    when(pricePolicyRepository.existsById(new PricePolicyId(ORG_ID, BILLABLE_METRIC_CODE)))
        .thenReturn(false);
    when(pricePolicyRepository.saveAndFlush(any()))
        .thenThrow(new DataIntegrityViolationException("pk"));

    assertThatThrownBy(() -> create(List.of()))
        .isInstanceOf(PricePolicyAlreadyExistsException.class);
  }

  @Test
  void 선언의_중복이나_빈_키는_Invalid다() {
    billableMetricExists();

    assertThatThrownBy(() -> create(List.of("model", "model")))
        .isInstanceOf(InvalidPricePolicyException.class)
        .hasMessageContaining("duplicate");
    assertThatThrownBy(() -> create(List.of(" ")))
        .isInstanceOf(InvalidPricePolicyException.class)
        .hasMessageContaining("blank");
    verify(pricePolicyRepository, never()).saveAndFlush(any());
  }

  @Test
  void 정상_등록이면_정책이_저장되고_저장된_모양이_응답이_된다() {
    billableMetricExists();
    when(pricePolicyRepository.existsById(new PricePolicyId(ORG_ID, BILLABLE_METRIC_CODE)))
        .thenReturn(false);

    PricePolicyResponse response = create(List.of("model"));

    ArgumentCaptor<PricePolicy> saved = ArgumentCaptor.forClass(PricePolicy.class);
    verify(pricePolicyRepository).saveAndFlush(saved.capture());
    assertThat(saved.getValue().getOrganizationId()).isEqualTo(ORG_ID);
    assertThat(saved.getValue().getBillableMetricCode()).isEqualTo(BILLABLE_METRIC_CODE);
    assertThat(saved.getValue().getDimensionProperties()).containsExactly("model");
    assertThat(response.billableMetricCode()).isEqualTo(BILLABLE_METRIC_CODE);
    assertThat(response.dimensionProperties()).containsExactly("model");
  }

  @Test
  void 목록의_순서는_미터_조회가_정한다() {
    when(billableMetricRepository.findByOrganizationIdOrderByCodeAsc(ORG_ID))
        .thenReturn(List.of(billableMetric("input-tokens"), billableMetric("token-usage")));
    when(pricePolicyRepository.findByOrganizationId(ORG_ID))
        .thenReturn(
            List.of(pricePolicy("token-usage", List.of()), pricePolicy("input-tokens", List.of())));
    when(priceRateRepository.findBaseUnitPrices(ORG_ID)).thenReturn(Map.of());

    ListPricePoliciesResponse response = service.list(ORG_ID);

    assertThat(response.pricePolicies())
        .extracting(BillableMetricPricePolicyResponse::billableMetricCode)
        .containsExactly("input-tokens", "token-usage");
  }

  @Test
  void 정책과_단가가_모두_있으면_둘_다_실린다() {
    when(billableMetricRepository.findByOrganizationIdOrderByCodeAsc(ORG_ID))
        .thenReturn(List.of(billableMetric("input-tokens")));
    when(pricePolicyRepository.findByOrganizationId(ORG_ID))
        .thenReturn(List.of(pricePolicy("input-tokens", List.of("model"))));
    when(priceRateRepository.findBaseUnitPrices(ORG_ID))
        .thenReturn(Map.of("input-tokens", new BigDecimal("0.007")));

    BillableMetricPricePolicyResponse only = service.list(ORG_ID).pricePolicies().getFirst();

    assertThat(only.dimensionProperties()).containsExactly("model");
    assertThat(only.unitPrice()).isEqualByComparingTo("0.007");
  }

  @Test
  void 정책이_없는_미터는_단가가_있어도_싣지_않는다() {
    when(billableMetricRepository.findByOrganizationIdOrderByCodeAsc(ORG_ID))
        .thenReturn(List.of(billableMetric("input-tokens")));
    when(pricePolicyRepository.findByOrganizationId(ORG_ID)).thenReturn(List.of());
    when(priceRateRepository.findBaseUnitPrices(ORG_ID))
        .thenReturn(Map.of("input-tokens", new BigDecimal("0.007")));

    BillableMetricPricePolicyResponse only = service.list(ORG_ID).pricePolicies().getFirst();

    assertThat(only.dimensionProperties()).isNull();
    assertThat(only.unitPrice()).isNull();
  }

  @Test
  void 다른_미터의_단가가_섞이지_않는다() {
    when(billableMetricRepository.findByOrganizationIdOrderByCodeAsc(ORG_ID))
        .thenReturn(List.of(billableMetric("input-tokens")));
    when(pricePolicyRepository.findByOrganizationId(ORG_ID))
        .thenReturn(List.of(pricePolicy("input-tokens", List.of())));
    when(priceRateRepository.findBaseUnitPrices(ORG_ID))
        .thenReturn(Map.of("token-usage", new BigDecimal("99")));

    assertThat(service.list(ORG_ID).pricePolicies().getFirst().unitPrice()).isNull();
  }

  private static BillableMetric billableMetric(String code) {
    return new BillableMetric(ORG_ID, code, "토큰 사용량", "chat_completion", "SUM", "token");
  }

  private static PricePolicy pricePolicy(String code, List<String> dimensionProperties) {
    return new PricePolicy(ORG_ID, code, dimensionProperties);
  }

  private void billableMetricExists() {
    when(billableMetricRepository.existsById(new BillableMetricId(ORG_ID, BILLABLE_METRIC_CODE)))
        .thenReturn(true);
  }

  private PricePolicyResponse create(List<String> properties) {
    return service.create(ORG_ID, BILLABLE_METRIC_CODE, new CreatePricePolicyRequest(properties));
  }
}
