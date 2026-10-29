package com.meterengine.invoice.service;

import com.meterengine.customer.entity.Customer;
import com.meterengine.customer.repository.CustomerRepository;
import com.meterengine.invoice.dto.DraftInvoiceResponse;
import com.meterengine.invoice.dto.DraftInvoiceResponse.DraftInvoiceCustomer;
import com.meterengine.invoice.dto.DraftInvoiceResponse.DraftInvoiceLine;
import com.meterengine.invoice.dto.DraftInvoiceResponse.DraftInvoiceNegativeUsage;
import com.meterengine.invoice.dto.DraftInvoiceResponse.DraftInvoiceUnpricedUsage;
import com.meterengine.invoice.repository.InvoiceRepository;
import com.meterengine.metric.service.BillableMetricUsageService;
import com.meterengine.pricing.dto.CustomerCharge;
import com.meterengine.pricing.dto.DimensionCharge;
import com.meterengine.pricing.dto.NegativeUsage;
import com.meterengine.pricing.dto.UnpricedUsage;
import com.meterengine.pricing.service.ChargeService;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DraftInvoiceService {

  private final BillableMetricUsageService billableMetricUsageService;
  private final CustomerRepository customerRepository;
  private final ChargeService chargeService;
  private final InvoiceRepository invoiceRepository;

  DraftInvoiceService(
      BillableMetricUsageService billableMetricUsageService,
      CustomerRepository customerRepository,
      ChargeService chargeService,
      InvoiceRepository invoiceRepository) {
    this.billableMetricUsageService = billableMetricUsageService;
    this.customerRepository = customerRepository;
    this.chargeService = chargeService;
    this.invoiceRepository = invoiceRepository;
  }

  @Transactional(readOnly = true)
  public DraftInvoiceResponse preview(UUID organizationId, YearMonth month) {
    OffsetDateTime calculatedAt = OffsetDateTime.now(BillableMetricUsageService.BILLING_ZONE);

    Set<UUID> finalizedCustomerIds =
        Set.copyOf(invoiceRepository.findCustomerIds(organizationId, month.toString()));
    List<Customer> organizationCustomers =
        customerRepository.findByOrganizationIdOrderByNameAscIdAsc(organizationId).stream()
            .filter(customer -> !finalizedCustomerIds.contains(customer.getId()))
            .toList();

    Map<UUID, CustomerCharge> customerChargeByCustomerId =
        chargeService.calculate(
            organizationId, month, billableMetricUsageService.aggregate(organizationId, month));

    List<DraftInvoiceCustomer> draftInvoiceCustomers =
        organizationCustomers.stream()
            .map(
                customer ->
                    toDraftInvoiceCustomer(
                        customer,
                        customerChargeByCustomerId.getOrDefault(
                            customer.getId(),
                            new CustomerCharge(
                                customer.getId(), 0, List.of(), List.of(), List.of()))))
            .toList();

    long totalAmount =
        draftInvoiceCustomers.stream()
            .mapToLong(DraftInvoiceCustomer::amount)
            .reduce(0L, Math::addExact);

    return new DraftInvoiceResponse(
        month.toString(), calculatedAt, totalAmount, draftInvoiceCustomers);
  }

  private static DraftInvoiceCustomer toDraftInvoiceCustomer(
      Customer customer, CustomerCharge customerCharge) {
    return new DraftInvoiceCustomer(
        customer.getId(),
        customer.getName(),
        customerCharge.amount(),
        customerCharge.dimensionCharges().stream()
            .map(DraftInvoiceService::toDraftInvoiceLine)
            .toList(),
        customerCharge.unpricedUsages().stream()
            .map(DraftInvoiceService::toDraftInvoiceUnpricedUsage)
            .toList(),
        customerCharge.negativeUsages().stream()
            .map(DraftInvoiceService::toDraftInvoiceNegativeUsage)
            .toList());
  }

  private static DraftInvoiceLine toDraftInvoiceLine(DimensionCharge dimensionCharge) {
    return new DraftInvoiceLine(
        dimensionCharge.billableMetricCode(),
        dimensionCharge.targetProperty(),
        dimensionCharge.dimensionValues(),
        dimensionCharge.quantity(),
        dimensionCharge.unitPrice(),
        dimensionCharge.amount(),
        dimensionCharge.lineType(),
        dimensionCharge.tierIndex(),
        dimensionCharge.quantityNumerator(),
        dimensionCharge.quantityDenominator());
  }

  private static DraftInvoiceUnpricedUsage toDraftInvoiceUnpricedUsage(
      UnpricedUsage unpricedUsage) {
    return new DraftInvoiceUnpricedUsage(
        unpricedUsage.billableMetricCode(),
        unpricedUsage.targetProperty(),
        unpricedUsage.dimensionValues(),
        unpricedUsage.quantity());
  }

  private static DraftInvoiceNegativeUsage toDraftInvoiceNegativeUsage(
      NegativeUsage negativeUsage) {
    return new DraftInvoiceNegativeUsage(
        negativeUsage.billableMetricCode(),
        negativeUsage.targetProperty(),
        negativeUsage.dimensionValues(),
        negativeUsage.quantity());
  }
}
