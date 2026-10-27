package com.meterengine.metric.service;

import com.meterengine.customer.entity.Customer;
import com.meterengine.customer.repository.CustomerRepository;
import com.meterengine.metric.dto.BillableMetricUsage;
import com.meterengine.metric.dto.CustomerUsage;
import com.meterengine.metric.dto.DimensionUsage;
import com.meterengine.metric.entity.BillableMetric;
import com.meterengine.metric.entity.MetricAggregation;
import com.meterengine.metric.repository.BillableMetricRepository;
import com.meterengine.metric.repository.BillableMetricUsageRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillableMetricUsageService {

  public static final ZoneId BILLING_ZONE = ZoneId.of("Asia/Seoul");

  private final BillableMetricUsageRepository billableMetricUsageRepository;
  private final BillableMetricRepository billableMetricRepository;
  private final CustomerRepository customerRepository;

  BillableMetricUsageService(
      BillableMetricUsageRepository billableMetricUsageRepository,
      BillableMetricRepository billableMetricRepository,
      CustomerRepository customerRepository) {
    this.billableMetricUsageRepository = billableMetricUsageRepository;
    this.billableMetricRepository = billableMetricRepository;
    this.customerRepository = customerRepository;
  }

  public static YearMonth currentMonth() {
    return YearMonth.now(BILLING_ZONE);
  }

  public static OffsetDateTime aggregationEnd(YearMonth month, OffsetDateTime now) {
    OffsetDateTime monthEnd =
        month.plusMonths(1).atDay(1).atStartOfDay(BILLING_ZONE).toOffsetDateTime();
    return now.isBefore(monthEnd) ? now : monthEnd;
  }

  @Transactional(readOnly = true)
  public List<BillableMetricUsage> aggregate(UUID organizationId, YearMonth month) {
    List<BillableMetric> billableMetrics =
        billableMetricRepository.findByOrganizationIdOrderByCodeAsc(organizationId);
    if (billableMetrics.isEmpty()) {
      return List.of();
    }

    OffsetDateTime start = month.atDay(1).atStartOfDay(BILLING_ZONE).toOffsetDateTime();
    OffsetDateTime end = aggregationEnd(month, OffsetDateTime.now(BILLING_ZONE));

    List<Customer> customers =
        customerRepository.findByOrganizationIdOrderByNameAscIdAsc(organizationId);

    return billableMetrics.stream()
        .map(billableMetric -> aggregate(billableMetric, customers, start, end))
        .toList();
  }

  private BillableMetricUsage aggregate(
      BillableMetric billableMetric,
      List<Customer> customers,
      OffsetDateTime start,
      OffsetDateTime end) {
    requireSupported(billableMetric);

    var quantities = quantities(billableMetric, start, end);
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

  private BillableMetricUsageRepository.AggregatedQuantities quantities(
      BillableMetric metric, OffsetDateTime start, OffsetDateTime end) {
    if (metric.isSum()
        && metric.getUnitSize().compareTo(BigDecimal.ONE) == 0
        && "none".equals(metric.getRoundingMode())) {
      var dimensions =
          billableMetricUsageRepository.sumQuantityByCustomerIdAndDimensionValues(
              metric.getOrganizationId(),
              metric.getEventType(),
              metric.getTargetProperty(),
              metric.getDimensionProperties(),
              start,
              end);
      var totals = new java.util.HashMap<UUID, DimensionUsage>();
      dimensions.forEach(
          (customer, values) ->
              totals.put(
                  customer,
                  new DimensionUsage(
                      java.util.Map.of(),
                      values.stream()
                          .map(DimensionUsage::quantity)
                          .reduce(BigDecimal.ZERO, BigDecimal::add))));
      return new BillableMetricUsageRepository.AggregatedQuantities(totals, dimensions);
    }
    return billableMetricUsageRepository.aggregate(metric, start, end);
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
