package com.meterengine.pricing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.meterengine.metric.entity.BillableMetric;
import com.meterengine.metric.repository.BillableMetricRepository;
import com.meterengine.pricing.dto.BillableMetricPriceResponse;
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

  @Mock private BillableMetricRepository billableMetricRepository;
  @Mock private PriceRateRepository priceRateRepository;

  private BillableMetricPriceService billableMetricPriceService;

  @BeforeEach
  void setUp() {
    billableMetricPriceService =
        new BillableMetricPriceService(billableMetricRepository, priceRateRepository);
  }

  @Test
  void 목록의_순서는_미터_조회가_정한다() {
    when(billableMetricRepository.findByOrganizationIdOrderByCodeAsc(ORGANIZATION_ID))
        .thenReturn(List.of(billableMetric("input-tokens"), billableMetric("token-usage")));
    when(priceRateRepository.findBaseUnitPriceByBillableMetricCode(ORGANIZATION_ID))
        .thenReturn(Map.of());

    assertThat(billableMetricPriceService.list(ORGANIZATION_ID).billableMetricPrices())
        .extracting(BillableMetricPriceResponse::billableMetricCode)
        .containsExactly("input-tokens", "token-usage");
  }

  @Test
  void 미터의_dimension_properties와_단가가_함께_실린다() {
    BillableMetric billableMetric = billableMetric("input-tokens", List.of("model"));
    when(billableMetricRepository.findByOrganizationIdOrderByCodeAsc(ORGANIZATION_ID))
        .thenReturn(List.of(billableMetric));
    when(priceRateRepository.findBaseUnitPriceByBillableMetricCode(ORGANIZATION_ID))
        .thenReturn(Map.of("input-tokens", new BigDecimal("0.007")));

    BillableMetricPriceResponse onlyBillableMetricPrice = onlyBillableMetricPrice();

    assertThat(onlyBillableMetricPrice.dimensionProperties()).containsExactly("model");
    assertThat(onlyBillableMetricPrice.unitPrice()).isEqualByComparingTo("0.007");
  }

  @Test
  void 단가가_없는_미터는_키는_실리고_unit_price는_null이다() {
    when(billableMetricRepository.findByOrganizationIdOrderByCodeAsc(ORGANIZATION_ID))
        .thenReturn(List.of(billableMetric("input-tokens")));
    when(priceRateRepository.findBaseUnitPriceByBillableMetricCode(ORGANIZATION_ID))
        .thenReturn(Map.of());

    BillableMetricPriceResponse onlyBillableMetricPrice = onlyBillableMetricPrice();

    assertThat(onlyBillableMetricPrice.dimensionProperties()).isEmpty();
    assertThat(onlyBillableMetricPrice.unitPrice()).isNull();
  }

  @Test
  void 다른_미터의_단가가_섞이지_않는다() {
    when(billableMetricRepository.findByOrganizationIdOrderByCodeAsc(ORGANIZATION_ID))
        .thenReturn(List.of(billableMetric("input-tokens")));
    when(priceRateRepository.findBaseUnitPriceByBillableMetricCode(ORGANIZATION_ID))
        .thenReturn(Map.of("token-usage", new BigDecimal("99")));

    assertThat(onlyBillableMetricPrice().unitPrice()).isNull();
  }

  private BillableMetricPriceResponse onlyBillableMetricPrice() {
    return billableMetricPriceService.list(ORGANIZATION_ID).billableMetricPrices().getFirst();
  }

  private static BillableMetric billableMetric(String code) {
    return billableMetric(code, List.of());
  }

  private static BillableMetric billableMetric(String code, List<String> dimensionProperties) {
    return new BillableMetric(
        ORGANIZATION_ID, code, "토큰 사용량", "chat_completion", "sum", "token", dimensionProperties);
  }
}
