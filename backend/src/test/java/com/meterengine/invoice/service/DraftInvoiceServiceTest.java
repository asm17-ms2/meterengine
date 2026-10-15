package com.meterengine.invoice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.meterengine.customer.entity.Customer;
import com.meterengine.customer.repository.CustomerRepository;
import com.meterengine.invoice.dto.DraftInvoiceResponse;
import com.meterengine.invoice.dto.DraftInvoiceResponse.DraftInvoiceCustomer;
import com.meterengine.invoice.dto.DraftInvoiceResponse.DraftInvoiceLine;
import com.meterengine.invoice.dto.DraftInvoiceResponse.DraftInvoiceUnpricedUsage;
import com.meterengine.invoice.repository.InvoiceRepository;
import com.meterengine.metric.dto.BillableMetricUsage;
import com.meterengine.metric.dto.CustomerUsage;
import com.meterengine.metric.dto.DimensionUsage;
import com.meterengine.metric.entity.BillableMetric;
import com.meterengine.metric.service.BillableMetricUsageService;
import com.meterengine.pricing.entity.PriceRate;
import com.meterengine.pricing.repository.PriceRateRepository;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.ZoneOffset;
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
class DraftInvoiceServiceTest {

  private static final UUID ORGANIZATION_ID = UUID.randomUUID();
  private static final YearMonth AUGUST = YearMonth.of(2026, 8);

  @Mock private BillableMetricUsageService billableMetricUsageService;
  @Mock private CustomerRepository customerRepository;
  @Mock private PriceRateRepository priceRateRepository;
  @Mock private InvoiceRepository invoiceRepository;

  private DraftInvoiceService draftInvoiceService;

  @BeforeEach
  void setUp() {
    draftInvoiceService =
        new DraftInvoiceService(
            billableMetricUsageService,
            customerRepository,
            priceRateRepository,
            invoiceRepository,
            JsonMapper.builder().build());
  }

  @Test
  void 금액은_사용량_곱하기_단가다() {
    Customer acme = customer("아크메");
    when(customerRepository.findByOrganizationIdOrderByNameAscIdAsc(ORGANIZATION_ID))
        .thenReturn(List.of(acme));
    when(billableMetricUsageService.aggregate(ORGANIZATION_ID, AUGUST))
        .thenReturn(List.of(usage(billableMetric("token-usage", "token"), acme, "3280")));
    stubPriceRates(priceRate("token-usage", "{}", "0.5"));

    DraftInvoiceResponse response = draftInvoiceService.preview(ORGANIZATION_ID, AUGUST);

    assertThat(response.month()).isEqualTo("2026-08");
    assertThat(response.calculatedAt()).isNotNull();
    assertThat(response.calculatedAt().getOffset()).isEqualTo(ZoneOffset.ofHours(9));
    assertThat(response.totalAmount()).isEqualTo(1640);
    DraftInvoiceCustomer draftInvoiceCustomer = response.customers().getFirst();
    assertThat(draftInvoiceCustomer.customerId()).isEqualTo(acme.getId());
    assertThat(draftInvoiceCustomer.customerName()).isEqualTo("아크메");
    assertThat(draftInvoiceCustomer.amount()).isEqualTo(1640);
    DraftInvoiceLine draftInvoiceLine = draftInvoiceCustomer.lines().getFirst();
    assertThat(draftInvoiceLine.billableMetricCode()).isEqualTo("token-usage");
    assertThat(draftInvoiceLine.targetProperty()).isEqualTo("token");
    assertThat(draftInvoiceLine.quantity()).isEqualByComparingTo("3280");
    assertThat(draftInvoiceLine.unitPrice()).isEqualByComparingTo("0.5");
    assertThat(draftInvoiceLine.amount()).isEqualTo(1640);
  }

  @Test
  void 라인_금액은_10원_미만을_버린다() {
    Customer acme = customer("아크메");
    when(customerRepository.findByOrganizationIdOrderByNameAscIdAsc(ORGANIZATION_ID))
        .thenReturn(List.of(acme));
    when(billableMetricUsageService.aggregate(ORGANIZATION_ID, AUGUST))
        .thenReturn(List.of(usage(billableMetric("token-usage", "token"), acme, "3299")));
    stubPriceRates(priceRate("token-usage", "{}", "0.5"));

    DraftInvoiceResponse response = draftInvoiceService.preview(ORGANIZATION_ID, AUGUST);

    assertThat(response.customers().getFirst().amount()).isEqualTo(1640);
  }

  @Test
  void 금액이_10원이_안_되는_라인은_0원이다() {
    Customer acme = customer("아크메");
    when(customerRepository.findByOrganizationIdOrderByNameAscIdAsc(ORGANIZATION_ID))
        .thenReturn(List.of(acme));
    when(billableMetricUsageService.aggregate(ORGANIZATION_ID, AUGUST))
        .thenReturn(List.of(usage(billableMetric("token-usage", "token"), acme, "19")));
    stubPriceRates(priceRate("token-usage", "{}", "0.5"));

    DraftInvoiceResponse response = draftInvoiceService.preview(ORGANIZATION_ID, AUGUST);

    assertThat(response.customers().getFirst().lines().getFirst().amount()).isZero();
    assertThat(response.totalAmount()).isZero();
  }

  @Test
  void 절사는_라인별로_하고_합산한다() {
    Customer acme = customer("아크메");
    when(customerRepository.findByOrganizationIdOrderByNameAscIdAsc(ORGANIZATION_ID))
        .thenReturn(List.of(acme));
    when(billableMetricUsageService.aggregate(ORGANIZATION_ID, AUGUST))
        .thenReturn(
            List.of(
                usage(billableMetric("token-usage", "token"), acme, "3299"),
                usage(billableMetric("api-request-count", "count"), acme, "39")));
    stubPriceRates(
        priceRate("token-usage", "{}", "0.5"), priceRate("api-request-count", "{}", "0.5"));

    DraftInvoiceResponse response = draftInvoiceService.preview(ORGANIZATION_ID, AUGUST);

    DraftInvoiceCustomer draftInvoiceCustomer = response.customers().getFirst();
    assertThat(draftInvoiceCustomer.lines())
        .extracting(DraftInvoiceLine::amount)
        .containsExactly(1640L, 10L);
    assertThat(draftInvoiceCustomer.amount()).isEqualTo(1650);
    assertThat(response.totalAmount()).isEqualTo(1650);
  }

  @Test
  void 이벤트가_없는_고객은_라인_없이_금액_0이다() {
    Customer acme = customer("아크메");
    Customer beta = customer("베타");
    when(customerRepository.findByOrganizationIdOrderByNameAscIdAsc(ORGANIZATION_ID))
        .thenReturn(List.of(acme, beta));
    when(billableMetricUsageService.aggregate(ORGANIZATION_ID, AUGUST))
        .thenReturn(
            List.of(
                new BillableMetricUsage(
                    billableMetric("token-usage", "token"),
                    List.of(
                        customerUsage(acme, new DimensionUsage(Map.of(), new BigDecimal("500"))),
                        new CustomerUsage(beta.getId(), "베타", BigDecimal.ZERO, List.of())))));
    stubPriceRates(priceRate("token-usage", "{}", "0.5"));

    List<DraftInvoiceCustomer> draftInvoiceCustomers =
        draftInvoiceService.preview(ORGANIZATION_ID, AUGUST).customers();

    DraftInvoiceCustomer betaDraftInvoiceCustomer = draftInvoiceCustomers.get(1);
    assertThat(betaDraftInvoiceCustomer.amount()).isZero();
    assertThat(betaDraftInvoiceCustomer.lines()).isEmpty();
  }

  @Test
  void 같은_조합의_단가가_있는_조합만_라인이_된다() {
    Customer acme = customer("아크메");
    when(customerRepository.findByOrganizationIdOrderByNameAscIdAsc(ORGANIZATION_ID))
        .thenReturn(List.of(acme));
    when(billableMetricUsageService.aggregate(ORGANIZATION_ID, AUGUST))
        .thenReturn(
            List.of(
                new BillableMetricUsage(
                    billableMetric("token-usage", "token"),
                    List.of(
                        customerUsage(
                            acme,
                            new DimensionUsage(Map.of("model", "a"), new BigDecimal("500")),
                            new DimensionUsage(Map.of("model", "b"), new BigDecimal("700")))))));
    stubPriceRates(priceRate("token-usage", "{\"model\": \"a\"}", "0.5"));

    DraftInvoiceResponse response = draftInvoiceService.preview(ORGANIZATION_ID, AUGUST);

    DraftInvoiceLine draftInvoiceLine = response.customers().getFirst().lines().getFirst();
    assertThat(response.customers().getFirst().lines()).hasSize(1);
    assertThat(draftInvoiceLine.dimensionValues()).isEqualTo(Map.of("model", "a"));
    assertThat(draftInvoiceLine.quantity()).isEqualByComparingTo("500");
    assertThat(draftInvoiceLine.amount()).isEqualTo(250);
    assertThat(response.totalAmount()).isEqualTo(250);
    assertThat(response.customers().getFirst().unpricedUsages())
        .singleElement()
        .satisfies(
            draftInvoiceUnpricedUsage -> {
              assertThat(draftInvoiceUnpricedUsage.billableMetricCode()).isEqualTo("token-usage");
              assertThat(draftInvoiceUnpricedUsage.targetProperty()).isEqualTo("token");
              assertThat(draftInvoiceUnpricedUsage.dimensionValues())
                  .isEqualTo(Map.of("model", "b"));
              assertThat(draftInvoiceUnpricedUsage.quantity()).isEqualByComparingTo("700");
            });
  }

  @Test
  void 수량이_0인_단가_없는_조합은_unpriced_usages에_싣지_않는다() {
    Customer acme = customer("아크메");
    when(customerRepository.findByOrganizationIdOrderByNameAscIdAsc(ORGANIZATION_ID))
        .thenReturn(List.of(acme));
    when(billableMetricUsageService.aggregate(ORGANIZATION_ID, AUGUST))
        .thenReturn(
            List.of(
                new BillableMetricUsage(
                    billableMetric("token-usage", "token"),
                    List.of(
                        customerUsage(
                            acme, new DimensionUsage(Map.of("model", "b"), BigDecimal.ZERO))))));
    stubPriceRates();

    DraftInvoiceResponse response = draftInvoiceService.preview(ORGANIZATION_ID, AUGUST);

    assertThat(response.customers().getFirst().lines()).isEmpty();
    assertThat(response.customers().getFirst().unpricedUsages()).isEmpty();
  }

  @Test
  void 미터가_없는_도입사도_고객이_전부_나온다() {
    Customer acme = customer("아크메");
    Customer beta = customer("베타");
    when(customerRepository.findByOrganizationIdOrderByNameAscIdAsc(ORGANIZATION_ID))
        .thenReturn(List.of(acme, beta));
    when(billableMetricUsageService.aggregate(ORGANIZATION_ID, AUGUST)).thenReturn(List.of());
    stubPriceRates();

    DraftInvoiceResponse response = draftInvoiceService.preview(ORGANIZATION_ID, AUGUST);

    assertThat(response.totalAmount()).isZero();
    assertThat(response.customers())
        .extracting(DraftInvoiceCustomer::customerName)
        .containsExactly("아크메", "베타");
    assertThat(response.customers())
        .allSatisfy(
            draftInvoiceCustomer -> {
              assertThat(draftInvoiceCustomer.amount()).isZero();
              assertThat(draftInvoiceCustomer.lines()).isEmpty();
              assertThat(draftInvoiceCustomer.unpricedUsages()).isEmpty();
            });
  }

  @Test
  void 단가가_없는_미터는_라인에서_빠진다() {
    Customer acme = customer("아크메");
    when(customerRepository.findByOrganizationIdOrderByNameAscIdAsc(ORGANIZATION_ID))
        .thenReturn(List.of(acme));
    when(billableMetricUsageService.aggregate(ORGANIZATION_ID, AUGUST))
        .thenReturn(
            List.of(
                usage(billableMetric("token-usage", "token"), acme, "3280"),
                usage(billableMetric("api-calls", "count"), acme, "3")));
    stubPriceRates(priceRate("token-usage", "{}", "0.5"));

    DraftInvoiceResponse response = draftInvoiceService.preview(ORGANIZATION_ID, AUGUST);

    assertThat(response.customers().getFirst().lines())
        .singleElement()
        .satisfies(
            draftInvoiceLine ->
                assertThat(draftInvoiceLine.billableMetricCode()).isEqualTo("token-usage"));
    assertThat(response.totalAmount()).isEqualTo(1640);
    assertThat(response.customers().getFirst().unpricedUsages())
        .extracting(DraftInvoiceUnpricedUsage::billableMetricCode)
        .containsExactly("api-calls");
  }

  private static Customer customer(String name) {
    return new Customer(UUID.randomUUID(), ORGANIZATION_ID, name);
  }

  private static BillableMetric billableMetric(String code, String targetProperty) {
    return new BillableMetric(
        ORGANIZATION_ID, code, code + " 미터", "chat_completion", "sum", targetProperty, List.of());
  }

  private static BillableMetricUsage usage(
      BillableMetric billableMetric, Customer customer, String quantity) {
    return new BillableMetricUsage(
        billableMetric,
        List.of(customerUsage(customer, new DimensionUsage(Map.of(), new BigDecimal(quantity)))));
  }

  private static CustomerUsage customerUsage(Customer customer, DimensionUsage... dimensionUsages) {
    BigDecimal quantity =
        List.of(dimensionUsages).stream()
            .map(DimensionUsage::quantity)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    return new CustomerUsage(
        customer.getId(), customer.getName(), quantity, List.of(dimensionUsages));
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
