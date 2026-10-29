package com.meterengine.pricing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.meterengine.customer.repository.CustomerRepository;
import com.meterengine.metric.entity.BillableMetric;
import com.meterengine.metric.repository.BillableMetricRepository;
import com.meterengine.pricing.dto.BillableMetricPricePriceRate;
import com.meterengine.pricing.dto.BillableMetricPriceResponse;
import com.meterengine.pricing.entity.PriceRate;
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
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class BillableMetricPriceServiceTest {

  private static final UUID ORGANIZATION_ID = UUID.randomUUID();

  @Mock private BillableMetricRepository billableMetricRepository;
  @Mock private PriceRateRepository priceRateRepository;
  @Mock private CustomerRepository customerRepository;

  private BillableMetricPriceService billableMetricPriceService;

  @BeforeEach
  void setUp() {
    billableMetricPriceService =
        new BillableMetricPriceService(
            billableMetricRepository,
            priceRateRepository,
            customerRepository,
            JsonMapper.builder().build());
  }

  @Test
  void 목록의_순서는_미터_조회가_정한다() {
    when(billableMetricRepository.findByOrganizationIdOrderByCodeAsc(ORGANIZATION_ID))
        .thenReturn(List.of(billableMetric("input-tokens"), billableMetric("token-usage")));
    priceRatesAre();

    assertThat(billableMetricPriceService.list(ORGANIZATION_ID).billableMetricPrices())
        .extracting(BillableMetricPriceResponse::billableMetricCode)
        .containsExactly("input-tokens", "token-usage");
  }

  @Test
  void 미터의_dimension_properties와_단가가_함께_실린다() {
    BillableMetric billableMetric = billableMetric("input-tokens", List.of("model"));
    when(billableMetricRepository.findByOrganizationIdOrderByCodeAsc(ORGANIZATION_ID))
        .thenReturn(List.of(billableMetric));
    priceRatesAre(
        priceRate("input-tokens", "{}", "0.007"),
        priceRate("input-tokens", "{\"model\": \"opus\"}", "0.009"));

    BillableMetricPriceResponse onlyBillableMetricPrice = onlyBillableMetricPrice();

    assertThat(onlyBillableMetricPrice.dimensionProperties()).containsExactly("model");
    assertThat(onlyBillableMetricPrice.priceRates())
        .extracting(BillableMetricPricePriceRate::dimensionValues)
        .containsExactly(Map.of(), Map.of("model", "opus"));
    assertThat(onlyBillableMetricPrice.priceRates())
        .extracting(BillableMetricPricePriceRate::unitPrice)
        .usingElementComparator(BigDecimal::compareTo)
        .containsExactly(new BigDecimal("0.007"), new BigDecimal("0.009"));
  }

  @Test
  void 단가가_없는_미터는_키는_실리고_price_rates는_빈_배열이다() {
    when(billableMetricRepository.findByOrganizationIdOrderByCodeAsc(ORGANIZATION_ID))
        .thenReturn(List.of(billableMetric("input-tokens")));
    priceRatesAre();

    BillableMetricPriceResponse onlyBillableMetricPrice = onlyBillableMetricPrice();

    assertThat(onlyBillableMetricPrice.dimensionProperties()).isEmpty();
    assertThat(onlyBillableMetricPrice.priceRates()).isEmpty();
  }

  @Test
  void 다른_미터의_단가가_섞이지_않는다() {
    when(billableMetricRepository.findByOrganizationIdOrderByCodeAsc(ORGANIZATION_ID))
        .thenReturn(List.of(billableMetric("input-tokens")));
    priceRatesAre(priceRate("token-usage", "{}", "99"));

    assertThat(onlyBillableMetricPrice().priceRates()).isEmpty();
  }

  private void priceRatesAre(PriceRate... priceRates) {
    when(priceRateRepository.findByOrganizationIdOrderByBillableMetricCodeAscDimensionValuesAsc(
            ORGANIZATION_ID))
        .thenReturn(List.of(priceRates));
  }

  private static PriceRate priceRate(String code, String dimensionValues, String unitPrice) {
    return new PriceRate(ORGANIZATION_ID, code, dimensionValues, new BigDecimal(unitPrice));
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
