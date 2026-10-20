package com.meterengine.metric.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.meterengine.customer.entity.Customer;
import com.meterengine.customer.repository.CustomerRepository;
import com.meterengine.metric.dto.BillableMetricUsage;
import com.meterengine.metric.dto.CustomerUsage;
import com.meterengine.metric.dto.DimensionUsage;
import com.meterengine.metric.entity.BillableMetric;
import com.meterengine.metric.repository.BillableMetricRepository;
import com.meterengine.metric.repository.BillableMetricUsageRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BillableMetricUsageServiceTest {

  private static final UUID ORGANIZATION_ID = UUID.randomUUID();
  private static final YearMonth AUGUST = YearMonth.of(2026, 8);

  @Mock private BillableMetricUsageRepository billableMetricUsageRepository;
  @Mock private BillableMetricRepository billableMetricRepository;
  @Mock private CustomerRepository customerRepository;

  @Captor private ArgumentCaptor<OffsetDateTime> startCaptor;
  @Captor private ArgumentCaptor<OffsetDateTime> endCaptor;

  private BillableMetricUsageService billableMetricUsageService;

  @BeforeEach
  void setUp() {
    billableMetricUsageService =
        new BillableMetricUsageService(
            billableMetricUsageRepository, billableMetricRepository, customerRepository);
  }

  @Test
  void 기간은_KST_월의_반열린_구간으로_넘긴다() {
    UUID customerId = UUID.randomUUID();
    when(billableMetricRepository.findByOrganizationIdOrderByCodeAsc(ORGANIZATION_ID))
        .thenReturn(List.of(billableMetric("token-usage", "token")));
    when(customerRepository.findByOrganizationIdOrderByNameAscIdAsc(ORGANIZATION_ID))
        .thenReturn(List.of(new Customer(customerId, ORGANIZATION_ID, "아크메", null)));
    when(billableMetricUsageRepository.sumQuantityByCustomerIdAndDimensionValues(
            any(), any(), any(), any(), any(), any()))
        .thenReturn(Map.of());

    billableMetricUsageService.aggregate(ORGANIZATION_ID, AUGUST);

    verify(billableMetricUsageRepository)
        .sumQuantityByCustomerIdAndDimensionValues(
            eq(ORGANIZATION_ID),
            eq("chat_completion"),
            eq("token"),
            eq(List.of()),
            startCaptor.capture(),
            endCaptor.capture());
    assertThat(startCaptor.getValue()).isEqualTo(OffsetDateTime.parse("2026-08-01T00:00:00+09:00"));
    assertThat(endCaptor.getValue()).isEqualTo(OffsetDateTime.parse("2026-09-01T00:00:00+09:00"));
  }

  @Test
  void 이벤트가_없는_고객도_0으로_결과에_들어간다() {
    UUID withEvents = UUID.randomUUID();
    UUID withoutEvents = UUID.randomUUID();
    when(billableMetricRepository.findByOrganizationIdOrderByCodeAsc(ORGANIZATION_ID))
        .thenReturn(List.of(billableMetric("token-usage", "token")));
    when(customerRepository.findByOrganizationIdOrderByNameAscIdAsc(ORGANIZATION_ID))
        .thenReturn(
            List.of(
                new Customer(withEvents, ORGANIZATION_ID, "아크메", null),
                new Customer(withoutEvents, ORGANIZATION_ID, "베타", null)));
    when(billableMetricUsageRepository.sumQuantityByCustomerIdAndDimensionValues(
            any(), any(), any(), any(), any(), any()))
        .thenReturn(Map.of(withEvents, List.of(baseDimensionUsage("1200"))));

    List<CustomerUsage> customerUsages =
        billableMetricUsageService.aggregate(ORGANIZATION_ID, AUGUST).getFirst().customers();

    assertThat(customerUsages)
        .containsExactly(
            new CustomerUsage(
                withEvents, "아크메", new BigDecimal("1200"), List.of(baseDimensionUsage("1200"))),
            new CustomerUsage(withoutEvents, "베타", BigDecimal.ZERO, List.of()));
  }

  @Test
  void 고객의_수량은_조합별_수량의_합이다() {
    UUID customerId = UUID.randomUUID();
    DimensionUsage opus = new DimensionUsage(Map.of("model", "opus-5"), new BigDecimal("500"));
    DimensionUsage haiku = new DimensionUsage(Map.of("model", "haiku-5"), new BigDecimal("300"));
    when(billableMetricRepository.findByOrganizationIdOrderByCodeAsc(ORGANIZATION_ID))
        .thenReturn(List.of(billableMetric("token-usage", "token")));
    when(customerRepository.findByOrganizationIdOrderByNameAscIdAsc(ORGANIZATION_ID))
        .thenReturn(List.of(new Customer(customerId, ORGANIZATION_ID, "아크메", null)));
    when(billableMetricUsageRepository.sumQuantityByCustomerIdAndDimensionValues(
            any(), any(), any(), any(), any(), any()))
        .thenReturn(Map.of(customerId, List.of(opus, haiku)));

    CustomerUsage customerUsage =
        billableMetricUsageService
            .aggregate(ORGANIZATION_ID, AUGUST)
            .getFirst()
            .customers()
            .getFirst();

    assertThat(customerUsage.quantity()).isEqualByComparingTo("800");
    assertThat(customerUsage.dimensionUsages()).containsExactly(opus, haiku);
  }

  @Test
  void 미터가_여러_개면_미터마다_집계해_묶는다() {
    UUID customerId = UUID.randomUUID();
    BillableMetric tokens = billableMetric("token-usage", "token");
    BillableMetric requests = billableMetric("request-usage", "request");
    when(billableMetricRepository.findByOrganizationIdOrderByCodeAsc(ORGANIZATION_ID))
        .thenReturn(List.of(tokens, requests));
    when(customerRepository.findByOrganizationIdOrderByNameAscIdAsc(ORGANIZATION_ID))
        .thenReturn(List.of(new Customer(customerId, ORGANIZATION_ID, "아크메", null)));
    when(billableMetricUsageRepository.sumQuantityByCustomerIdAndDimensionValues(
            any(), any(), eq("token"), any(), any(), any()))
        .thenReturn(Map.of(customerId, List.of(baseDimensionUsage("1200"))));
    when(billableMetricUsageRepository.sumQuantityByCustomerIdAndDimensionValues(
            any(), any(), eq("request"), any(), any(), any()))
        .thenReturn(Map.of(customerId, List.of(baseDimensionUsage("7"))));

    List<BillableMetricUsage> billableMetricUsages =
        billableMetricUsageService.aggregate(ORGANIZATION_ID, AUGUST);

    assertThat(billableMetricUsages)
        .extracting(usage -> usage.billableMetric().getCode())
        .containsExactly(tokens.getCode(), requests.getCode());
    assertThat(billableMetricUsages.get(0).customers().getFirst().quantity())
        .isEqualByComparingTo("1200");
    assertThat(billableMetricUsages.get(1).customers().getFirst().quantity())
        .isEqualByComparingTo("7");
  }

  @Test
  void 미터가_없으면_고객을_조회하지도_않고_빈_결과다() {
    when(billableMetricRepository.findByOrganizationIdOrderByCodeAsc(ORGANIZATION_ID))
        .thenReturn(List.of());

    assertThat(billableMetricUsageService.aggregate(ORGANIZATION_ID, AUGUST)).isEmpty();

    verifyNoInteractions(customerRepository, billableMetricUsageRepository);
  }

  @Test
  void 아직_구현하지_않은_집계_방식이면_조용히_0을_내지_않고_멈춘다() {
    when(billableMetricRepository.findByOrganizationIdOrderByCodeAsc(ORGANIZATION_ID))
        .thenReturn(
            List.of(
                new BillableMetric(
                    ORGANIZATION_ID,
                    "api-calls",
                    "API 호출 수",
                    "chat_completion",
                    "COUNT",
                    null,
                    List.of())));
    when(customerRepository.findByOrganizationIdOrderByNameAscIdAsc(ORGANIZATION_ID))
        .thenReturn(List.of(new Customer(UUID.randomUUID(), ORGANIZATION_ID, "아크메", null)));

    assertThatThrownBy(() -> billableMetricUsageService.aggregate(ORGANIZATION_ID, AUGUST))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("api-calls")
        .hasMessageContaining("COUNT");

    verify(billableMetricUsageRepository, never())
        .sumQuantityByCustomerIdAndDimensionValues(any(), any(), any(), any(), any(), any());
  }

  @Test
  void SUM인데_합할_대상_키가_없는_미터도_멈춘다() {
    when(billableMetricRepository.findByOrganizationIdOrderByCodeAsc(ORGANIZATION_ID))
        .thenReturn(
            List.of(
                new BillableMetric(
                    ORGANIZATION_ID,
                    "broken",
                    "잘못 등록된 미터",
                    "chat_completion",
                    "sum",
                    null,
                    List.of())));
    when(customerRepository.findByOrganizationIdOrderByNameAscIdAsc(ORGANIZATION_ID))
        .thenReturn(List.of(new Customer(UUID.randomUUID(), ORGANIZATION_ID, "아크메", null)));

    assertThatThrownBy(() -> billableMetricUsageService.aggregate(ORGANIZATION_ID, AUGUST))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("target_property");

    verify(billableMetricUsageRepository, never())
        .sumQuantityByCustomerIdAndDimensionValues(any(), any(), any(), any(), any(), any());
  }

  private static DimensionUsage baseDimensionUsage(String quantity) {
    return new DimensionUsage(Map.of(), new BigDecimal(quantity));
  }

  private BillableMetric billableMetric(String code, String targetProperty) {
    return new BillableMetric(
        ORGANIZATION_ID, code, code + " 미터", "chat_completion", "sum", targetProperty, List.of());
  }
}
