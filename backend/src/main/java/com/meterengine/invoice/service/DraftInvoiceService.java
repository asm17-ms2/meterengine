package com.meterengine.invoice.service;

import com.meterengine.customer.entity.Customer;
import com.meterengine.customer.repository.CustomerRepository;
import com.meterengine.global.security.BusinessEnvironmentContext;
import com.meterengine.global.time.BillingMonth;
import com.meterengine.invoice.config.InvoiceAutoFinalizeProperties;
import com.meterengine.invoice.dto.DraftInvoiceResponse;
import com.meterengine.invoice.dto.DraftInvoiceResponse.DraftInvoiceCustomer;
import com.meterengine.invoice.dto.DraftInvoiceResponse.DraftInvoiceLine;
import com.meterengine.invoice.dto.DraftInvoiceResponse.DraftInvoiceNegativeUsage;
import com.meterengine.invoice.dto.DraftInvoiceResponse.DraftInvoiceUnpricedUsage;
import com.meterengine.invoice.repository.InvoiceFinalizationAttemptRepository;
import com.meterengine.invoice.repository.InvoiceRepository;
import com.meterengine.metric.service.BillableMetricUsageService;
import com.meterengine.pricing.dto.CustomerCharge;
import com.meterengine.pricing.dto.DimensionCharge;
import com.meterengine.pricing.dto.NegativeUsage;
import com.meterengine.pricing.dto.UnpricedUsage;
import com.meterengine.pricing.service.ChargeService;
import com.meterengine.scheduling.service.ScheduledJobRunService;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
  private final InvoiceAutoFinalizeProperties invoiceAutoFinalizeProperties;
  private final InvoiceFinalizationAttemptRepository invoiceFinalizationAttemptRepository;
  private final ScheduledJobRunService scheduledJobRunService;
  private final Clock clock;

  DraftInvoiceService(
      BillableMetricUsageService billableMetricUsageService,
      CustomerRepository customerRepository,
      ChargeService chargeService,
      InvoiceRepository invoiceRepository,
      InvoiceAutoFinalizeProperties invoiceAutoFinalizeProperties,
      InvoiceFinalizationAttemptRepository invoiceFinalizationAttemptRepository,
      ScheduledJobRunService scheduledJobRunService,
      Clock clock) {
    this.billableMetricUsageService = billableMetricUsageService;
    this.customerRepository = customerRepository;
    this.chargeService = chargeService;
    this.invoiceRepository = invoiceRepository;
    this.invoiceAutoFinalizeProperties = invoiceAutoFinalizeProperties;
    this.invoiceFinalizationAttemptRepository = invoiceFinalizationAttemptRepository;
    this.scheduledJobRunService = scheduledJobRunService;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public DraftInvoiceResponse preview(UUID organizationId, YearMonth month) {
    OffsetDateTime calculatedAt =
        OffsetDateTime.now(clock.withZone(BillableMetricUsageService.BILLING_ZONE));
    List<DraftInvoiceCustomer> draftInvoiceCustomers =
        calculate(organizationId, month, BillableMetricUsageService.cutoffAt(month));

    long totalAmount =
        draftInvoiceCustomers.stream()
            .mapToLong(DraftInvoiceCustomer::amount)
            .reduce(0L, Math::addExact);

    AutoFinalize autoFinalize = autoFinalize(organizationId, month, calculatedAt);
    return new DraftInvoiceResponse(
        month.toString(),
        calculatedAt,
        autoFinalize.enabled(),
        autoFinalize.autoFinalizeAt(),
        autoFinalize.running(),
        totalAmount,
        draftInvoiceCustomers);
  }

  // 확정 시각 전이면 그 시각이다. 지났으면 요청한 환경에서 그 달을 아직 다 훑지 않았거나 실패가 남았을 때만 다음 정시다.
  private AutoFinalize autoFinalize(UUID organizationId, YearMonth month, OffsetDateTime now) {
    if (!invoiceAutoFinalizeProperties.enabled()) {
      return new AutoFinalize(false, null, false);
    }
    OffsetDateTime autoFinalizeAt = InvoiceAutoFinalizationService.autoFinalizeAt(month);
    if (now.isBefore(autoFinalizeAt)) {
      return new AutoFinalize(true, autoFinalizeAt, false);
    }
    UUID environmentId = BusinessEnvironmentContext.environmentId();
    Optional<OffsetDateTime> firstCreatedAt =
        environmentId == null
            ? customerRepository.findFirstCreatedAt(organizationId)
            : customerRepository.findFirstCreatedAtScoped(organizationId, environmentId);
    boolean coveredMonth =
        firstCreatedAt
            .map(InvoiceAutoFinalizationService::firstBillableMonth)
            .filter(firstBillableMonth -> !month.isBefore(firstBillableMonth))
            .isPresent();
    if (!coveredMonth) {
      return new AutoFinalize(true, null, false);
    }
    OffsetDateTime nextHour = now.truncatedTo(ChronoUnit.HOURS).plusHours(1);
    if (!invoiceFinalizationAttemptRepository.existsSweptPeriod(
        organizationId, environmentId, month.toString())) {
      return new AutoFinalize(
          true,
          nextHour,
          scheduledJobRunService.isRunning(InvoiceAutoFinalizationService.JOB_NAME));
    }
    return new AutoFinalize(
        true,
        invoiceFinalizationAttemptRepository.existsOpenFailure(
                organizationId, environmentId, month.toString())
            ? nextHour
            : null,
        false);
  }

  // 그 달을 확정하지 않은 고객마다, 그 달에 발생했고 receivedBefore 전에 받은 이벤트로 금액을 계산한다.
  @Transactional(readOnly = true)
  public List<DraftInvoiceCustomer> calculate(
      UUID organizationId, YearMonth month, OffsetDateTime receivedBefore) {
    Set<UUID> finalizedCustomerIds =
        Set.copyOf(
            (BusinessEnvironmentContext.environmentId() == null
                ? invoiceRepository.findCustomerIds(organizationId, month.toString())
                : invoiceRepository.findCustomerIdsScoped(
                    organizationId, BusinessEnvironmentContext.environmentId(), month.toString())));
    List<Customer> organizationCustomers =
        (BusinessEnvironmentContext.environmentId() == null
                ? customerRepository.findByOrganizationIdOrderByNameAscIdAsc(organizationId)
                : customerRepository.listScoped(
                    organizationId, BusinessEnvironmentContext.environmentId()))
            .stream().filter(customer -> !finalizedCustomerIds.contains(customer.getId())).toList();

    Map<UUID, CustomerCharge> customerChargeByCustomerId =
        chargeService.calculate(
            organizationId,
            month,
            billableMetricUsageService.aggregate(
                organizationId, month, BillingMonth.now(clock), receivedBefore));

    return organizationCustomers.stream()
        .map(
            customer ->
                toDraftInvoiceCustomer(
                    customer,
                    customerChargeByCustomerId.getOrDefault(
                        customer.getId(),
                        new CustomerCharge(customer.getId(), 0, List.of(), List.of(), List.of()))))
        .toList();
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

  private record AutoFinalize(boolean enabled, OffsetDateTime autoFinalizeAt, boolean running) {}
}
