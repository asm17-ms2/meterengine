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
    Map<String, BigDecimal> baseUnitPrices = priceRateRepository.findBaseUnitPrices(organizationId);
    List<MetricQuantitiesByCustomer> metricQuantitiesByCustomers =
        metricUsageService.aggregate(organizationId, month).stream()
            .filter(usage -> baseUnitPrices.containsKey(usage.metric().getCode()))
            .map(usage -> MetricQuantitiesByCustomer.from(usage, baseUnitPrices))
            .toList();

    List<DraftInvoiceCustomerEntry> customerEntries =
        organizationCustomers.stream()
            .map(customer -> customerEntry(customer, metricQuantitiesByCustomers))
            .toList();
    long totalAmount =
        customerEntries.stream()
            .mapToLong(DraftInvoiceCustomerEntry::amount)
            .reduce(0L, Math::addExact);

    return new DraftInvoiceResponse(month.toString(), calculatedAt, totalAmount, customerEntries);
  }

  private static DraftInvoiceCustomerEntry customerEntry(
      Customer customer, List<MetricQuantitiesByCustomer> metricQuantitiesByCustomers) {
    List<MetricLineItem> metricLineItems =
        metricQuantitiesByCustomers.stream()
            .map(metric -> metric.lineFor(customer.getId()))
            .toList();
    long amount =
        metricLineItems.stream().mapToLong(MetricLineItem::amount).reduce(0L, Math::addExact);
    return new DraftInvoiceCustomerEntry(
        customer.getId(), customer.getName(), amount, metricLineItems);
  }

  private record MetricQuantitiesByCustomer(
      String metricCode,
      String targetProperty,
      BigDecimal unitPrice,
      Map<UUID, BigDecimal> byCustomer) {

    static MetricQuantitiesByCustomer from(
        MetricUsage metricUsage, Map<String, BigDecimal> unitPrices) {
      String metricCode = metricUsage.metric().getCode();
      return new MetricQuantitiesByCustomer(
          metricCode,
          metricUsage.metric().getTargetProperty(),
          unitPrices.get(metricCode),
          metricUsage.customers().stream()
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
