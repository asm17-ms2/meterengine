package com.meterengine.invoice.service;

import com.meterengine.customer.entity.Customer;
import com.meterengine.customer.repository.CustomerRepository;
import com.meterengine.invoice.dto.DraftInvoiceResponse;
import com.meterengine.invoice.dto.DraftInvoiceResponse.CustomerEntry;
import com.meterengine.invoice.dto.DraftInvoiceResponse.LineEntry;
import com.meterengine.metric.dto.CustomerUsage;
import com.meterengine.metric.dto.MetricUsage;
import com.meterengine.metric.service.MetricUsageService;
import com.meterengine.pricing.repository.PriceRateRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DraftInvoiceService {

  private final MetricUsageService aggregation;
  private final CustomerRepository customers;
  private final PriceRateRepository prices;

  DraftInvoiceService(
      MetricUsageService aggregation, CustomerRepository customers, PriceRateRepository prices) {
    this.aggregation = aggregation;
    this.customers = customers;
    this.prices = prices;
  }

  @Transactional(readOnly = true)
  public DraftInvoiceResponse preview(UUID organizationId, YearMonth month) {
    OffsetDateTime calculatedAt = OffsetDateTime.now(MetricUsageService.BILLING_ZONE);

    List<Customer> organizationCustomers =
        customers.findByOrganizationIdOrderByNameAscIdAsc(organizationId);
    Map<String, BigDecimal> unitPrices = prices.findBaseUnitPrices(organizationId);
    List<MetricQuantities> metricQuantities =
        aggregation.aggregate(organizationId, month).stream()
            .filter(usage -> unitPrices.containsKey(usage.metric().getCode()))
            .map(usage -> MetricQuantities.from(usage, unitPrices))
            .toList();

    List<CustomerEntry> entries =
        organizationCustomers.stream()
            .map(customer -> customerEntry(customer, metricQuantities))
            .toList();
    long totalAmount = entries.stream().mapToLong(CustomerEntry::amount).reduce(0L, Math::addExact);

    return new DraftInvoiceResponse(month.toString(), calculatedAt, totalAmount, entries);
  }

  private static CustomerEntry customerEntry(
      Customer customer, List<MetricQuantities> metricQuantities) {
    List<LineEntry> lines =
        metricQuantities.stream().map(metric -> metric.lineFor(customer.getId())).toList();
    long amount = lines.stream().mapToLong(LineEntry::amount).reduce(0L, Math::addExact);
    return new CustomerEntry(customer.getId(), customer.getName(), amount, lines);
  }

  private record MetricQuantities(
      String metricCode,
      String targetProperty,
      BigDecimal unitPrice,
      Map<UUID, BigDecimal> byCustomer) {

    static MetricQuantities from(MetricUsage usage, Map<String, BigDecimal> unitPrices) {
      String metricCode = usage.metric().getCode();
      return new MetricQuantities(
          metricCode,
          usage.metric().getTargetProperty(),
          unitPrices.get(metricCode),
          usage.customers().stream()
              .collect(Collectors.toMap(CustomerUsage::customerId, CustomerUsage::quantity)));
    }

    LineEntry lineFor(UUID customerId) {
      BigDecimal quantity = byCustomer.getOrDefault(customerId, BigDecimal.ZERO);
      return new LineEntry(
          metricCode, targetProperty, quantity, unitPrice, charge(quantity, unitPrice));
    }
  }

  private static long charge(BigDecimal quantity, BigDecimal unitPrice) {
    return quantity.multiply(unitPrice).setScale(0, RoundingMode.DOWN).longValueExact();
  }
}
