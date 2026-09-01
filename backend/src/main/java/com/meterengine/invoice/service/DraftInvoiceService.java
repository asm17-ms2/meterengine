package com.meterengine.invoice.service;

import com.meterengine.customer.entity.Customer;
import com.meterengine.customer.repository.CustomerRepository;
import com.meterengine.invoice.dto.DraftInvoiceResponse;
import com.meterengine.invoice.dto.DraftInvoiceResponse.DraftInvoiceCustomerEntry;
import com.meterengine.invoice.dto.DraftInvoiceResponse.MetricLineItem;
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

  private final MetricUsageService metricUsageService;
  private final CustomerRepository customerRepository;
  private final PriceRateRepository priceRateRepository;

  DraftInvoiceService(
      MetricUsageService metricUsageService,
      CustomerRepository customerRepository,
      PriceRateRepository priceRateRepository) {
    this.metricUsageService = metricUsageService;
    this.customerRepository = customerRepository;
    this.priceRateRepository = priceRateRepository;
  }

  @Transactional(readOnly = true)
  public DraftInvoiceResponse preview(UUID organizationId, YearMonth month) {
    OffsetDateTime calculatedAt = OffsetDateTime.now(MetricUsageService.BILLING_ZONE);

    List<Customer> organizationCustomers =
        customerRepository.findByOrganizationIdOrderByNameAscIdAsc(organizationId);
    Map<String, BigDecimal> unitPrices = priceRateRepository.findBaseUnitPrices(organizationId);
    List<MetricQuantities> metricQuantities =
        metricUsageService.aggregate(organizationId, month).stream()
            .filter(usage -> unitPrices.containsKey(usage.metric().getCode()))
            .map(usage -> MetricQuantities.from(usage, unitPrices))
            .toList();

    List<DraftInvoiceCustomerEntry> entries =
        organizationCustomers.stream()
            .map(customer -> customerEntry(customer, metricQuantities))
            .toList();
    long totalAmount =
        entries.stream().mapToLong(DraftInvoiceCustomerEntry::amount).reduce(0L, Math::addExact);

    return new DraftInvoiceResponse(month.toString(), calculatedAt, totalAmount, entries);
  }

  private static DraftInvoiceCustomerEntry customerEntry(
      Customer customer, List<MetricQuantities> metricQuantities) {
    List<MetricLineItem> lines =
        metricQuantities.stream().map(metric -> metric.lineFor(customer.getId())).toList();
    long amount = lines.stream().mapToLong(MetricLineItem::amount).reduce(0L, Math::addExact);
    return new DraftInvoiceCustomerEntry(customer.getId(), customer.getName(), amount, lines);
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

    MetricLineItem lineFor(UUID customerId) {
      BigDecimal quantity = byCustomer.getOrDefault(customerId, BigDecimal.ZERO);
      return new MetricLineItem(
          metricCode, targetProperty, quantity, unitPrice, charge(quantity, unitPrice));
    }
  }

  private static long charge(BigDecimal quantity, BigDecimal unitPrice) {
    return quantity.multiply(unitPrice).setScale(0, RoundingMode.DOWN).longValueExact();
  }
}
