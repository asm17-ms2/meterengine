package com.meterengine.invoice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.meterengine.customer.entity.Customer;
import com.meterengine.customer.repository.CustomerRepository;
import com.meterengine.invoice.dto.DraftInvoiceResponse;
import com.meterengine.invoice.dto.DraftInvoiceResponse.DraftInvoiceCustomerEntry;
import com.meterengine.invoice.dto.DraftInvoiceResponse.MetricLineItem;
import com.meterengine.metric.dto.BillableMetricUsage;
import com.meterengine.metric.dto.CustomerUsage;
import com.meterengine.metric.entity.BillableMetric;
import com.meterengine.metric.service.BillableMetricUsageService;
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

@ExtendWith(MockitoExtension.class)
class DraftInvoiceServiceTest {

  private static final UUID ORG_ID = UUID.randomUUID();
  private static final YearMonth AUGUST = YearMonth.of(2026, 8);

  @Mock private BillableMetricUsageService aggregation;
  @Mock private CustomerRepository customers;
  @Mock private PriceRateRepository prices;

  private DraftInvoiceService service;

  @BeforeEach
  void setUp() {
    service = new DraftInvoiceService(aggregation, customers, prices);
  }

  @Test
  void 금액은_사용량_곱하기_단가다() {
    Customer acme = customer("아크메");
    when(customers.findByOrganizationIdOrderByNameAscIdAsc(ORG_ID)).thenReturn(List.of(acme));
    when(aggregation.aggregate(ORG_ID, AUGUST))
        .thenReturn(List.of(usage(metric("token-usage", "token"), acme, "3290")));
    when(prices.findBaseUnitPrices(ORG_ID))
        .thenReturn(Map.of("token-usage", new BigDecimal("0.5")));

    DraftInvoiceResponse response = service.preview(ORG_ID, AUGUST);

    assertThat(response.month()).isEqualTo("2026-08");
    assertThat(response.calculatedAt()).isNotNull();
    assertThat(response.calculatedAt().getOffset()).isEqualTo(ZoneOffset.ofHours(9));
    assertThat(response.totalAmount()).isEqualTo(1645);
    DraftInvoiceCustomerEntry entry = response.customers().getFirst();
    assertThat(entry.customerId()).isEqualTo(acme.getId());
    assertThat(entry.customerName()).isEqualTo("아크메");
    assertThat(entry.amount()).isEqualTo(1645);
    MetricLineItem line = entry.lines().getFirst();
    assertThat(line.billableMetricCode()).isEqualTo("token-usage");
    assertThat(line.targetProperty()).isEqualTo("token");
    assertThat(line.quantity()).isEqualByComparingTo("3290");
    assertThat(line.unitPrice()).isEqualByComparingTo("0.5");
    assertThat(line.amount()).isEqualTo(1645);
  }

  @Test
  void 원_미만은_버린다() {
    Customer acme = customer("아크메");
    when(customers.findByOrganizationIdOrderByNameAscIdAsc(ORG_ID)).thenReturn(List.of(acme));
    when(aggregation.aggregate(ORG_ID, AUGUST))
        .thenReturn(List.of(usage(metric("token-usage", "token"), acme, "3291")));
    when(prices.findBaseUnitPrices(ORG_ID))
        .thenReturn(Map.of("token-usage", new BigDecimal("0.5")));

    DraftInvoiceResponse response = service.preview(ORG_ID, AUGUST);

    assertThat(response.customers().getFirst().amount()).isEqualTo(1645);
  }

  @Test
  void 절사는_라인별로_하고_합산한다() {
    Customer acme = customer("아크메");
    when(customers.findByOrganizationIdOrderByNameAscIdAsc(ORG_ID)).thenReturn(List.of(acme));
    when(aggregation.aggregate(ORG_ID, AUGUST))
        .thenReturn(
            List.of(
                usage(metric("token-usage", "token"), acme, "3291"),
                usage(metric("api-request-count", "count"), acme, "5")));
    when(prices.findBaseUnitPrices(ORG_ID))
        .thenReturn(
            Map.of(
                "token-usage", new BigDecimal("0.5"),
                "api-request-count", new BigDecimal("0.5")));

    DraftInvoiceResponse response = service.preview(ORG_ID, AUGUST);

    DraftInvoiceCustomerEntry entry = response.customers().getFirst();
    assertThat(entry.lines()).extracting(MetricLineItem::amount).containsExactly(1645L, 2L);
    assertThat(entry.amount()).isEqualTo(1647);
    assertThat(response.totalAmount()).isEqualTo(1647);
  }

  @Test
  void 이벤트가_없는_고객은_사용량_0_금액_0이다() {
    Customer acme = customer("아크메");
    Customer beta = customer("베타");
    when(customers.findByOrganizationIdOrderByNameAscIdAsc(ORG_ID)).thenReturn(List.of(acme, beta));
    when(aggregation.aggregate(ORG_ID, AUGUST))
        .thenReturn(
            List.of(
                new BillableMetricUsage(
                    metric("token-usage", "token"),
                    List.of(
                        new CustomerUsage(acme.getId(), "아크메", new BigDecimal("500")),
                        new CustomerUsage(beta.getId(), "베타", BigDecimal.ZERO)))));
    when(prices.findBaseUnitPrices(ORG_ID))
        .thenReturn(Map.of("token-usage", new BigDecimal("0.5")));

    List<DraftInvoiceCustomerEntry> entries = service.preview(ORG_ID, AUGUST).customers();

    DraftInvoiceCustomerEntry betaEntry = entries.get(1);
    assertThat(betaEntry.amount()).isZero();
    MetricLineItem betaLine = betaEntry.lines().getFirst();
    assertThat(betaLine.quantity()).isEqualByComparingTo("0");
    assertThat(betaLine.amount()).isZero();
    assertThat(betaLine.unitPrice()).isEqualByComparingTo("0.5");
  }

  @Test
  void 미터가_없는_도입사도_고객이_전부_나온다() {
    Customer acme = customer("아크메");
    Customer beta = customer("베타");
    when(customers.findByOrganizationIdOrderByNameAscIdAsc(ORG_ID)).thenReturn(List.of(acme, beta));
    when(aggregation.aggregate(ORG_ID, AUGUST)).thenReturn(List.of());
    when(prices.findBaseUnitPrices(ORG_ID)).thenReturn(Map.of());

    DraftInvoiceResponse response = service.preview(ORG_ID, AUGUST);

    assertThat(response.totalAmount()).isZero();
    assertThat(response.customers())
        .extracting(DraftInvoiceCustomerEntry::customerName)
        .containsExactly("아크메", "베타");
    assertThat(response.customers())
        .allSatisfy(
            entry -> {
              assertThat(entry.amount()).isZero();
              assertThat(entry.lines()).isEmpty();
            });
  }

  @Test
  void 단가가_없는_미터는_라인에서_빠진다() {
    Customer acme = customer("아크메");
    when(customers.findByOrganizationIdOrderByNameAscIdAsc(ORG_ID)).thenReturn(List.of(acme));
    when(aggregation.aggregate(ORG_ID, AUGUST))
        .thenReturn(
            List.of(
                usage(metric("token-usage", "token"), acme, "3290"),
                usage(metric("api-calls", "count"), acme, "3")));
    when(prices.findBaseUnitPrices(ORG_ID))
        .thenReturn(Map.of("token-usage", new BigDecimal("0.5")));

    DraftInvoiceResponse response = service.preview(ORG_ID, AUGUST);

    assertThat(response.customers().getFirst().lines())
        .singleElement()
        .satisfies(line -> assertThat(line.billableMetricCode()).isEqualTo("token-usage"));
    assertThat(response.totalAmount()).isEqualTo(1645);
  }

  private static Customer customer(String name) {
    return new Customer(UUID.randomUUID(), ORG_ID, name);
  }

  private static BillableMetric metric(String code, String targetProperty) {
    return new BillableMetric(ORG_ID, code, code + " 미터", "chat_completion", "SUM", targetProperty);
  }

  private static BillableMetricUsage usage(
      BillableMetric metric, Customer customer, String quantity) {
    return new BillableMetricUsage(
        metric,
        List.of(new CustomerUsage(customer.getId(), customer.getName(), new BigDecimal(quantity))));
  }
}
