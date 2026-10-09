package com.meterengine.invoice.service;

import com.meterengine.invoice.dto.DraftInvoiceResponse.DraftInvoiceCustomer;
import com.meterengine.invoice.dto.DraftInvoiceResponse.DraftInvoiceLine;
import com.meterengine.invoice.entity.Invoice;
import com.meterengine.invoice.entity.InvoiceLine;
import com.meterengine.invoice.repository.InvoiceLineRepository;
import com.meterengine.invoice.repository.InvoiceRepository;
import com.meterengine.metric.service.BillableMetricUsageService;
import com.meterengine.pricing.entity.PriceRate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvoiceFinalizationService {

  public static final long TAX_RATE_PERCENT = 10;

  private final DraftInvoiceService draftInvoiceService;
  private final InvoiceRepository invoiceRepository;
  private final InvoiceLineRepository invoiceLineRepository;

  InvoiceFinalizationService(
      DraftInvoiceService draftInvoiceService,
      InvoiceRepository invoiceRepository,
      InvoiceLineRepository invoiceLineRepository) {
    this.draftInvoiceService = draftInvoiceService;
    this.invoiceRepository = invoiceRepository;
    this.invoiceLineRepository = invoiceLineRepository;
  }

  // (고객, 달) 하나를 확정한다.
  @Transactional
  public Invoice finalize(UUID organizationId, UUID customerId, YearMonth month) {
    DraftInvoiceCustomer draftInvoiceCustomer = preview(organizationId, customerId, month);

    long supplyAmount = draftInvoiceCustomer.amount();
    Invoice invoice =
        invoiceRepository.save(
            new Invoice(
                UUID.randomUUID(),
                organizationId,
                customerId,
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

  private DraftInvoiceCustomer preview(UUID organizationId, UUID customerId, YearMonth month) {
    return draftInvoiceService.preview(organizationId, month).customers().stream()
        .filter(draftInvoiceCustomer -> draftInvoiceCustomer.customerId().equals(customerId))
        .findFirst()
        .orElseThrow();
  }

  private static InvoiceLine toInvoiceLine(Invoice invoice, DraftInvoiceLine draftInvoiceLine) {
    return new InvoiceLine(
        UUID.randomUUID(),
        invoice.getOrganizationId(),
        invoice.getId(),
        draftInvoiceLine.billableMetricCode(),
        draftInvoiceLine.targetProperty(),
        PriceRate.BASE_DIMENSION_VALUES,
        draftInvoiceLine.quantity(),
        draftInvoiceLine.unitPrice(),
        draftInvoiceLine.amount());
  }
}
