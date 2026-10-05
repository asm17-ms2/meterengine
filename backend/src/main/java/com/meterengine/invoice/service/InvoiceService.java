package com.meterengine.invoice.service;

import com.meterengine.customer.repository.CustomerRepository;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.NotFoundException;
import com.meterengine.invoice.dto.InvoiceResponse;
import com.meterengine.invoice.dto.ListInvoicesResponse;
import com.meterengine.invoice.repository.InvoiceRepository;
import com.meterengine.metric.service.BillableMetricUsageService;
import java.time.YearMonth;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvoiceService {

  private final InvoiceRepository invoiceRepository;
  private final CustomerRepository customerRepository;

  InvoiceService(InvoiceRepository invoiceRepository, CustomerRepository customerRepository) {
    this.invoiceRepository = invoiceRepository;
    this.customerRepository = customerRepository;
  }

  @Transactional(readOnly = true)
  public ListInvoicesResponse list(UUID organizationId, UUID customerId, YearMonth month) {
    if (customerId != null
        && !customerRepository.existsByOrganizationIdAndId(organizationId, customerId)) {
      throw new NotFoundException(ErrorCode.CUSTOMER_NOT_FOUND);
    }

    String period = month == null ? null : month.toString();
    return new ListInvoicesResponse(
        invoiceRepository.findWithCustomerName(organizationId, customerId, period).stream()
            .map(InvoiceService::toBillingZone)
            .toList());
  }

  private static InvoiceResponse toBillingZone(InvoiceResponse invoice) {
    return new InvoiceResponse(
        invoice.id(),
        invoice.customerId(),
        invoice.customerName(),
        invoice.month(),
        invoice
            .finalizedAt()
            .atZoneSameInstant(BillableMetricUsageService.BILLING_ZONE)
            .toOffsetDateTime(),
        invoice.totalAmount());
  }
}
