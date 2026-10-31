package com.meterengine.invoice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.meterengine.customer.entity.Customer;
import com.meterengine.customer.repository.CustomerRepository;
import com.meterengine.invoice.dto.DraftInvoiceResponse;
import com.meterengine.invoice.dto.DraftInvoiceResponse.DraftInvoiceCustomer;
import com.meterengine.invoice.repository.InvoiceRepository;
import com.meterengine.metric.dto.BillableMetricUsage;
import com.meterengine.metric.entity.BillableMetric;
import com.meterengine.metric.service.BillableMetricUsageService;
import com.meterengine.pricing.dto.CustomerCharge;
import com.meterengine.pricing.dto.DimensionCharge;
import com.meterengine.pricing.dto.NegativeUsage;
import com.meterengine.pricing.dto.UnpricedUsage;
import com.meterengine.pricing.service.ChargeService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
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

  private static final UUID ORGANIZATION_ID = UUID.randomUUID();
  private static final YearMonth AUGUST = YearMonth.of(2026, 8);

  @Mock private BillableMetricUsageService billableMetricUsageService;
  @Mock private CustomerRepository customerRepository;
  @Mock private ChargeService chargeService;
  @Mock private InvoiceRepository invoiceRepository;

  private DraftInvoiceService draftInvoiceService;

  @BeforeEach
  void setUp() {
    draftInvoiceService =
        new DraftInvoiceService(
            billableMetricUsageService,
            customerRepository,
            chargeService,
            invoiceRepository,
            Clock.fixed(Instant.parse("2026-10-26T01:00:00Z"), ZoneOffset.UTC));
  }

  @Test
  void 계산_시각은_주입한_시계의_지금을_KST로_나타낸_것이다() {
    Customer acme = customer("아크메");
    stubCustomers(acme);
    stubCustomerCharges(Map.of());

    DraftInvoiceResponse response = draftInvoiceService.preview(ORGANIZATION_ID, AUGUST);

    assertThat(response.month()).isEqualTo("2026-08");
    assertThat(response.calculatedAt())
        .isEqualTo(OffsetDateTime.parse("2026-10-26T10:00:00+09:00"));
    assertThat(response.calculatedAt().getOffset()).isEqualTo(ZoneOffset.ofHours(9));
  }

  @Test
  void 이벤트가_없는_고객은_라인_없이_금액_0이다() {
    Customer acme = customer("아크메");
    Customer beta = customer("베타");
    stubCustomers(acme, beta);
    stubCustomerCharges(Map.of(acme.getId(), customerCharge(acme, 250)));

    List<DraftInvoiceCustomer> draftInvoiceCustomers =
        draftInvoiceService.preview(ORGANIZATION_ID, AUGUST).customers();

    DraftInvoiceCustomer betaDraftInvoiceCustomer = draftInvoiceCustomers.get(1);
    assertThat(betaDraftInvoiceCustomer.customerName()).isEqualTo("베타");
    assertThat(betaDraftInvoiceCustomer.amount()).isZero();
    assertThat(betaDraftInvoiceCustomer.lines()).isEmpty();
    assertThat(betaDraftInvoiceCustomer.unpricedUsages()).isEmpty();
    assertThat(betaDraftInvoiceCustomer.negativeUsages()).isEmpty();
  }

  @Test
  void 미터가_없는_도입사도_고객이_전부_나온다() {
    Customer acme = customer("아크메");
    Customer beta = customer("베타");
    stubCustomers(acme, beta);
    stubCustomerCharges(Map.of());

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
              assertThat(draftInvoiceCustomer.negativeUsages()).isEmpty();
            });
  }

  @Test
  void 확정된_고객은_빼고_total_amount는_남은_고객_금액의_합이다() {
    Customer acme = customer("아크메");
    Customer beta = customer("베타");
    Customer gamma = customer("감마");
    when(invoiceRepository.findCustomerIds(ORGANIZATION_ID, "2026-08"))
        .thenReturn(List.of(beta.getId()));
    stubCustomers(acme, beta, gamma);
    Map<UUID, CustomerCharge> customerChargeByCustomerId = new LinkedHashMap<>();
    customerChargeByCustomerId.put(acme.getId(), customerCharge(acme, 1640));
    customerChargeByCustomerId.put(beta.getId(), customerCharge(beta, 5000));
    customerChargeByCustomerId.put(gamma.getId(), customerCharge(gamma, 10));
    stubCustomerCharges(customerChargeByCustomerId);

    DraftInvoiceResponse response = draftInvoiceService.preview(ORGANIZATION_ID, AUGUST);

    assertThat(response.customers())
        .extracting(DraftInvoiceCustomer::customerName)
        .containsExactly("아크메", "감마");
    assertThat(response.totalAmount()).isEqualTo(1650);
  }

  @Test
  void 요금_계산_결과가_응답_레코드로_필드_그대로_옮겨진다() {
    Customer acme = customer("아크메");
    stubCustomers(acme);
    stubCustomerCharges(
        Map.of(
            acme.getId(),
            new CustomerCharge(
                acme.getId(),
                1640,
                List.of(
                    new DimensionCharge(
                        "token-usage",
                        "token",
                        Map.of("model", "a"),
                        new BigDecimal("3280"),
                        new BigDecimal("0.5"),
                        1640)),
                List.of(
                    new UnpricedUsage(
                        "api-calls", "count", Map.of("region", "kr"), new BigDecimal("3"))),
                List.of(
                    new NegativeUsage(
                        "refund-tokens", "token", Map.of("model", "b"), new BigDecimal("-30"))))));

    DraftInvoiceCustomer draftInvoiceCustomer =
        draftInvoiceService.preview(ORGANIZATION_ID, AUGUST).customers().getFirst();

    assertThat(draftInvoiceCustomer.customerId()).isEqualTo(acme.getId());
    assertThat(draftInvoiceCustomer.customerName()).isEqualTo("아크메");
    assertThat(draftInvoiceCustomer.amount()).isEqualTo(1640);
    assertThat(draftInvoiceCustomer.lines())
        .singleElement()
        .satisfies(
            draftInvoiceLine -> {
              assertThat(draftInvoiceLine.billableMetricCode()).isEqualTo("token-usage");
              assertThat(draftInvoiceLine.targetProperty()).isEqualTo("token");
              assertThat(draftInvoiceLine.dimensionValues()).isEqualTo(Map.of("model", "a"));
              assertThat(draftInvoiceLine.quantity()).isEqualByComparingTo("3280");
              assertThat(draftInvoiceLine.unitPrice()).isEqualByComparingTo("0.5");
              assertThat(draftInvoiceLine.amount()).isEqualTo(1640);
            });
    assertThat(draftInvoiceCustomer.unpricedUsages())
        .singleElement()
        .satisfies(
            draftInvoiceUnpricedUsage -> {
              assertThat(draftInvoiceUnpricedUsage.billableMetricCode()).isEqualTo("api-calls");
              assertThat(draftInvoiceUnpricedUsage.targetProperty()).isEqualTo("count");
              assertThat(draftInvoiceUnpricedUsage.dimensionValues())
                  .isEqualTo(Map.of("region", "kr"));
              assertThat(draftInvoiceUnpricedUsage.quantity()).isEqualByComparingTo("3");
            });
    assertThat(draftInvoiceCustomer.negativeUsages())
        .singleElement()
        .satisfies(
            draftInvoiceNegativeUsage -> {
              assertThat(draftInvoiceNegativeUsage.billableMetricCode()).isEqualTo("refund-tokens");
              assertThat(draftInvoiceNegativeUsage.targetProperty()).isEqualTo("token");
              assertThat(draftInvoiceNegativeUsage.dimensionValues())
                  .isEqualTo(Map.of("model", "b"));
              assertThat(draftInvoiceNegativeUsage.quantity()).isEqualByComparingTo("-30");
            });
  }

  private static Customer customer(String name) {
    return new Customer(UUID.randomUUID(), ORGANIZATION_ID, name, null);
  }

  private static CustomerCharge customerCharge(Customer customer, long amount) {
    return new CustomerCharge(
        customer.getId(),
        amount,
        List.of(
            new DimensionCharge(
                "token-usage", "token", Map.of(), new BigDecimal("1"), BigDecimal.ONE, amount)),
        List.of(),
        List.of());
  }

  private void stubCustomers(Customer... customers) {
    when(customerRepository.findByOrganizationIdOrderByNameAscIdAsc(ORGANIZATION_ID))
        .thenReturn(List.of(customers));
  }

  private void stubCustomerCharges(Map<UUID, CustomerCharge> customerChargeByCustomerId) {
    List<BillableMetricUsage> billableMetricUsages =
        List.of(
            new BillableMetricUsage(
                new BillableMetric(
                    ORGANIZATION_ID,
                    "token-usage",
                    "token-usage 미터",
                    "chat_completion",
                    "sum",
                    "token",
                    List.of()),
                List.of()));
    when(billableMetricUsageService.aggregate(
            ORGANIZATION_ID,
            AUGUST,
            OffsetDateTime.parse("2026-10-26T10:00:00+09:00"),
            BillableMetricUsageService.cutoffAt(AUGUST)))
        .thenReturn(billableMetricUsages);
    when(chargeService.calculate(ORGANIZATION_ID, AUGUST, billableMetricUsages))
        .thenReturn(customerChargeByCustomerId);
  }
}
