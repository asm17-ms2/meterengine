package com.meterengine.invoice.service;

import com.meterengine.audit.service.AuditLogService;
import com.meterengine.customer.entity.Customer;
import com.meterengine.customer.repository.CustomerRepository;
import com.meterengine.global.security.BusinessEnvironmentContext;
import com.meterengine.invoice.dto.DraftInvoiceResponse.DraftInvoiceCustomer;
import com.meterengine.invoice.dto.DraftInvoiceResponse.DraftInvoiceLine;
import com.meterengine.invoice.dto.InvoiceFinalizationResult;
import com.meterengine.invoice.entity.Invoice;
import com.meterengine.invoice.entity.InvoiceLine;
import com.meterengine.invoice.repository.InvoiceLineRepository;
import com.meterengine.invoice.repository.InvoiceRepository;
import com.meterengine.metric.entity.BillableMetric;
import com.meterengine.metric.repository.BillableMetricRepository;
import com.meterengine.metric.service.BillableMetricUsageService;
import com.meterengine.organization.repository.OrganizationRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

@Service
public class InvoiceFinalizationService {

  private static final Logger logger = LoggerFactory.getLogger(InvoiceFinalizationService.class);

  private static final long TAX_RATE_PERCENT = 10;

  private final DraftInvoiceService draftInvoiceService;
  private final InvoiceRepository invoiceRepository;
  private final InvoiceLineRepository invoiceLineRepository;
  private final CustomerRepository customerRepository;
  private final OrganizationRepository organizationRepository;
  private final InvoiceEmailService invoiceEmailService;
  private final JsonMapper jsonMapper;
  private final AuditLogService auditLogService;
  private final BillableMetricRepository billableMetricRepository;
  private final TransactionTemplate transactionTemplate;
  private final Clock clock;

  InvoiceFinalizationService(
      DraftInvoiceService draftInvoiceService,
      InvoiceRepository invoiceRepository,
      InvoiceLineRepository invoiceLineRepository,
      CustomerRepository customerRepository,
      OrganizationRepository organizationRepository,
      InvoiceEmailService invoiceEmailService,
      JsonMapper jsonMapper,
      AuditLogService auditLogService,
      BillableMetricRepository billableMetricRepository,
      TransactionTemplate transactionTemplate,
      Clock clock) {
    this.draftInvoiceService = draftInvoiceService;
    this.invoiceRepository = invoiceRepository;
    this.invoiceLineRepository = invoiceLineRepository;
    this.customerRepository = customerRepository;
    this.organizationRepository = organizationRepository;
    this.invoiceEmailService = invoiceEmailService;
    this.jsonMapper = jsonMapper;
    this.auditLogService = auditLogService;
    this.billableMetricRepository = billableMetricRepository;
    this.transactionTemplate = transactionTemplate;
    this.clock = clock;
  }

  // 그 달 청구 예정액이 0원이 아닌 고객을 마감 시각까지 받은 이벤트로 확정한다.
  // 마감 시각 전이면 지금을 마감으로 쓰고, 고객마다 따로 커밋해 한 고객의 실패가 다른 고객을 되돌리지 않는다.
  public InvoiceFinalizationResult finalize(UUID organizationId, YearMonth month) {
    OffsetDateTime now =
        OffsetDateTime.now(clock.withZone(BillableMetricUsageService.BILLING_ZONE));
    OffsetDateTime monthCutoffAt = BillableMetricUsageService.cutoffAt(month);
    OffsetDateTime cutoffAt = now.isBefore(monthCutoffAt) ? now : monthCutoffAt;

    Targets targets =
        transactionTemplate.execute(status -> targets(organizationId, month, cutoffAt));
    List<Invoice> invoices = new ArrayList<>();
    int skippedCount = 0;
    int failedCount = 0;
    for (DraftInvoiceCustomer draftInvoiceCustomer : targets.draftInvoiceCustomers()) {
      try {
        Optional<Invoice> invoice =
            transactionTemplate.execute(
                status ->
                    finalizeCustomer(
                        organizationId,
                        targets.organizationName(),
                        month,
                        cutoffAt,
                        now,
                        draftInvoiceCustomer,
                        targets.billingEmailByCustomerId().get(draftInvoiceCustomer.customerId()),
                        targets.metricNameByCode()));
        if (invoice.isPresent()) {
          invoices.add(invoice.get());
        } else {
          skippedCount++;
        }
      } catch (RuntimeException exception) {
        failedCount++;
        logger.error(
            "invoice finalization failed: organization={}, environment={}, customer={}, month={}",
            organizationId,
            BusinessEnvironmentContext.mode(),
            draftInvoiceCustomer.customerId(),
            month,
            exception);
      }
    }
    return new InvoiceFinalizationResult(List.copyOf(invoices), skippedCount, failedCount);
  }

  // 환경이 있으면 도입사 행을 잠근 채 계산해, 같은 행을 잠그는 확정과 단가, 미터 변경이 계산 도중에 끼지 않는다.
  private Targets targets(UUID organizationId, YearMonth month, OffsetDateTime cutoffAt) {
    UUID environmentId = BusinessEnvironmentContext.environmentId();
    if (environmentId != null)
      organizationRepository.findForUpdateById(organizationId).orElseThrow();
    List<DraftInvoiceCustomer> draftInvoiceCustomers =
        draftInvoiceService.calculate(organizationId, month, cutoffAt).stream()
            .filter(draftInvoiceCustomer -> draftInvoiceCustomer.amount() != 0)
            .toList();
    if (draftInvoiceCustomers.isEmpty()) {
      return new Targets(null, List.of(), Map.of(), Map.of());
    }

    Map<UUID, String> billingEmailByCustomerId = new HashMap<>();
    for (Customer customer :
        environmentId == null
            ? customerRepository.findByOrganizationIdOrderByNameAscIdAsc(organizationId)
            : customerRepository.listScoped(organizationId, environmentId)) {
      billingEmailByCustomerId.put(customer.getId(), customer.getBillingEmail());
    }
    Map<String, String> metricNameByCode =
        (environmentId == null
                ? billableMetricRepository.findByOrganizationIdOrderByCodeAsc(organizationId)
                : billableMetricRepository.listScoped(organizationId, environmentId))
            .stream().collect(Collectors.toMap(BillableMetric::getCode, BillableMetric::getName));
    return new Targets(
        organizationRepository.findById(organizationId).orElseThrow().getName(),
        draftInvoiceCustomers,
        billingEmailByCustomerId,
        metricNameByCode);
  }

  // 그 고객과 달의 인보이스가 이미 있으면 아무것도 남기지 않는다.
  private Optional<Invoice> finalizeCustomer(
      UUID organizationId,
      String organizationName,
      YearMonth month,
      OffsetDateTime cutoffAt,
      OffsetDateTime finalizedAt,
      DraftInvoiceCustomer draftInvoiceCustomer,
      String billingEmail,
      Map<String, String> metricNameByCode) {
    DraftInvoiceCustomer withoutZeroQuantityLines = withoutZeroQuantityLines(draftInvoiceCustomer);
    long supplyAmount = withoutZeroQuantityLines.amount();
    Invoice invoice =
        new Invoice(
            UUID.randomUUID(),
            organizationId,
            withoutZeroQuantityLines.customerId(),
            withoutZeroQuantityLines.customerName(),
            month.toString(),
            supplyAmount,
            Math.multiplyExact(supplyAmount, TAX_RATE_PERCENT) / 100,
            cutoffAt,
            finalizedAt,
            BusinessEnvironmentContext.environmentId());
    if (invoiceRepository.insertIfAbsent(invoice) == 0) {
      return Optional.empty();
    }

    invoiceLineRepository.saveAll(
        withoutZeroQuantityLines.lines().stream()
            .map(
                draftInvoiceLine ->
                    toInvoiceLine(
                        invoice,
                        draftInvoiceLine,
                        metricNameByCode.get(draftInvoiceLine.billableMetricCode())))
            .toList());
    invoiceEmailService.record(
        organizationName, invoice, withoutZeroQuantityLines.lines(), billingEmail);
    auditLogService.record(
        organizationId,
        "invoice_finalized",
        "invoice",
        invoice.getId().toString(),
        Map.of("month", month.toString()));
    return Optional.of(invoice);
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

  private InvoiceLine toInvoiceLine(
      Invoice invoice, DraftInvoiceLine draftInvoiceLine, String billableMetricName) {
    return new InvoiceLine(
        UUID.randomUUID(),
        invoice.getOrganizationId(),
        invoice.getId(),
        draftInvoiceLine.billableMetricCode(),
        draftInvoiceLine.targetProperty(),
        jsonMapper.writeValueAsString(draftInvoiceLine.dimensionValues()),
        draftInvoiceLine.quantity(),
        draftInvoiceLine.unitPrice(),
        draftInvoiceLine.amount(),
        draftInvoiceLine.lineType(),
        draftInvoiceLine.tierIndex(),
        draftInvoiceLine.quantityNumerator(),
        draftInvoiceLine.quantityDenominator(),
        invoice.getEnvironmentId(),
        billableMetricName);
  }

  private record Targets(
      String organizationName,
      List<DraftInvoiceCustomer> draftInvoiceCustomers,
      Map<UUID, String> billingEmailByCustomerId,
      Map<String, String> metricNameByCode) {}
}
