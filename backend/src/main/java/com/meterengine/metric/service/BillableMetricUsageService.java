package com.meterengine.metric.service;

import com.meterengine.customer.entity.Customer;
import com.meterengine.customer.repository.CustomerRepository;
import com.meterengine.metric.dto.BillableMetricUsage;
import com.meterengine.metric.dto.CustomerUsage;
import com.meterengine.metric.entity.BillableMetric;
import com.meterengine.metric.repository.BillableMetricRepository;
import com.meterengine.metric.repository.BillableMetricUsageRepository;
import java.math.BigDecimal;
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

  @Transactional(readOnly = true)
  public List<BillableMetricUsage> aggregate(UUID organizationId, YearMonth month) {
    List<BillableMetric> billableMetrics =
        billableMetricRepository.findByOrganizationIdOrderByCodeAsc(organizationId);
    if (billableMetrics.isEmpty()) {
      return List.of();
    }

    OffsetDateTime start = month.atDay(1).atStartOfDay(BILLING_ZONE).toOffsetDateTime();
    OffsetDateTime end = month.plusMonths(1).atDay(1).atStartOfDay(BILLING_ZONE).toOffsetDateTime();

    List<Customer> customers =
        customerRepository.findByOrganizationIdOrderByNameAscIdAsc(organizationId);

    return billableMetrics.stream()
        .map(billableMetric -> aggregateBillableMetric(billableMetric, customers, start, end))
        .toList();
  }

  private BillableMetricUsage aggregateBillableMetric(
      BillableMetric billableMetric,
      List<Customer> customers,
      OffsetDateTime start,
      OffsetDateTime end) {
    requireSupported(billableMetric);

    Map<UUID, BigDecimal> quantityByCustomerId =
        billableMetricUsageRepository.sumQuantityByCustomerId(
            billableMetric.getOrganizationId(),
            billableMetric.getEventType(),
            billableMetric.getTargetProperty(),
            start,
            end);

    List<CustomerUsage> customerUsages =
        customers.stream()
            .map(
                customer ->
                    new CustomerUsage(
                        customer.getId(),
                        customer.getName(),
                        quantityByCustomerId.getOrDefault(customer.getId(), BigDecimal.ZERO)))
            .toList();

    return new BillableMetricUsage(billableMetric, customerUsages);
  }

  private void requireSupported(BillableMetric billableMetric) {
    if (!billableMetric.isSum()) {
      throw new IllegalStateException(
          "metric %s uses aggregation %s, which is not implemented yet (only SUM)"
              .formatted(billableMetric.getCode(), billableMetric.getAggregation()));
    }
    if (billableMetric.getTargetProperty() == null
        || billableMetric.getTargetProperty().isBlank()) {
      throw new IllegalStateException(
          "metric %s aggregates with SUM but has no target_property to sum"
              .formatted(billableMetric.getCode()));
    }
  }
}
