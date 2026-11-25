package com.meterengine.invoice.service;

import com.meterengine.customer.entity.Customer;
import com.meterengine.customer.repository.CustomerRepository;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.NotFoundException;
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
import java.util.Collection;
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
    return preview(organizationId, month, null);
  }

  // customerId가 있으면 그 고객의 이벤트만 집계해 그 고객만 돌려준다. total_amount도 그 고객의 금액이다.
  @Transactional(readOnly = true)
  public DraftInvoiceResponse preview(UUID organizationId, YearMonth month, UUID customerId) {
    if (customerId != null && !existsCustomer(organizationId, customerId)) {
      throw new NotFoundException(ErrorCode.CUSTOMER_NOT_FOUND);
    }

    OffsetDateTime calculatedAt =
        OffsetDateTime.now(clock.withZone(BillableMetricUsageService.BILLING_ZONE));
    List<DraftInvoiceCustomer> draftInvoiceCustomers =
        calculate(
            organizationId,
            month,
            BillableMetricUsageService.cutoffAt(month),
            customerId == null ? null : List.of(customerId));

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

  private boolean existsCustomer(UUID organizationId, UUID customerId) {
    UUID environmentId = BusinessEnvironmentContext.environmentId();
    return environmentId == null
        ? customerRepository.existsByOrganizationIdAndId(organizationId, customerId)
        : customerRepository.findScoped(organizationId, environmentId, customerId).isPresent();
  }

  // 확정 시각 전이면 그 시각이다. 지났으면 요청한 환경에서 그 달을 아직 다 훑지 않았거나 실패가 남았을 때만 다음 실행 시각이다.
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
    OffsetDateTime nextRunAt = InvoiceAutoFinalizationService.nextRunAt(now);
    if (!invoiceFinalizationAttemptRepository.existsSweptPeriod(
        organizationId, environmentId, month.toString())) {
      return new AutoFinalize(
          true,
          nextRunAt,
          scheduledJobRunService.isRunning(InvoiceAutoFinalizationService.JOB_NAME));
    }
    return new AutoFinalize(
        true,
        invoiceFinalizationAttemptRepository.existsOpenFailure(
                organizationId, environmentId, month.toString())
            ? nextRunAt
            : null,
        false);
  }

  // 그 달을 확정하지 않은 고객마다, 그 달에 발생했고 receivedBefore 전에 받은 이벤트로 금액을 계산한다.
  @Transactional(readOnly = true)
  public List<DraftInvoiceCustomer> calculate(
      UUID organizationId, YearMonth month, OffsetDateTime receivedBefore) {
    return calculate(organizationId, month, receivedBefore, null);
  }

  // customerIds가 있으면 그 고객들의 이벤트만 집계해 그 고객들만 돌려준다.
  @Transactional(readOnly = true)
  public List<DraftInvoiceCustomer> calculate(
      UUID organizationId,
      YearMonth month,
      OffsetDateTime receivedBefore,
      Collection<UUID> customerIds) {
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
            .stream()
                .filter(customer -> customerIds == null || customerIds.contains(customer.getId()))
                .filter(customer -> !finalizedCustomerIds.contains(customer.getId()))
                .toList();

    Map<UUID, CustomerCharge> customerChargeByCustomerId =
        chargeService.calculate(
            organizationId,
            month,
            billableMetricUsageService.aggregate(
                organizationId, month, BillingMonth.now(clock), receivedBefore, customerIds));

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

  private DraftInvoiceCustomer toDraftInvoiceCustomer(
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
            .toList(),
        isAutoFinalizeHeld(customerCharge));
  }

  // 자동 확정처럼 0원이면 단가 없는 사용량이 있어도 보류하지 않는다.
  private boolean isAutoFinalizeHeld(CustomerCharge customerCharge) {
    return invoiceAutoFinalizeProperties.enabled()
        && customerCharge.amount() != 0
        && !customerCharge.unpricedUsages().isEmpty();
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
