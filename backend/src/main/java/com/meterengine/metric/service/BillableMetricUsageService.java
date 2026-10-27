package com.meterengine.metric.service;

import com.meterengine.customer.entity.Customer;
import com.meterengine.customer.repository.CustomerRepository;
import com.meterengine.metric.dto.BillableMetricUsage;
import com.meterengine.metric.dto.CustomerUsage;
import com.meterengine.metric.dto.DimensionUsage;
import com.meterengine.metric.entity.BillableMetric;
import com.meterengine.metric.repository.BillableMetricRepository;
import com.meterengine.metric.repository.BillableMetricUsageRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillableMetricUsageService {

  public static final ZoneId BILLING_ZONE = ZoneId.of("Asia/Seoul");

  private static final LocalTime CUTOFF_TIME = LocalTime.of(13, 0);

  private final BillableMetricUsageRepository billableMetricUsageRepository;
  private final BillableMetricRepository billableMetricRepository;
  private final CustomerRepository customerRepository;
  private final Clock clock;

  BillableMetricUsageService(
      BillableMetricUsageRepository billableMetricUsageRepository,
      BillableMetricRepository billableMetricRepository,
      CustomerRepository customerRepository,
      Clock clock) {
    this.billableMetricUsageRepository = billableMetricUsageRepository;
    this.billableMetricRepository = billableMetricRepository;
    this.customerRepository = customerRepository;
    this.clock = clock;
  }

  public static YearMonth currentMonth(Clock clock) {
    return YearMonth.now(clock.withZone(BILLING_ZONE));
  }

  public static OffsetDateTime aggregationEnd(YearMonth month, OffsetDateTime now) {
    OffsetDateTime monthEnd =
        month.plusMonths(1).atDay(1).atStartOfDay(BILLING_ZONE).toOffsetDateTime();
    return now.isBefore(monthEnd) ? now : monthEnd;
  }

  // 그 달 사용량의 집계 마감 시각. 이 시각 전에 받은 그 달 이벤트만 집계하고 청구한다.
  public static OffsetDateTime cutoffAt(YearMonth month) {
    return month.plusMonths(1).atDay(1).atTime(CUTOFF_TIME).atZone(BILLING_ZONE).toOffsetDateTime();
  }

  @Transactional(readOnly = true)
  public List<BillableMetricUsage> aggregate(UUID organizationId, YearMonth month) {
    return aggregate(organizationId, month, cutoffAt(month));
  }

  // 그 달에 발생했고 receivedBefore 전에 받은 이벤트를 집계한다.
  @Transactional(readOnly = true)
  public List<BillableMetricUsage> aggregate(
      UUID organizationId, YearMonth month, OffsetDateTime receivedBefore) {
    List<BillableMetric> billableMetrics =
        billableMetricRepository.findByOrganizationIdOrderByCodeAsc(organizationId);
    if (billableMetrics.isEmpty()) {
      return List.of();
    }

    OffsetDateTime start = month.atDay(1).atStartOfDay(BILLING_ZONE).toOffsetDateTime();
    OffsetDateTime end = aggregationEnd(month, OffsetDateTime.now(clock));

    List<Customer> customers =
        customerRepository.findByOrganizationIdOrderByNameAscIdAsc(organizationId);

    return billableMetrics.stream()
        .map(billableMetric -> aggregate(billableMetric, customers, start, end, receivedBefore))
        .toList();
  }

  private BillableMetricUsage aggregate(
      BillableMetric billableMetric,
      List<Customer> customers,
      OffsetDateTime start,
      OffsetDateTime end,
      OffsetDateTime receivedBefore) {
    requireSupported(billableMetric);

    Map<UUID, List<DimensionUsage>> dimensionUsagesByCustomerId =
        billableMetricUsageRepository.sumQuantityByCustomerIdAndDimensionValues(
            billableMetric.getOrganizationId(),
            billableMetric.getEventType(),
            billableMetric.getTargetProperty(),
            billableMetric.getDimensionProperties(),
            start,
            end,
            receivedBefore);

    List<CustomerUsage> customerUsages =
        customers.stream()
            .map(
                customer ->
                    toCustomerUsage(
                        customer,
                        dimensionUsagesByCustomerId.getOrDefault(customer.getId(), List.of())))
            .toList();

    return new BillableMetricUsage(billableMetric, customerUsages);
  }

  private static CustomerUsage toCustomerUsage(
      Customer customer, List<DimensionUsage> dimensionUsages) {
    BigDecimal quantity =
        dimensionUsages.stream()
            .map(DimensionUsage::quantity)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    return new CustomerUsage(customer.getId(), customer.getName(), quantity, dimensionUsages);
  }

  private void requireSupported(BillableMetric billableMetric) {
    if (!billableMetric.isSum()) {
      throw new IllegalStateException(
          "metric %s uses aggregation %s, which is not implemented yet (only sum)"
              .formatted(billableMetric.getCode(), billableMetric.getAggregation()));
    }
    if (billableMetric.getTargetProperty() == null
        || billableMetric.getTargetProperty().isBlank()) {
      throw new IllegalStateException(
          "metric %s aggregates with sum but has no target_property to sum"
              .formatted(billableMetric.getCode()));
    }
  }
}
