package com.meterengine.dashboard.service;

import com.meterengine.dashboard.dto.DashboardResponse;
import com.meterengine.dashboard.dto.DashboardResponse.DashboardInvoice;
import com.meterengine.global.security.BusinessEnvironmentContext;
import com.meterengine.invoice.entity.Invoice;
import com.meterengine.invoice.repository.InvoiceRepository;
import com.meterengine.invoice.service.DraftInvoiceService;
import com.meterengine.metric.service.BillableMetricUsageService;
import com.meterengine.payment.dto.InvoicePaymentResponse;
import com.meterengine.payment.service.InvoicePaymentService;
import com.meterengine.payment.service.PaymentSettingService;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

  private static final Set<String> COLLECTED = Set.of("paid", "partially_canceled", "canceled");
  private static final Set<String> OUTSTANDING = Set.of("unpaid", "pending", "failed");
  private final JdbcTemplate jdbcTemplate;
  private final DraftInvoiceService draftInvoiceService;
  private final InvoicePaymentService invoicePaymentService;
  private final PaymentSettingService paymentSettingService;
  private final Clock clock;
  private final InvoiceRepository invoiceRepository;

  DashboardService(
      JdbcTemplate jdbcTemplate,
      DraftInvoiceService draftInvoiceService,
      InvoicePaymentService invoicePaymentService,
      PaymentSettingService paymentSettingService,
      Clock clock,
      InvoiceRepository invoiceRepository) {
    this.jdbcTemplate = jdbcTemplate;
    this.draftInvoiceService = draftInvoiceService;
    this.invoicePaymentService = invoicePaymentService;
    this.paymentSettingService = paymentSettingService;
    this.clock = clock;
    this.invoiceRepository = invoiceRepository;
  }

  @Transactional(readOnly = true)
  public DashboardResponse get(UUID organizationId, YearMonth month) {
    String mode =
        BusinessEnvironmentContext.mode() == null
            ? paymentSettingService.get(organizationId).mode()
            : BusinessEnvironmentContext.mode();
    var draft = draftInvoiceService.preview(organizationId, month);
    List<InvoicePaymentResponse> payments =
        invoicePaymentService.list(organizationId, month, null, mode).invoicePayments();
    Map<UUID, Invoice> invoiceById =
        (BusinessEnvironmentContext.environmentId() == null
                ? invoiceRepository.findNewestFirst(organizationId, null, month.toString())
                : invoiceRepository.findNewestFirstScoped(
                    organizationId,
                    BusinessEnvironmentContext.environmentId(),
                    null,
                    month.toString()))
            .stream().collect(Collectors.toMap(Invoice::getId, invoice -> invoice));
    List<DashboardInvoice> invoicePayments =
        payments.stream()
            .map(
                payment -> {
                  Invoice invoice = invoiceById.get(payment.invoiceId());
                  return new DashboardInvoice(
                      payment.invoiceId(),
                      invoice.getCustomerId(),
                      invoice.getCustomerName(),
                      invoice.getPeriod(),
                      payment.status(),
                      payment.mode(),
                      payment.amount(),
                      payment.canceledAmount(),
                      payment.attemptCount(),
                      payment.lastFailureCode(),
                      payment.nextRetryAt(),
                      payment.completedAt());
                })
            .toList();
    long collected =
        payments.stream()
            .filter(p -> COLLECTED.contains(p.status()))
            .mapToLong(p -> p.amount() - p.canceledAmount())
            .reduce(0L, Math::addExact);
    long canceled =
        payments.stream()
            .mapToLong(InvoicePaymentResponse::canceledAmount)
            .reduce(0L, Math::addExact);
    long outstanding =
        payments.stream()
            .filter(p -> OUTSTANDING.contains(p.status()))
            .mapToLong(InvoicePaymentResponse::amount)
            .reduce(0L, Math::addExact);
    return new DashboardResponse(
        month.toString(),
        OffsetDateTime.now(clock),
        count(
            "select count(*) from customer where organization_id = ? and environment_id IS NOT"
                + " DISTINCT FROM ?::uuid and archived_at is null",
            organizationId,
            BusinessEnvironmentContext.environmentId()),
        count(
            "select count(*) from billable_metric where organization_id = ? and environment_id IS"
                + " NOT DISTINCT FROM ?::uuid and archived_at is null",
            organizationId,
            BusinessEnvironmentContext.environmentId()),
        count(
            "select count(*) from api_key where organization_id = ? and environment_id IS NOT"
                + " DISTINCT FROM ?::uuid and revoked_at is null and (expires_at is null or"
                + " expires_at > ?)",
            organizationId,
            BusinessEnvironmentContext.environmentId(),
            OffsetDateTime.now(clock)),
        count(
            "select count(*) from event where organization_id = ? and environment_id IS NOT"
                + " DISTINCT FROM ?::uuid and occurred_at >= ? and occurred_at < ?",
            organizationId,
            BusinessEnvironmentContext.environmentId(),
            month.atDay(1).atStartOfDay(BillableMetricUsageService.BILLING_ZONE).toOffsetDateTime(),
            month
                .plusMonths(1)
                .atDay(1)
                .atStartOfDay(BillableMetricUsageService.BILLING_ZONE)
                .toOffsetDateTime()),
        (BusinessEnvironmentContext.environmentId() == null
                ? count(
                    "select count(*) from organization where id = ? and"
                        + " business_registration_number is not null and representative_name is not"
                        + " null and business_address is not null",
                    organizationId)
                : count(
                    "select count(*) from organization_environment where organization_id = ? and id"
                        + " = ? and business_registration_number is not null and"
                        + " representative_name is not null and business_address is not null",
                    organizationId,
                    BusinessEnvironmentContext.environmentId()))
            > 0,
        count(
                "select count(*) from toss_payments_credential where organization_id = ? and"
                    + " environment_id IS NOT DISTINCT FROM ?::uuid and mode = ? and"
                    + " archived_at is null",
                organizationId,
                BusinessEnvironmentContext.environmentId(),
                mode)
            > 0,
        mode,
        draft.totalAmount(),
        payments.stream().mapToLong(InvoicePaymentResponse::amount).reduce(0L, Math::addExact),
        collected,
        canceled,
        outstanding,
        payments.stream().filter(p -> "failed".equals(p.status())).count(),
        draft.customers().stream().mapToLong(c -> c.unpricedUsages().size()).sum(),
        draft.customers().stream().mapToLong(c -> c.negativeUsages().size()).sum(),
        invoicePayments,
        draft);
  }

  private long count(String sql, Object... arguments) {
    Long result = jdbcTemplate.queryForObject(sql, Long.class, arguments);
    return result == null ? 0 : result;
  }
}
