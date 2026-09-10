package com.meterengine.pricing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.meterengine.metric.entity.BillableMetric;
import com.meterengine.metric.repository.BillableMetricRepository;
import com.meterengine.pricing.dto.BillableMetricPriceResponse;
import com.meterengine.pricing.entity.PricePolicy;
import com.meterengine.pricing.repository.PricePolicyRepository;
import com.meterengine.pricing.repository.PriceRateRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BillableMetricPriceServiceTest {

  private static final UUID ORGANIZATION_ID = UUID.randomUUID();

  @Mock private PricePolicyRepository pricePolicyRepository;
  @Mock private BillableMetricRepository billableMetricRepository;
  @Mock private PriceRateRepository priceRateRepository;

  private BillableMetricPriceService billableMetricPriceService;

  @BeforeEach
  void setUp() {
    billableMetricPriceService =
        new BillableMetricPriceService(
            pricePolicyRepository, billableMetricRepository, priceRateRepository);
  }

  @Test
  void 목록의_순서는_미터_조회가_정한다() {
    when(billableMetricRepository.findByOrganizationIdOrderByCodeAsc(ORGANIZATION_ID))
        .thenReturn(List.of(billableMetric("input-tokens"), billableMetric("token-usage")));
    when(pricePolicyRepository.findByOrganizationId(ORGANIZATION_ID))
        .thenReturn(
            List.of(pricePolicy("token-usage", List.of()), pricePolicy("input-tokens", List.of())));
    when(priceRateRepository.findBaseUnitPrices(ORGANIZATION_ID)).thenReturn(Map.of());

    assertThat(billableMetricPriceService.list(ORGANIZATION_ID).billableMetricPrices())
        .extracting(BillableMetricPriceResponse::billableMetricCode)
        .containsExactly("input-tokens", "token-usage");
  }

  @Test
  void 정책과_단가가_모두_있으면_둘_다_실린다() {
    when(billableMetricRepository.findByOrganizationIdOrderByCodeAsc(ORGANIZATION_ID))
        .thenReturn(List.of(billableMetric("input-tokens")));
    when(pricePolicyRepository.findByOrganizationId(ORGANIZATION_ID))
        .thenReturn(List.of(pricePolicy("input-tokens", List.of("model"))));
    when(priceRateRepository.findBaseUnitPrices(ORGANIZATION_ID))
        .thenReturn(Map.of("input-tokens", new BigDecimal("0.007")));

    BillableMetricPriceResponse only = onlyBillableMetricPrice();

    assertThat(only.dimensionProperties()).containsExactly("model");
    assertThat(only.unitPrice()).isEqualByComparingTo("0.007");
  }

  @Test
  void 정책이_없는_미터는_단가가_있어도_싣지_않는다() {
    when(billableMetricRepository.findByOrganizationIdOrderByCodeAsc(ORGANIZATION_ID))
        .thenReturn(List.of(billableMetric("input-tokens")));
    when(pricePolicyRepository.findByOrganizationId(ORGANIZATION_ID)).thenReturn(List.of());
    when(priceRateRepository.findBaseUnitPrices(ORGANIZATION_ID))
        .thenReturn(Map.of("input-tokens", new BigDecimal("0.007")));

    BillableMetricPriceResponse only = onlyBillableMetricPrice();

    assertThat(only.dimensionProperties()).isNull();
    assertThat(only.unitPrice()).isNull();
  }

  @Test
  void 다른_미터의_단가가_섞이지_않는다() {
    when(billableMetricRepository.findByOrganizationIdOrderByCodeAsc(ORGANIZATION_ID))
        .thenReturn(List.of(billableMetric("input-tokens")));
    when(pricePolicyRepository.findByOrganizationId(ORGANIZATION_ID))
        .thenReturn(List.of(pricePolicy("input-tokens", List.of())));
    when(priceRateRepository.findBaseUnitPrices(ORGANIZATION_ID))
        .thenReturn(Map.of("token-usage", new BigDecimal("99")));

    assertThat(onlyBillableMetricPrice().unitPrice()).isNull();
  }

  private BillableMetricPriceResponse onlyBillableMetricPrice() {
    return billableMetricPriceService.list(ORGANIZATION_ID).billableMetricPrices().getFirst();
  }

  private static BillableMetric billableMetric(String code) {
    return new BillableMetric(ORGANIZATION_ID, code, "토큰 사용량", "chat_completion", "SUM", "token");
  }

  private static PricePolicy pricePolicy(String code, List<String> dimensionProperties) {
    return new PricePolicy(ORGANIZATION_ID, code, dimensionProperties);
  }
}
