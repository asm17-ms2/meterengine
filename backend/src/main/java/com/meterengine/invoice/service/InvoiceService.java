package com.meterengine.invoice.service;

import com.meterengine.customer.repository.CustomerRepository;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.NotFoundException;
import com.meterengine.invoice.dto.InvoiceResponse;
import com.meterengine.invoice.dto.InvoiceResponse.InvoiceLineResponse;
import com.meterengine.invoice.dto.ListInvoicesResponse;
import com.meterengine.invoice.entity.Invoice;
import com.meterengine.invoice.entity.InvoiceLine;
import com.meterengine.invoice.repository.InvoiceLineRepository;
import com.meterengine.invoice.repository.InvoiceRepository;
import com.meterengine.metric.service.BillableMetricUsageService;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Service
public class InvoiceService {

  private static final TypeReference<Map<String, Object>> DIMENSION_VALUES_TYPE =
      new TypeReference<>() {};

  private final InvoiceRepository invoiceRepository;
  private final InvoiceLineRepository invoiceLineRepository;
  private final CustomerRepository customerRepository;
  private final JsonMapper jsonMapper;

  InvoiceService(
      InvoiceRepository invoiceRepository,
      InvoiceLineRepository invoiceLineRepository,
      CustomerRepository customerRepository,
      JsonMapper jsonMapper) {
    this.invoiceRepository = invoiceRepository;
    this.invoiceLineRepository = invoiceLineRepository;
    this.customerRepository = customerRepository;
    this.jsonMapper = jsonMapper;
  }

  @Transactional(readOnly = true)
  public ListInvoicesResponse list(UUID organizationId, UUID customerId, YearMonth month) {
    if (customerId != null
        && !customerRepository.existsByOrganizationIdAndId(organizationId, customerId)) {
      throw new NotFoundException(ErrorCode.CUSTOMER_NOT_FOUND);
    }

    String period = month == null ? null : month.toString();
    List<Invoice> invoices = invoiceRepository.findNewestFirst(organizationId, customerId, period);
    Map<UUID, List<InvoiceLine>> invoiceLinesByInvoiceId =
        invoiceLineRepository
            .findByOrganizationIdAndInvoiceIdInOrderByBillableMetricCodeAscDimensionValuesAsc(
                organizationId, invoices.stream().map(Invoice::getId).toList())
            .stream()
            .collect(Collectors.groupingBy(InvoiceLine::getInvoiceId));

    return new ListInvoicesResponse(
        invoices.stream()
            .map(
                invoice ->
                    toInvoiceResponse(
                        invoice, invoiceLinesByInvoiceId.getOrDefault(invoice.getId(), List.of())))
            .toList());
  }

  @Transactional(readOnly = true)
  public InvoiceResponse get(UUID organizationId, UUID id) {
    Invoice invoice =
        invoiceRepository
            .findByOrganizationIdAndId(organizationId, id)
            .orElseThrow(() -> new NotFoundException(ErrorCode.INVOICE_NOT_FOUND));
    return toInvoiceResponse(
        invoice,
        invoiceLineRepository
            .findByOrganizationIdAndInvoiceIdOrderByBillableMetricCodeAscDimensionValuesAsc(
                organizationId, id));
  }

  private InvoiceResponse toInvoiceResponse(Invoice invoice, List<InvoiceLine> invoiceLines) {
    return new InvoiceResponse(
        invoice.getId(),
        invoice.getCustomerId(),
        invoice.getCustomerName(),
        invoice.getPeriod(),
        invoice
            .getFinalizedAt()
            .atZoneSameInstant(BillableMetricUsageService.BILLING_ZONE)
            .toOffsetDateTime(),
        invoice.getSupplyAmount(),
        invoice.getTaxAmount(),
        invoice.getTotalAmount(),
        invoiceLines.stream().map(this::toInvoiceLineResponse).toList());
  }

  private InvoiceLineResponse toInvoiceLineResponse(InvoiceLine invoiceLine) {
    return new InvoiceLineResponse(
        invoiceLine.getBillableMetricCode(),
        invoiceLine.getTargetProperty(),
        jsonMapper.readValue(invoiceLine.getDimensionValues(), DIMENSION_VALUES_TYPE),
        invoiceLine.getQuantity(),
        invoiceLine.getUnitPrice(),
        invoiceLine.getAmount());
  }
}
