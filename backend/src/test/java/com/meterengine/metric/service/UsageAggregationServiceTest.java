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
import com.meterengine.metric.dto.CustomerUsage;
import com.meterengine.metric.dto.MetricUsage;
import com.meterengine.metric.entity.BillableMetric;
import com.meterengine.metric.repository.BillableMetricRepository;
import com.meterengine.metric.repository.UsageAggregationRepository;
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

/**
 * 집계 서비스의 분기 검증 (MS2-129).
 *
 * <p>월 귀속이 실제로 맞는지는 Postgres의 TIMESTAMPTZ 비교에 달려 있어 {@link
 * com.meterengine.metric.UsageAggregationIntegrationTest}가 맡는다. 여기서는 서비스가 레포지토리에 어떤 기간을 넘기는지, 결과를
 * 어떻게 조립하는지만 본다.
 */
@ExtendWith(MockitoExtension.class)
class UsageAggregationServiceTest {

  private static final UUID ORG_ID = UUID.randomUUID();
  private static final YearMonth AUGUST = YearMonth.of(2026, 8);

  @Mock private UsageAggregationRepository usageEvents;
  @Mock private BillableMetricRepository metrics;
  @Mock private CustomerRepository customers;

  @Captor private ArgumentCaptor<OffsetDateTime> startCaptor;
  @Captor private ArgumentCaptor<OffsetDateTime> endCaptor;

  private UsageAggregationService service;

  @BeforeEach
  void setUp() {
    service = new UsageAggregationService(usageEvents, metrics, customers);
  }

  @Test
  void 기간은_KST_월의_반열린_구간으로_넘긴다() {
    UUID customerId = UUID.randomUUID();
    when(metrics.findByOrganizationIdOrderByCodeAsc(ORG_ID))
        .thenReturn(List.of(metric("token-usage", "token")));
    when(customers.findByOrganizationIdOrderByNameAscIdAsc(ORG_ID))
        .thenReturn(List.of(new Customer(customerId, ORG_ID, "아크메")));
    when(usageEvents.sumByCustomer(any(), any(), any(), any(), any())).thenReturn(Map.of());

    service.aggregate(ORG_ID, AUGUST);

    verify(usageEvents)
        .sumByCustomer(
            eq(ORG_ID),
            eq("chat_completion"),
            eq("token"),
            startCaptor.capture(),
            endCaptor.capture());
    // 끝이 8/31 23:59:59가 아니라 9/1 00:00:00인 것이 핵심이다. 마지막 1초를 제외하면
    // 23:59:59.5 같은 이벤트가 어느 달에도 속하지 않는다.
    assertThat(startCaptor.getValue()).isEqualTo(OffsetDateTime.parse("2026-08-01T00:00:00+09:00"));
    assertThat(endCaptor.getValue()).isEqualTo(OffsetDateTime.parse("2026-09-01T00:00:00+09:00"));
  }

  @Test
  void 이벤트가_없는_고객도_0으로_결과에_들어간다() {
    UUID withEvents = UUID.randomUUID();
    UUID withoutEvents = UUID.randomUUID();
    when(metrics.findByOrganizationIdOrderByCodeAsc(ORG_ID))
        .thenReturn(List.of(metric("token-usage", "token")));
    when(customers.findByOrganizationIdOrderByNameAscIdAsc(ORG_ID))
        .thenReturn(
            List.of(
                new Customer(withEvents, ORG_ID, "아크메"),
                new Customer(withoutEvents, ORG_ID, "베타")));
    when(usageEvents.sumByCustomer(any(), any(), any(), any(), any()))
        .thenReturn(Map.of(withEvents, new BigDecimal("1200")));

    List<CustomerUsage> usages = service.aggregate(ORG_ID, AUGUST).getFirst().customers();

    assertThat(usages)
        .containsExactly(
            new CustomerUsage(withEvents, "아크메", new BigDecimal("1200")),
            new CustomerUsage(withoutEvents, "베타", BigDecimal.ZERO));
  }

  @Test
  void 미터가_여러_개면_미터마다_집계해_묶는다() {
    UUID customerId = UUID.randomUUID();
    BillableMetric tokens = metric("token-usage", "token");
    BillableMetric requests = metric("request-usage", "request");
    when(metrics.findByOrganizationIdOrderByCodeAsc(ORG_ID)).thenReturn(List.of(tokens, requests));
    when(customers.findByOrganizationIdOrderByNameAscIdAsc(ORG_ID))
        .thenReturn(List.of(new Customer(customerId, ORG_ID, "아크메")));
    when(usageEvents.sumByCustomer(any(), any(), eq("token"), any(), any()))
        .thenReturn(Map.of(customerId, new BigDecimal("1200")));
    when(usageEvents.sumByCustomer(any(), any(), eq("request"), any(), any()))
        .thenReturn(Map.of(customerId, new BigDecimal("7")));

    List<MetricUsage> result = service.aggregate(ORG_ID, AUGUST);

    assertThat(result)
        .extracting(usage -> usage.metric().getCode())
        .containsExactly(tokens.getCode(), requests.getCode());
    assertThat(result.get(0).customers().getFirst().quantity()).isEqualByComparingTo("1200");
    assertThat(result.get(1).customers().getFirst().quantity()).isEqualByComparingTo("7");
  }

  @Test
  void 미터가_없으면_고객을_조회하지도_않고_빈_결과다() {
    when(metrics.findByOrganizationIdOrderByCodeAsc(ORG_ID)).thenReturn(List.of());

    assertThat(service.aggregate(ORG_ID, AUGUST)).isEmpty();

    verifyNoInteractions(customers, usageEvents);
  }

  @Test
  void 아직_구현하지_않은_집계_방식이면_조용히_0을_내지_않고_멈춘다() {
    when(metrics.findByOrganizationIdOrderByCodeAsc(ORG_ID))
        .thenReturn(
            List.of(
                new BillableMetric(
                    ORG_ID,
                    "api-calls",
                    "API 호출 수",
                    "chat_completion",
                    "COUNT",
                    null,
                    BigDecimal.ONE)));
    when(customers.findByOrganizationIdOrderByNameAscIdAsc(ORG_ID))
        .thenReturn(List.of(new Customer(UUID.randomUUID(), ORG_ID, "아크메")));

    assertThatThrownBy(() -> service.aggregate(ORG_ID, AUGUST))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("api-calls")
        .hasMessageContaining("COUNT");

    verify(usageEvents, never()).sumByCustomer(any(), any(), any(), any(), any());
  }

  @Test
  void SUM인데_합할_대상_키가_없는_미터도_멈춘다() {
    // 스키마상 target_property는 nullable이라 SUM 미터에 값이 비어 있을 수 있다. 그대로 두면
    // jsonb_typeof 필터에 아무 행도 걸리지 않아 모든 고객이 0으로 나간다 (조용히 틀린 값).
    when(metrics.findByOrganizationIdOrderByCodeAsc(ORG_ID))
        .thenReturn(
            List.of(
                new BillableMetric(
                    ORG_ID,
                    "broken",
                    "잘못 등록된 미터",
                    "chat_completion",
                    "SUM",
                    null,
                    BigDecimal.ONE)));
    when(customers.findByOrganizationIdOrderByNameAscIdAsc(ORG_ID))
        .thenReturn(List.of(new Customer(UUID.randomUUID(), ORG_ID, "아크메")));

    assertThatThrownBy(() -> service.aggregate(ORG_ID, AUGUST))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("target_property");

    verify(usageEvents, never()).sumByCustomer(any(), any(), any(), any(), any());
  }

  private BillableMetric metric(String code, String targetProperty) {
    return new BillableMetric(
        ORG_ID,
        code,
        code + " 미터",
        "chat_completion",
        "SUM",
        targetProperty,
        new BigDecimal("0.5"));
  }
}
