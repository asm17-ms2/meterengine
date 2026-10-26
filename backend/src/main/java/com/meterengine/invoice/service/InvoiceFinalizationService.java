package com.meterengine.invoice.service;

import com.meterengine.customer.entity.Customer;
import com.meterengine.customer.repository.CustomerRepository;
import com.meterengine.invoice.dto.DraftInvoiceResponse.DraftInvoiceCustomer;
import com.meterengine.invoice.dto.DraftInvoiceResponse.DraftInvoiceLine;
import com.meterengine.invoice.entity.Invoice;
import com.meterengine.invoice.entity.InvoiceLine;
import com.meterengine.invoice.repository.InvoiceLineRepository;
import com.meterengine.invoice.repository.InvoiceRepository;
import com.meterengine.metric.service.BillableMetricUsageService;
import com.meterengine.organization.repository.OrganizationRepository;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
public class InvoiceFinalizationService {

  private static final long TAX_RATE_PERCENT = 10;

  private final DraftInvoiceService draftInvoiceService;
  private final InvoiceRepository invoiceRepository;
  private final InvoiceLineRepository invoiceLineRepository;
  private final CustomerRepository customerRepository;
  private final OrganizationRepository organizationRepository;
  private final InvoiceEmailService invoiceEmailService;
  private final JsonMapper jsonMapper;

  InvoiceFinalizationService(
      DraftInvoiceService draftInvoiceService,
      InvoiceRepository invoiceRepository,
      InvoiceLineRepository invoiceLineRepository,
      CustomerRepository customerRepository,
      OrganizationRepository organizationRepository,
      InvoiceEmailService invoiceEmailService,
      JsonMapper jsonMapper) {
    this.draftInvoiceService = draftInvoiceService;
    this.invoiceRepository = invoiceRepository;
    this.invoiceLineRepository = invoiceLineRepository;
    this.customerRepository = customerRepository;
    this.organizationRepository = organizationRepository;
    this.invoiceEmailService = invoiceEmailService;
    this.jsonMapper = jsonMapper;
  }

  // 도입사의 고객 중 그 달 청구 예정액이 0원이 아닌 고객을 확정하고, 고객마다 청구 메일을 남긴다.
  @Transactional
  public List<Invoice> finalize(UUID organizationId, YearMonth month) {
    List<DraftInvoiceCustomer> draftInvoiceCustomers =
        draftInvoiceService.preview(organizationId, month).customers().stream()
            .filter(draftInvoiceCustomer -> draftInvoiceCustomer.amount() != 0)
            .map(InvoiceFinalizationService::withoutZeroQuantityLines)
            .toList();
    if (draftInvoiceCustomers.isEmpty()) {
      return List.of();
    }

    String organizationName =
        organizationRepository.findById(organizationId).orElseThrow().getName();
    Map<UUID, Customer> customerById =
        customerRepository.findByOrganizationIdOrderByNameAscIdAsc(organizationId).stream()
            .collect(Collectors.toMap(Customer::getId, Function.identity()));

    return draftInvoiceCustomers.stream()
        .map(
            draftInvoiceCustomer -> {
              Invoice invoice = save(organizationId, month, draftInvoiceCustomer);
              invoiceEmailService.record(
                  organizationName,
                  invoice,
                  draftInvoiceCustomer.lines(),
                  customerById.get(draftInvoiceCustomer.customerId()).getBillingEmail());
              return invoice;
            })
        .toList();
  }

  private Invoice save(
      UUID organizationId, YearMonth month, DraftInvoiceCustomer draftInvoiceCustomer) {
    long supplyAmount = draftInvoiceCustomer.amount();
    Invoice invoice =
        invoiceRepository.save(
            new Invoice(
                UUID.randomUUID(),
                organizationId,
                draftInvoiceCustomer.customerId(),
                draftInvoiceCustomer.customerName(),
                month.toString(),
                supplyAmount,
                Math.multiplyExact(supplyAmount, TAX_RATE_PERCENT) / 100,
                OffsetDateTime.now(BillableMetricUsageService.BILLING_ZONE)));

    invoiceLineRepository.saveAll(
        draftInvoiceCustomer.lines().stream()
            .map(draftInvoiceLine -> toInvoiceLine(invoice, draftInvoiceLine))
            .toList());

    return invoice;
  }

  private static DraftInvoiceCustomer withoutZeroQuantityLines(
      DraftInvoiceCustomer draftInvoiceCustomer) {
    return new DraftInvoiceCustomer(
        draftInvoiceCustomer.customerId(),
        draftInvoiceCustomer.customerName(),
        draftInvoiceCustomer.amount(),
        draftInvoiceCustomer.lines().stream()
            .filter(draftInvoiceLine -> draftInvoiceLine.quantity().signum() != 0)
            .toList(),
        draftInvoiceCustomer.unpricedUsages(),
        draftInvoiceCustomer.negativeUsages());
  }

  private InvoiceLine toInvoiceLine(Invoice invoice, DraftInvoiceLine draftInvoiceLine) {
    return new InvoiceLine(
        UUID.randomUUID(),
        invoice.getOrganizationId(),
        invoice.getId(),
        draftInvoiceLine.billableMetricCode(),
        draftInvoiceLine.targetProperty(),
        jsonMapper.writeValueAsString(draftInvoiceLine.dimensionValues()),
        draftInvoiceLine.quantity(),
        draftInvoiceLine.unitPrice(),
        draftInvoiceLine.amount());
  }
}
