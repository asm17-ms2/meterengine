package com.meterengine.pricing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.meterengine.metric.dto.BillableMetricUsage;
import com.meterengine.metric.dto.CustomerUsage;
import com.meterengine.metric.dto.DimensionUsage;
import com.meterengine.metric.entity.BillableMetric;
import com.meterengine.pricing.dto.CustomerCharge;
import com.meterengine.pricing.dto.DimensionCharge;
import com.meterengine.pricing.dto.UnpricedUsage;
import com.meterengine.pricing.entity.PriceRate;
import com.meterengine.pricing.repository.PriceRateRepository;
import java.math.BigDecimal;
import java.time.YearMonth;
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
class ChargeServiceTest {

  private static final UUID ORGANIZATION_ID = UUID.randomUUID();
  private static final YearMonth AUGUST = YearMonth.of(2026, 8);
  private static final UUID ACME_ID = UUID.randomUUID();
  private static final UUID BETA_ID = UUID.randomUUID();

  @Mock private PriceRateRepository priceRateRepository;

  private ChargeService chargeService;

  @BeforeEach
  void setUp() {
    chargeService = new ChargeService(priceRateRepository, JsonMapper.builder().build());
  }

  @Test
  void 금액은_사용량_곱하기_단가다() {
    stubPriceRates(priceRate("token-usage", "{}", "0.5"));

    CustomerCharge customerCharge =
        calculate(usage(billableMetric("token-usage", "token"), "3280")).get(ACME_ID);

    assertThat(customerCharge.customerId()).isEqualTo(ACME_ID);
    assertThat(customerCharge.amount()).isEqualTo(1640);
    DimensionCharge dimensionCharge = customerCharge.dimensionCharges().getFirst();
    assertThat(dimensionCharge.billableMetricCode()).isEqualTo("token-usage");
    assertThat(dimensionCharge.targetProperty()).isEqualTo("token");
    assertThat(dimensionCharge.dimensionValues()).isEmpty();
    assertThat(dimensionCharge.quantity()).isEqualByComparingTo("3280");
    assertThat(dimensionCharge.unitPrice()).isEqualByComparingTo("0.5");
    assertThat(dimensionCharge.amount()).isEqualTo(1640);
  }

  @Test
  void 라인_금액은_10원_미만을_버린다() {
    stubPriceRates(priceRate("token-usage", "{}", "0.5"));

    CustomerCharge customerCharge =
        calculate(usage(billableMetric("token-usage", "token"), "3299")).get(ACME_ID);

    assertThat(customerCharge.dimensionCharges().getFirst().amount()).isEqualTo(1640);
    assertThat(customerCharge.amount()).isEqualTo(1640);
  }

  @Test
  void 금액이_10원이_안_되는_라인은_0원이다() {
    stubPriceRates(priceRate("token-usage", "{}", "0.5"));

    CustomerCharge customerCharge =
        calculate(usage(billableMetric("token-usage", "token"), "19")).get(ACME_ID);

    assertThat(customerCharge.dimensionCharges().getFirst().amount()).isZero();
    assertThat(customerCharge.amount()).isZero();
  }

  @Test
  void 절사는_라인별로_하고_합산한다() {
    stubPriceRates(
        priceRate("token-usage", "{}", "0.5"), priceRate("api-request-count", "{}", "0.5"));

    CustomerCharge customerCharge =
        calculate(
                usage(billableMetric("token-usage", "token"), "3299"),
                usage(billableMetric("api-request-count", "count"), "39"))
            .get(ACME_ID);

    assertThat(customerCharge.dimensionCharges())
        .extracting(DimensionCharge::amount)
        .containsExactly(1640L, 10L);
    assertThat(customerCharge.amount()).isEqualTo(1650);
  }

  @Test
  void 같은_조합의_단가가_있는_조합만_라인이_된다() {
    stubPriceRates(priceRate("token-usage", "{\"model\": \"a\"}", "0.5"));

    CustomerCharge customerCharge =
        calculate(
                new BillableMetricUsage(
                    billableMetric("token-usage", "token"),
                    List.of(
                        customerUsage(
                            ACME_ID,
                            new DimensionUsage(Map.of("model", "a"), new BigDecimal("500")),
                            new DimensionUsage(Map.of("model", "b"), new BigDecimal("700"))))))
            .get(ACME_ID);

    assertThat(customerCharge.dimensionCharges())
        .singleElement()
        .satisfies(
            dimensionCharge -> {
              assertThat(dimensionCharge.dimensionValues()).isEqualTo(Map.of("model", "a"));
              assertThat(dimensionCharge.quantity()).isEqualByComparingTo("500");
              assertThat(dimensionCharge.amount()).isEqualTo(250);
            });
    assertThat(customerCharge.amount()).isEqualTo(250);
    assertThat(customerCharge.unpricedUsages())
        .singleElement()
        .satisfies(
            unpricedUsage -> {
              assertThat(unpricedUsage.billableMetricCode()).isEqualTo("token-usage");
              assertThat(unpricedUsage.targetProperty()).isEqualTo("token");
              assertThat(unpricedUsage.dimensionValues()).isEqualTo(Map.of("model", "b"));
              assertThat(unpricedUsage.quantity()).isEqualByComparingTo("700");
            });
  }

  @Test
  void 수량이_0인_단가_없는_조합은_어디에도_싣지_않는다() {
    stubPriceRates();

    CustomerCharge customerCharge =
        calculate(
                new BillableMetricUsage(
                    billableMetric("token-usage", "token"),
                    List.of(
                        customerUsage(
                            ACME_ID, new DimensionUsage(Map.of("model", "b"), BigDecimal.ZERO)))))
            .get(ACME_ID);

    assertThat(customerCharge.dimensionCharges()).isEmpty();
    assertThat(customerCharge.unpricedUsages()).isEmpty();
    assertThat(customerCharge.negativeUsages()).isEmpty();
  }

  @Test
  void 단가가_없는_미터는_라인에서_빠진다() {
    stubPriceRates(priceRate("token-usage", "{}", "0.5"));

    CustomerCharge customerCharge =
        calculate(
                usage(billableMetric("token-usage", "token"), "3280"),
                usage(billableMetric("api-calls", "count"), "3"))
            .get(ACME_ID);

    assertThat(customerCharge.dimensionCharges())
        .extracting(DimensionCharge::billableMetricCode)
        .containsExactly("token-usage");
    assertThat(customerCharge.amount()).isEqualTo(1640);
    assertThat(customerCharge.unpricedUsages())
        .extracting(UnpricedUsage::billableMetricCode)
        .containsExactly("api-calls");
  }

  @Test
  void 수량_합이_음수인_조합은_라인에_없고_금액_0이며_negativeUsages에_남는다() {
    stubPriceRates();

    CustomerCharge customerCharge =
        calculate(
                new BillableMetricUsage(
                    billableMetric("token-usage", "token"),
                    List.of(
                        customerUsage(
                            ACME_ID,
                            new DimensionUsage(Map.of("model", "a"), new BigDecimal("-30"))))))
            .get(ACME_ID);

    assertThat(customerCharge.amount()).isZero();
    assertThat(customerCharge.dimensionCharges()).isEmpty();
    assertThat(customerCharge.unpricedUsages()).isEmpty();
    assertThat(customerCharge.negativeUsages())
        .singleElement()
        .satisfies(
            negativeUsage -> {
              assertThat(negativeUsage.billableMetricCode()).isEqualTo("token-usage");
              assertThat(negativeUsage.targetProperty()).isEqualTo("token");
              assertThat(negativeUsage.dimensionValues()).isEqualTo(Map.of("model", "a"));
              assertThat(negativeUsage.quantity()).isEqualByComparingTo("-30");
            });
  }

  @Test
  void 단가가_있어도_수량_합이_음수인_조합은_라인이_되지_않고_negativeUsages에_남는다() {
    stubPriceRates(priceRate("token-usage", "{}", "0.5"));

    CustomerCharge customerCharge =
        calculate(usage(billableMetric("token-usage", "token"), "-3280")).get(ACME_ID);

    assertThat(customerCharge.amount()).isZero();
    assertThat(customerCharge.dimensionCharges()).isEmpty();
    assertThat(customerCharge.negativeUsages())
        .singleElement()
        .satisfies(
            negativeUsage -> assertThat(negativeUsage.quantity()).isEqualByComparingTo("-3280"));
  }

  @Test
  void 음수_조합이_있어도_다른_양수_조합은_그대로_라인이_된다() {
    stubPriceRates(
        priceRate("token-usage", "{\"model\": \"a\"}", "0.5"),
        priceRate("token-usage", "{\"model\": \"b\"}", "0.5"));

    CustomerCharge customerCharge =
        calculate(
                new BillableMetricUsage(
                    billableMetric("token-usage", "token"),
                    List.of(
                        customerUsage(
                            ACME_ID,
                            new DimensionUsage(Map.of("model", "a"), new BigDecimal("500")),
                            new DimensionUsage(Map.of("model", "b"), new BigDecimal("-700"))))))
            .get(ACME_ID);

    assertThat(customerCharge.dimensionCharges())
        .singleElement()
        .satisfies(
            dimensionCharge -> {
              assertThat(dimensionCharge.dimensionValues()).isEqualTo(Map.of("model", "a"));
              assertThat(dimensionCharge.amount()).isEqualTo(250);
            });
    assertThat(customerCharge.amount()).isEqualTo(250);
    assertThat(customerCharge.negativeUsages())
        .singleElement()
        .satisfies(
            negativeUsage ->
                assertThat(negativeUsage.dimensionValues()).isEqualTo(Map.of("model", "b")));
  }

  @Test
  void 사용량에_나온_고객을_순서대로_모두_담는다() {
    stubPriceRates(priceRate("token-usage", "{}", "0.5"));

    Map<UUID, CustomerCharge> customerChargeByCustomerId =
        calculate(
            new BillableMetricUsage(
                billableMetric("token-usage", "token"),
                List.of(
                    customerUsage(ACME_ID, new DimensionUsage(Map.of(), new BigDecimal("500"))),
                    new CustomerUsage(BETA_ID, "베타", BigDecimal.ZERO, List.of()))));

    assertThat(customerChargeByCustomerId).containsOnlyKeys(ACME_ID, BETA_ID);
    assertThat(customerChargeByCustomerId.keySet()).containsExactly(ACME_ID, BETA_ID);
    CustomerCharge betaCustomerCharge = customerChargeByCustomerId.get(BETA_ID);
    assertThat(betaCustomerCharge.amount()).isZero();
    assertThat(betaCustomerCharge.dimensionCharges()).isEmpty();
  }

  private Map<UUID, CustomerCharge> calculate(BillableMetricUsage... billableMetricUsages) {
    return chargeService.calculate(ORGANIZATION_ID, AUGUST, List.of(billableMetricUsages));
  }

  private static BillableMetric billableMetric(String code, String targetProperty) {
    return new BillableMetric(
        ORGANIZATION_ID, code, code + " 미터", "chat_completion", "sum", targetProperty, List.of());
  }

  private static BillableMetricUsage usage(BillableMetric billableMetric, String quantity) {
    return new BillableMetricUsage(
        billableMetric,
        List.of(customerUsage(ACME_ID, new DimensionUsage(Map.of(), new BigDecimal(quantity)))));
  }

  private static CustomerUsage customerUsage(UUID customerId, DimensionUsage... dimensionUsages) {
    BigDecimal quantity =
        List.of(dimensionUsages).stream()
            .map(DimensionUsage::quantity)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    return new CustomerUsage(customerId, "아크메", quantity, List.of(dimensionUsages));
  }

  private static PriceRate priceRate(
      String billableMetricCode, String dimensionValues, String unitPrice) {
    return new PriceRate(
        ORGANIZATION_ID, billableMetricCode, dimensionValues, new BigDecimal(unitPrice));
  }

  private void stubPriceRates(PriceRate... priceRates) {
    when(priceRateRepository.findByOrganizationIdOrderByBillableMetricCodeAscDimensionValuesAsc(
            ORGANIZATION_ID))
        .thenReturn(List.of(priceRates));
  }
}
