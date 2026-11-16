package com.meterengine.metric.service;

import com.meterengine.customer.entity.Customer;
import com.meterengine.customer.repository.CustomerRepository;
import com.meterengine.global.security.BusinessEnvironmentContext;
import com.meterengine.global.time.BillingMonth;
import com.meterengine.metric.dto.BillableMetricUsage;
import com.meterengine.metric.dto.CustomerUsage;
import com.meterengine.metric.dto.DimensionUsage;
import com.meterengine.metric.entity.BillableMetric;
import com.meterengine.metric.entity.MetricAggregation;
import com.meterengine.metric.repository.BillableMetricPeriodRepository;
import com.meterengine.metric.repository.BillableMetricRepository;
import com.meterengine.metric.repository.BillableMetricUsageRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillableMetricUsageService {

  public static final ZoneId BILLING_ZONE = BillingMonth.ZONE;

  private final BillableMetricUsageRepository billableMetricUsageRepository;
  private final BillableMetricRepository billableMetricRepository;
  private final CustomerRepository customerRepository;
  private final BillableMetricPeriodRepository billableMetricPeriodRepository;
  private final Clock clock;

  BillableMetricUsageService(
      BillableMetricUsageRepository billableMetricUsageRepository,
      BillableMetricRepository billableMetricRepository,
      CustomerRepository customerRepository,
      BillableMetricPeriodRepository billableMetricPeriodRepository,
      Clock clock) {
    this.clock = clock;
    this.billableMetricUsageRepository = billableMetricUsageRepository;
    this.billableMetricRepository = billableMetricRepository;
    this.customerRepository = customerRepository;
    this.billableMetricPeriodRepository = billableMetricPeriodRepository;
  }

  public static YearMonth currentMonth() {
    return YearMonth.now(BILLING_ZONE);
  }

  public static YearMonth currentMonth(Clock clock) {
    return YearMonth.now(clock.withZone(BILLING_ZONE));
  }

  // 그 달 사용량의 집계 마감 시각인 다음 달 2일 00:00 KST. 이 시각 전에 받은 그 달 이벤트만 집계하고 청구한다.
  public static OffsetDateTime cutoffAt(YearMonth month) {
    return BillingMonth.start(month.plusMonths(1)).plusDays(1);
  }

  public static OffsetDateTime aggregationEnd(YearMonth month, OffsetDateTime now) {
    return BillingMonth.cutoff(month, now);
  }

  @Transactional(readOnly = true)
  public List<BillableMetricUsage> aggregate(UUID organizationId, YearMonth month) {
    return aggregate(organizationId, month, BillingMonth.now(clock));
  }

  @Transactional(readOnly = true)
  public List<BillableMetricUsage> aggregate(
      UUID organizationId, YearMonth month, OffsetDateTime cutoff) {
    return aggregate(organizationId, month, cutoff, cutoffAt(month));
  }

  // 고객 한 명의 사용량만 집계한다. 결과의 고객 목록에도 그 고객만 있다.
  @Transactional(readOnly = true)
  public List<BillableMetricUsage> aggregate(
      UUID organizationId, YearMonth month, UUID customerId) {
    return aggregate(
        organizationId, month, BillingMonth.now(clock), cutoffAt(month), List.of(customerId));
  }

  // 그 달에 cutoff 전에 발생했고 receivedBefore 전에 받은 이벤트를 집계한다.
  @Transactional(readOnly = true)
  public List<BillableMetricUsage> aggregate(
      UUID organizationId, YearMonth month, OffsetDateTime cutoff, OffsetDateTime receivedBefore) {
    return aggregate(organizationId, month, cutoff, receivedBefore, null);
  }

  // customerIds가 있으면 그 고객들의 이벤트만 집계하고, 결과의 고객 목록에도 그 고객들만 있다.
  @Transactional(readOnly = true)
  public List<BillableMetricUsage> aggregate(
      UUID organizationId,
      YearMonth month,
      OffsetDateTime cutoff,
      OffsetDateTime receivedBefore,
      Collection<UUID> customerIds) {
    List<BillableMetric> billableMetrics =
        (BusinessEnvironmentContext.environmentId() == null
            ? billableMetricRepository.findByOrganizationIdOrderByCodeAsc(organizationId)
            : billableMetricRepository.listScoped(
                organizationId, BusinessEnvironmentContext.environmentId()));
    if (billableMetrics.isEmpty()) {
      return List.of();
    }

    OffsetDateTime start = BillingMonth.start(month);
    OffsetDateTime end = aggregationEnd(month, cutoff);

    List<Customer> customers =
        (BusinessEnvironmentContext.environmentId() == null
                ? customerRepository.findByOrganizationIdOrderByNameAscIdAsc(organizationId)
                : customerRepository.listScoped(
                    organizationId, BusinessEnvironmentContext.environmentId()))
            .stream()
                .filter(customer -> customerIds == null || customerIds.contains(customer.getId()))
                .toList();

    var periods =
        (BusinessEnvironmentContext.environmentId() == null
            ? billableMetricPeriodRepository.findByOrganizationId(organizationId)
            : billableMetricPeriodRepository.listScoped(
                organizationId, BusinessEnvironmentContext.environmentId()));
    String period = month.toString();
    return billableMetrics.stream()
        .filter(
            metric ->
                (metric.getArchivedAt() == null && metric.getActiveFrom().compareTo(period) <= 0)
                    || periods.stream()
                        .anyMatch(
                            interval ->
                                interval.getBillableMetricCode().equals(metric.getCode())
                                    && interval.includes(period)))
        .map(
            billableMetric ->
                aggregate(billableMetric, customers, start, end, receivedBefore, customerIds))
        .toList();
  }

  private BillableMetricUsage aggregate(
      BillableMetric billableMetric,
      List<Customer> customers,
      OffsetDateTime start,
      OffsetDateTime end,
      OffsetDateTime receivedBefore,
      Collection<UUID> customerIds) {
    requireSupported(billableMetric);

    var quantities =
        billableMetricUsageRepository.aggregate(
            billableMetric, start, end, receivedBefore, customerIds);
    List<CustomerUsage> customerUsages =
        customers.stream()
            .map(
                customer -> {
                  DimensionUsage total =
                      quantities
                          .totalByCustomerId()
                          .getOrDefault(
                              customer.getId(),
                              new DimensionUsage(java.util.Map.of(), BigDecimal.ZERO));
                  return new CustomerUsage(
                      customer.getId(),
                      customer.getName(),
                      total.quantity(),
                      quantities
                          .dimensionUsagesByCustomerId()
                          .getOrDefault(customer.getId(), List.of()),
                      total.rawQuantity(),
                      total.rawQuantityNumerator(),
                      total.rawQuantityDenominator());
                })
            .toList();

    return new BillableMetricUsage(billableMetric, customerUsages);
  }

  private void requireSupported(BillableMetric billableMetric) {
    if (!MetricAggregation.SUPPORTED.contains(billableMetric.getAggregation()))
      throw new IllegalStateException(
          "metric %s uses unsupported aggregation %s"
              .formatted(billableMetric.getCode(), billableMetric.getAggregation()));
    if (!"count".equals(billableMetric.getAggregation())
        && (billableMetric.getTargetProperty() == null
            || billableMetric.getTargetProperty().isBlank()))
      throw new IllegalStateException(
          "metric %s has no target_property".formatted(billableMetric.getCode()));
  }
}
