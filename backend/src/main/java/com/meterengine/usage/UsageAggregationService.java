package com.meterengine.usage;

import com.meterengine.customer.Customer;
import com.meterengine.customer.CustomerRepository;
import com.meterengine.metric.BillableMetric;
import com.meterengine.metric.BillableMetricRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 고객별 월 사용량 집계 (MS2-129).
 *
 * <p>도입사의 미터를 하나씩 돌면서, 그 미터의 event_type과 맞는 이벤트를 고객별로 합산한다. 금액은 내지 않는다. 사용량에 단가를 곱하는 일은 MS2-124 청구
 * 예정액 API의 몫이고, 이 서비스의 결과가 그 입력이 된다.
 *
 * <p>public인 이유: 그 MS2-124가 invoice 패키지에서 이 서비스를 직접 호출한다. 결과 모델({@link MetricUsage})이 처음부터
 * public이었던 것과 같은 사정이다.
 */
@Service
public class UsageAggregationService {

  /**
   * 청구 기간을 자르는 기준 시간대다. "8월 사용량"이 무슨 뜻인지는 시간대를 정해야 답이 나온다 (스토리 MS2-121은 국내 도입사 대상이라 KST).
   *
   * <p>이 상수가 곧 월 경계 인수 기준의 근거다. 2026-08-31T23:59:59+09:00은 8월, 2026-09-01T00:00:00+09:00은 9월.
   */
  public static final ZoneId BILLING_ZONE = ZoneId.of("Asia/Seoul");

  private final UsageAggregationRepository usageEvents;
  private final BillableMetricRepository metrics;
  private final CustomerRepository customers;

  UsageAggregationService(
      UsageAggregationRepository usageEvents,
      BillableMetricRepository metrics,
      CustomerRepository customers) {
    this.usageEvents = usageEvents;
    this.metrics = metrics;
    this.customers = customers;
  }

  /** 지금이 속한 달(KST). 기간을 지정하지 않은 조회의 기본값이다. */
  public static YearMonth currentMonth() {
    return YearMonth.now(BILLING_ZONE);
  }

  /**
   * 지정 월의 미터별/고객별 사용량을 낸다.
   *
   * <p>고객 목록을 기준으로 삼기 때문에 이벤트가 한 건도 없는 고객도 quantity 0으로 들어간다 (MS2-129 팀 결정). 이벤트 쪽을 기준으로 하면 그 고객은
   * 아예 나오지 않아, 상위 서비스가 고객 목록과 병합하는 로직을 따로 갖게 된다.
   *
   * <p>읽기 트랜잭션으로 묶는 이유: 미터, 고객, 이벤트를 세 번에 나눠 읽는데 그 사이에 데이터가 바뀌면 한 응답 안에서 서로 다른 시점을 섞어 보게 된다. 묶어 두면
   * JPA 레포지토리와 JdbcTemplate이 같은 커넥션과 같은 스냅샷을 쓴다.
   */
  @Transactional(readOnly = true)
  public List<MetricUsage> aggregate(UUID organizationId, YearMonth month) {
    List<BillableMetric> organizationMetrics =
        metrics.findByOrganizationIdOrderByCodeAsc(organizationId);
    if (organizationMetrics.isEmpty()) {
      return List.of();
    }

    // 기간은 반열린 구간 [start, end)다. occurred_at이 TIMESTAMPTZ라 이 비교가 절대 시각으로 이뤄져서,
    // 같은 순간을 어느 오프셋으로 표기해 보냈든 같은 달에 귀속된다 (UsageAggregationRepository.sumByCustomer 참조).
    OffsetDateTime start = month.atDay(1).atStartOfDay(BILLING_ZONE).toOffsetDateTime();
    OffsetDateTime end = month.plusMonths(1).atDay(1).atStartOfDay(BILLING_ZONE).toOffsetDateTime();

    List<Customer> organizationCustomers =
        customers.findByOrganizationIdOrderByNameAscIdAsc(organizationId);

    return organizationMetrics.stream()
        .map(metric -> aggregateMetric(metric, organizationCustomers, start, end))
        .toList();
  }

  private MetricUsage aggregateMetric(
      BillableMetric metric,
      List<Customer> organizationCustomers,
      OffsetDateTime start,
      OffsetDateTime end) {
    requireSupported(metric);

    Map<UUID, BigDecimal> sums =
        usageEvents.sumByCustomer(
            metric.getOrganizationId(),
            metric.getEventType(),
            metric.getTargetProperty(),
            start,
            end);

    List<CustomerUsage> usages =
        organizationCustomers.stream()
            .map(
                customer ->
                    new CustomerUsage(
                        // 현재 고객의 UUID가 전역 고유하므로, 도입사 ID를 보낼 필요가 없다. 이후 고객에게 alias가 붙으면 추가 필요성을
                        // 검토해야한다.
                        customer.getId(),
                        customer.getName(),
                        sums.getOrDefault(customer.getId(), BigDecimal.ZERO)))
            .toList();

    return new MetricUsage(metric, usages);
  }

  /**
   * 이 슬라이스가 계산할 수 없는 미터면 멈춘다.
   *
   * <p>4xx가 아니라 예외로 500을 내는 쪽을 택했다. 도입사가 요청을 잘못 보낸 것이 아니라 우리가 아직 만들지 않은 것이라, 요청을 고쳐서 해결할 수 있다는 신호를
   * 주면 안 된다. 무엇보다 조용히 0이나 부분 합을 내려보내면 그 값이 그대로 청구 근거가 된다.
   *
   * <p>미터를 만들 통로가 아직 시드뿐이라 실제로는 발생하지 않는다. 미터 등록 API가 생기는 슬라이스에서 COUNT를 구현하거나, 등록 시점에 막게 된다.
   */
  private void requireSupported(BillableMetric metric) {
    if (!metric.isSum()) {
      throw new IllegalStateException(
          "metric %s uses aggregation %s, which is not implemented yet (only SUM)"
              .formatted(metric.getCode(), metric.getAggregation()));
    }
    if (metric.getTargetProperty() == null || metric.getTargetProperty().isBlank()) {
      throw new IllegalStateException(
          "metric %s aggregates with SUM but has no target_property to sum"
              .formatted(metric.getCode()));
    }
  }
}
