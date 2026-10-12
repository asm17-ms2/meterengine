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
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvoiceFinalizationService {

  private static final long TAX_RATE_PERCENT = 10;

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

  // 도입사의 고객 중 그 달 청구 예정액이 0원이 아닌 고객을 확정한다.
  @Transactional
  public List<Invoice> finalize(UUID organizationId, YearMonth month) {
    return draftInvoiceService.preview(organizationId, month).customers().stream()
        .filter(draftInvoiceCustomer -> draftInvoiceCustomer.amount() != 0)
        .map(InvoiceFinalizationService::withoutZeroQuantityLines)
        .map(draftInvoiceCustomer -> save(organizationId, month, draftInvoiceCustomer))
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
            .toList());
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
