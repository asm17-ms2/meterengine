package com.meterengine.invoice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.meterengine.customer.entity.Customer;
import com.meterengine.customer.repository.CustomerRepository;
import com.meterengine.invoice.dto.DraftInvoiceResponse;
import com.meterengine.invoice.dto.DraftInvoiceResponse.CustomerEntry;
import com.meterengine.invoice.dto.DraftInvoiceResponse.LineEntry;
import com.meterengine.metric.dto.CustomerUsage;
import com.meterengine.metric.dto.MetricUsage;
import com.meterengine.metric.entity.BillableMetric;
import com.meterengine.metric.service.MetricUsageService;
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

/**
 * 청구 예정액 계산의 분기 검증 (MS2-124).
 *
 * <p>집계 자체가 맞는지는 MS2-129의 테스트가 맡는다. 여기서는 집계 결과에 단가를 곱해 응답으로 조립하는 부분만 본다.
 *
 * <p>TODO: 목 사용 정책은 멘토 답변 후 확정한다 (스프린트 플래닝 회의6). 목 금지로 결정되면 계산 테스트는 순수 계산 직접 호출로, 피벗 테스트는 통합 테스트로
 * 옮긴다.
 */
@ExtendWith(MockitoExtension.class)
class DraftInvoiceServiceTest {

  private static final UUID ORG_ID = UUID.randomUUID();
  private static final YearMonth AUGUST = YearMonth.of(2026, 8);

  @Mock private MetricUsageService aggregation;
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
    CustomerEntry entry = response.customers().getFirst();
    assertThat(entry.customerId()).isEqualTo(acme.getId());
    assertThat(entry.customerName()).isEqualTo("아크메");
    assertThat(entry.amount()).isEqualTo(1645);
    LineEntry line = entry.lines().getFirst();
    assertThat(line.metricCode()).isEqualTo("token-usage");
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

    // 3291 x 0.5 = 1645.5원. 반올림이 아니라 절사라 1645원이다.
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

    // 1645.5원과 2.5원. 라인마다 절사한 뒤 합산하므로 1645 + 2 = 1647원이다.
    // 합산 후 한 번만 절사하면 1648원이 되어, 화면의 라인 금액을 다 더해도 소계와 안 맞는 표가 된다.
    CustomerEntry entry = response.customers().getFirst();
    assertThat(entry.lines()).extracting(LineEntry::amount).containsExactly(1645L, 2L);
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
                new MetricUsage(
                    metric("token-usage", "token"),
                    List.of(
                        new CustomerUsage(acme.getId(), "아크메", new BigDecimal("500")),
                        new CustomerUsage(beta.getId(), "베타", BigDecimal.ZERO)))));
    when(prices.findBaseUnitPrices(ORG_ID))
        .thenReturn(Map.of("token-usage", new BigDecimal("0.5")));

    List<CustomerEntry> entries = service.preview(ORG_ID, AUGUST).customers();

    CustomerEntry betaEntry = entries.get(1);
    assertThat(betaEntry.amount()).isZero();
    LineEntry betaLine = betaEntry.lines().getFirst();
    assertThat(betaLine.quantity()).isEqualByComparingTo("0");
    assertThat(betaLine.amount()).isZero();
    // 단가는 사용량과 무관한 미터의 속성이라 0이 아니라 실제 값이 나간다 (화면 목업과 동일)
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
        .extracting(CustomerEntry::customerName)
        .containsExactly("아크메", "베타");
    assertThat(response.customers())
        .allSatisfy(
            entry -> {
              assertThat(entry.amount()).isZero();
              assertThat(entry.lines()).isEmpty();
            });
  }

  /**
   * V2 마이그레이션과 시드가 미터:단가 1:1을 보장하므로 정상 경로에서는 없다. 그래도 데이터가 깨졌을 때 조용한 0원 청구 대신 즉시 실패해야 한다 (V1의
   * longValueExact와 같은 태도).
   */
  @Test
  void 단가가_없는_미터는_즉시_실패한다() {
    Customer acme = customer("아크메");
    when(customers.findByOrganizationIdOrderByNameAscIdAsc(ORG_ID)).thenReturn(List.of(acme));
    when(aggregation.aggregate(ORG_ID, AUGUST))
        .thenReturn(List.of(usage(metric("token-usage", "token"), acme, "3290")));
    when(prices.findBaseUnitPrices(ORG_ID)).thenReturn(Map.of());

    assertThatThrownBy(() -> service.preview(ORG_ID, AUGUST))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("token-usage");
  }

  private static Customer customer(String name) {
    return new Customer(UUID.randomUUID(), ORG_ID, name);
  }

  private static BillableMetric metric(String code, String targetProperty) {
    return new BillableMetric(ORG_ID, code, code + " 미터", "chat_completion", "SUM", targetProperty);
  }

  private static MetricUsage usage(BillableMetric metric, Customer customer, String quantity) {
    return new MetricUsage(
        metric,
        List.of(new CustomerUsage(customer.getId(), customer.getName(), new BigDecimal(quantity))));
  }
}
