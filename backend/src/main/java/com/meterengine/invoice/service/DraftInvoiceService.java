package com.meterengine.invoice.service;

import com.meterengine.customer.entity.Customer;
import com.meterengine.customer.repository.CustomerRepository;
import com.meterengine.invoice.dto.DraftInvoiceResponse;
import com.meterengine.invoice.dto.DraftInvoiceResponse.DraftInvoiceCustomer;
import com.meterengine.invoice.dto.DraftInvoiceResponse.DraftInvoiceLine;
import com.meterengine.metric.dto.BillableMetricUsage;
import com.meterengine.metric.dto.CustomerUsage;
import com.meterengine.metric.service.BillableMetricUsageService;
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

  private final BillableMetricUsageService billableMetricUsageService;
  private final CustomerRepository customerRepository;
  private final PriceRateRepository priceRateRepository;

  DraftInvoiceService(
      BillableMetricUsageService billableMetricUsageService,
      CustomerRepository customerRepository,
      PriceRateRepository priceRateRepository) {
    this.billableMetricUsageService = billableMetricUsageService;
    this.customerRepository = customerRepository;
    this.priceRateRepository = priceRateRepository;
  }

  @Transactional(readOnly = true)
  public DraftInvoiceResponse preview(UUID organizationId, YearMonth month) {
    OffsetDateTime calculatedAt = OffsetDateTime.now(BillableMetricUsageService.BILLING_ZONE);

    List<Customer> organizationCustomers =
        customerRepository.findByOrganizationIdOrderByNameAscIdAsc(organizationId);

    Map<String, BigDecimal> baseUnitPrices = priceRateRepository.findBaseUnitPrices(organizationId);

    List<BillableMetricQuantitiesByCustomer> billableMetricQuantitiesByCustomers =
        billableMetricUsageService.aggregate(organizationId, month).stream()
            .filter(
                billableMetricUsage ->
                    baseUnitPrices.containsKey(billableMetricUsage.billableMetric().getCode()))
            .map(
                billableMetricUsage ->
                    BillableMetricQuantitiesByCustomer.from(billableMetricUsage, baseUnitPrices))
            .toList();

    List<DraftInvoiceCustomer> draftInvoiceCustomers =
        organizationCustomers.stream()
            .map(customer -> draftInvoiceCustomer(customer, billableMetricQuantitiesByCustomers))
            .toList();

    long totalAmount =
        draftInvoiceCustomers.stream()
            .mapToLong(DraftInvoiceCustomer::amount)
            .reduce(0L, Math::addExact);

    return new DraftInvoiceResponse(
        month.toString(), calculatedAt, totalAmount, draftInvoiceCustomers);
  }

  private static DraftInvoiceCustomer draftInvoiceCustomer(
      Customer customer,
      List<BillableMetricQuantitiesByCustomer> billableMetricQuantitiesByCustomers) {
    List<DraftInvoiceLine> draftInvoiceLines =
        billableMetricQuantitiesByCustomers.stream()
            .map(quantities -> quantities.toDraftInvoiceLine(customer.getId()))
            .toList();

    long amount =
        draftInvoiceLines.stream().mapToLong(DraftInvoiceLine::amount).reduce(0L, Math::addExact);

    return new DraftInvoiceCustomer(
        customer.getId(), customer.getName(), amount, draftInvoiceLines);
  }

  private record BillableMetricQuantitiesByCustomer(
      String billableMetricCode,
      String targetProperty,
      BigDecimal unitPrice,
      Map<UUID, BigDecimal> quantityByCustomerId) {

    static BillableMetricQuantitiesByCustomer from(
        BillableMetricUsage billableMetricUsage, Map<String, BigDecimal> baseUnitPrices) {
      String billableMetricCode = billableMetricUsage.billableMetric().getCode();

      return new BillableMetricQuantitiesByCustomer(
          billableMetricCode,
          billableMetricUsage.billableMetric().getTargetProperty(),
          baseUnitPrices.get(billableMetricCode),
          billableMetricUsage.customers().stream()
              .collect(Collectors.toMap(CustomerUsage::customerId, CustomerUsage::quantity)));
    }

    DraftInvoiceLine toDraftInvoiceLine(UUID customerId) {
      BigDecimal quantity = quantityByCustomerId.getOrDefault(customerId, BigDecimal.ZERO);

      return new DraftInvoiceLine(
          billableMetricCode, targetProperty, quantity, unitPrice, charge(quantity, unitPrice));
    }
  }

  private static long charge(BigDecimal quantity, BigDecimal unitPrice) {
    return quantity.multiply(unitPrice).setScale(0, RoundingMode.DOWN).longValueExact();
  }
}
