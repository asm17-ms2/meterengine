package com.meterengine.invoice.service;

import com.meterengine.customer.repository.CustomerRepository;
import com.meterengine.global.security.BusinessEnvironment;
import com.meterengine.global.security.BusinessEnvironmentContext;
import com.meterengine.global.security.OrganizationEnvironments;
import com.meterengine.invoice.config.InvoiceAutoFinalizeProperties;
import com.meterengine.invoice.dto.DraftInvoiceResponse.DraftInvoiceCustomer;
import com.meterengine.invoice.dto.DraftInvoiceResponse.DraftInvoiceUnpricedUsage;
import com.meterengine.invoice.entity.Invoice;
import com.meterengine.invoice.entity.InvoiceFinalizationAttempt;
import com.meterengine.invoice.repository.InvoiceFinalizationAttemptRepository;
import com.meterengine.invoice.repository.InvoiceFinalizationAttemptRepository.OpenAttempt;
import com.meterengine.invoice.service.InvoiceFinalizationService.Targets;
import com.meterengine.metric.service.BillableMetricUsageService;
import com.meterengine.organization.entity.Organization;
import com.meterengine.organization.repository.OrganizationRepository;
import com.meterengine.scheduling.dto.ScheduledJobResult;
import com.meterengine.scheduling.service.ScheduledJobRunService;
import java.time.Clock;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

@Service
public class InvoiceAutoFinalizationService {

  static final String JOB_NAME = "invoice_auto_finalize";

  private static final Logger logger =
      LoggerFactory.getLogger(InvoiceAutoFinalizationService.class);

  private static final LocalTime AUTO_FINALIZE_TIME = LocalTime.of(14, 0);

  private final InvoiceAutoFinalizeProperties invoiceAutoFinalizeProperties;
  private final OrganizationRepository organizationRepository;
  private final OrganizationEnvironments organizationEnvironments;
  private final CustomerRepository customerRepository;
  private final InvoiceFinalizationService invoiceFinalizationService;
  private final InvoiceFinalizationAttemptRepository invoiceFinalizationAttemptRepository;
  private final ScheduledJobRunService scheduledJobRunService;
  private final TransactionTemplate transactionTemplate;
  private final JsonMapper jsonMapper;
  private final Clock clock;

  InvoiceAutoFinalizationService(
      InvoiceAutoFinalizeProperties invoiceAutoFinalizeProperties,
      OrganizationRepository organizationRepository,
      OrganizationEnvironments organizationEnvironments,
      CustomerRepository customerRepository,
      InvoiceFinalizationService invoiceFinalizationService,
      InvoiceFinalizationAttemptRepository invoiceFinalizationAttemptRepository,
      ScheduledJobRunService scheduledJobRunService,
      TransactionTemplate transactionTemplate,
      JsonMapper jsonMapper,
      Clock clock) {
    this.invoiceAutoFinalizeProperties = invoiceAutoFinalizeProperties;
    this.organizationRepository = organizationRepository;
    this.organizationEnvironments = organizationEnvironments;
    this.customerRepository = customerRepository;
    this.invoiceFinalizationService = invoiceFinalizationService;
    this.invoiceFinalizationAttemptRepository = invoiceFinalizationAttemptRepository;
    this.scheduledJobRunService = scheduledJobRunService;
    this.transactionTemplate = transactionTemplate;
    this.jsonMapper = jsonMapper;
    this.clock = clock;
  }

  // 그 달을 자동 확정하는 시각. 다음 달 1일 14:00 KST다.
  public static OffsetDateTime autoFinalizeAt(YearMonth month) {
    return month
        .plusMonths(1)
        .atDay(1)
        .atTime(AUTO_FINALIZE_TIME)
        .atZone(BillableMetricUsageService.BILLING_ZONE)
        .toOffsetDateTime();
  }

  @EventListener(ApplicationReadyEvent.class)
  void registerJob() {
    if (invoiceAutoFinalizeProperties.enabled()) {
      scheduledJobRunService.register(JOB_NAME);
    }
  }

  @Scheduled(cron = "0 0 * * * *", zone = "Asia/Seoul")
  public void run() {
    if (!invoiceAutoFinalizeProperties.enabled()) {
      return;
    }
    OffsetDateTime now =
        OffsetDateTime.now(clock.withZone(BillableMetricUsageService.BILLING_ZONE));
    scheduledJobRunService.run(JOB_NAME, now.truncatedTo(ChronoUnit.HOURS), () -> finalize(now));
  }

  // 확정 시각이 지난 달 중 끝난 결과가 없는 (고객, 달)을 활성 도입사마다 시도하고 결과를 남긴다.
  // 처리 수는 확정 수, 건너뛴 수는 이번 실행에서 보류한 수, 실패 수는 실패한 (고객, 달)의 수다.
  public ScheduledJobResult finalize(OffsetDateTime now) {
    ScheduledJobResult total = new ScheduledJobResult(0, 0, 0);
    for (Organization organization :
        organizationRepository.findByStatusOrderByCreatedAtAscIdAsc(Organization.ACTIVE)) {
      total = sum(total, finalize(organization, now));
    }
    return total;
  }

  public ScheduledJobResult finalize(UUID organizationId, OffsetDateTime now) {
    return finalize(organizationRepository.findById(organizationId).orElseThrow(), now);
  }

  // 환경 구분 이전 범위와 그 도입사의 환경을 따로 훑는다. 고객, 단가, 시도 기록은 그 범위의 것만 본다.
  private ScheduledJobResult finalize(Organization organization, OffsetDateTime now) {
    List<BusinessEnvironment> environments = new ArrayList<>();
    environments.add(new BusinessEnvironment(organization.getId(), null, null));
    environments.addAll(organizationEnvironments.list(organization.getId()));
    ScheduledJobResult total = new ScheduledJobResult(0, 0, 0);
    for (BusinessEnvironment environment : environments) {
      total =
          sum(
              total,
              BusinessEnvironmentContext.run(
                  environment, () -> finalizeEnvironment(organization, now)));
    }
    return total;
  }

  // 아직 다 훑지 않은 달은 대상 고객을 모두 시도하고 그 달을 훑었다고 표시한다.
  // 다 훑은 달은 표 전체를 다시 보지 않고, 마지막 시도가 실패인 고객만 다시 시도한다.
  private ScheduledJobResult finalizeEnvironment(Organization organization, OffsetDateTime now) {
    UUID environmentId = BusinessEnvironmentContext.environmentId();
    ScheduledJobResult total = new ScheduledJobResult(0, 0, 0);
    Optional<OffsetDateTime> firstCreatedAt =
        environmentId == null
            ? customerRepository.findFirstCreatedAt(organization.getId())
            : customerRepository.findFirstCreatedAtScoped(organization.getId(), environmentId);
    if (firstCreatedAt.isEmpty()) {
      return total;
    }

    YearMonth lastMonth = lastAutoFinalizedMonth(now);
    Set<String> sweptPeriods =
        new HashSet<>(
            invoiceFinalizationAttemptRepository.findSweptPeriods(
                organization.getId(), environmentId));
    Set<String> sweptNow = new HashSet<>();
    for (YearMonth month = firstBillableMonth(firstCreatedAt.get());
        !month.isAfter(lastMonth);
        month = month.plusMonths(1)) {
      if (sweptPeriods.contains(month.toString())) {
        continue;
      }
      List<UUID> customerIds =
          invoiceFinalizationAttemptRepository.findUnfinishedCandidates(
              organization.getId(),
              environmentId,
              month.toString(),
              BillableMetricUsageService.cutoffAt(month));
      if (!customerIds.isEmpty()) {
        total = sum(total, finalize(organization, month, customerIds, false, now));
      }
      invoiceFinalizationAttemptRepository.insertSweptIfAbsent(
          organization.getId(), environmentId, month.toString(), now);
      sweptNow.add(month.toString());
    }

    total = sum(total, retryFailures(organization, environmentId, sweptNow, now));
    return total;
  }

  // 실패만 다시 시도한다. 지난달 단가는 등록할 수 없어(PriceRateService.create) 보류는 다시 계산해도 같으니 수동 확정으로 끝낸다.
  private ScheduledJobResult retryFailures(
      Organization organization, UUID environmentId, Set<String> sweptNow, OffsetDateTime now) {
    Map<YearMonth, List<UUID>> failedCustomerIdsByMonth = new TreeMap<>();
    for (OpenAttempt openAttempt :
        invoiceFinalizationAttemptRepository.findOpenAttempts(
            organization.getId(), environmentId)) {
      if (InvoiceFinalizationAttempt.FAILED.equals(openAttempt.getResult())
          && !sweptNow.contains(openAttempt.getPeriod())) {
        failedCustomerIdsByMonth
            .computeIfAbsent(YearMonth.parse(openAttempt.getPeriod()), key -> new ArrayList<>())
            .add(openAttempt.getCustomerId());
      }
    }

    ScheduledJobResult total = new ScheduledJobResult(0, 0, 0);
    for (Map.Entry<YearMonth, List<UUID>> entry : failedCustomerIdsByMonth.entrySet()) {
      total = sum(total, finalize(organization, entry.getKey(), entry.getValue(), true, now));
    }
    return total;
  }

  // 실패를 다시 시도하다 도입사 계산이 또 실패하면 이미 실패인 고객에게 실패 기록을 다시 쌓지 않는다.
  private ScheduledJobResult finalize(
      Organization organization,
      YearMonth month,
      List<UUID> customerIds,
      boolean retryingFailures,
      OffsetDateTime now) {
    OffsetDateTime cutoffAt = BillableMetricUsageService.cutoffAt(month);
    Targets targets;
    try {
      targets =
          transactionTemplate.execute(
              status -> invoiceFinalizationService.targets(organization.getId(), month, cutoffAt));
    } catch (RuntimeException exception) {
      logger.error(
          "invoice auto finalization failed to calculate: organization={}, environment={},"
              + " month={}",
          organization.getId(),
          BusinessEnvironmentContext.mode(),
          month,
          exception);
      if (!retryingFailures) {
        for (UUID customerId : customerIds) {
          recordFailure(organization, customerId, month, exception, now);
        }
      }
      return new ScheduledJobResult(0, 0, customerIds.size());
    }

    Map<UUID, DraftInvoiceCustomer> draftInvoiceCustomerByCustomerId =
        targets.draftInvoiceCustomers().stream()
            .collect(Collectors.toMap(DraftInvoiceCustomer::customerId, Function.identity()));
    int finalizedCount = 0;
    int heldCount = 0;
    int failedCount = 0;
    for (UUID customerId : customerIds) {
      String result =
          attempt(
              organization,
              customerId,
              month,
              cutoffAt,
              targets,
              draftInvoiceCustomerByCustomerId.get(customerId),
              now);
      switch (result) {
        case InvoiceFinalizationAttempt.FINALIZED -> finalizedCount++;
        case InvoiceFinalizationAttempt.HELD -> heldCount++;
        case InvoiceFinalizationAttempt.FAILED -> failedCount++;
        default -> {}
      }
    }
    return new ScheduledJobResult(finalizedCount, heldCount, failedCount);
  }

  // 결과를 시도 기록에 남기고 돌려준다. 확정은 인보이스와 시도 기록을 한 트랜잭션에 쓴다.
  private String attempt(
      Organization organization,
      UUID customerId,
      YearMonth month,
      OffsetDateTime cutoffAt,
      Targets targets,
      DraftInvoiceCustomer draftInvoiceCustomer,
      OffsetDateTime now) {
    if (draftInvoiceCustomer == null) {
      return record(
          organization, customerId, month, InvoiceFinalizationAttempt.SKIPPED, null, null, now);
    }
    if (draftInvoiceCustomer.lines().isEmpty()
        && draftInvoiceCustomer.unpricedUsages().isEmpty()
        && draftInvoiceCustomer.negativeUsages().isEmpty()) {
      return record(
          organization, customerId, month, InvoiceFinalizationAttempt.NO_EVENTS, null, null, now);
    }
    // 0원이면 수동 확정도 청구할 것이 없어 보류하지 않고 끝낸다. 단가 없는 사용량은 사유에 남긴다.
    if (draftInvoiceCustomer.amount() == 0) {
      return record(
          organization,
          customerId,
          month,
          InvoiceFinalizationAttempt.ZERO_AMOUNT,
          draftInvoiceCustomer.unpricedUsages().isEmpty()
              ? null
              : unpricedReason(draftInvoiceCustomer.unpricedUsages()),
          null,
          now);
    }
    if (!draftInvoiceCustomer.unpricedUsages().isEmpty()) {
      return record(
          organization,
          customerId,
          month,
          InvoiceFinalizationAttempt.HELD,
          unpricedReason(draftInvoiceCustomer.unpricedUsages()),
          jsonMapper.writeValueAsString(draftInvoiceCustomer.unpricedUsages()),
          now);
    }

    try {
      return transactionTemplate.execute(
          status -> {
            Optional<Invoice> invoice =
                invoiceFinalizationService.finalizeCustomer(
                    organization.getId(),
                    targets.organizationName(),
                    month,
                    cutoffAt,
                    now,
                    draftInvoiceCustomer,
                    targets.billingEmailByCustomerId().get(customerId),
                    targets.metricNameByCode());
            return record(
                organization,
                customerId,
                month,
                invoice.isPresent()
                    ? InvoiceFinalizationAttempt.FINALIZED
                    : InvoiceFinalizationAttempt.SKIPPED,
                null,
                null,
                now);
          });
    } catch (RuntimeException exception) {
      logger.error(
          "invoice auto finalization failed: organization={}, environment={}, customer={},"
              + " month={}",
          organization.getId(),
          BusinessEnvironmentContext.mode(),
          customerId,
          month,
          exception);
      return recordFailure(organization, customerId, month, exception, now);
    }
  }

  private String recordFailure(
      Organization organization,
      UUID customerId,
      YearMonth month,
      RuntimeException exception,
      OffsetDateTime now) {
    return record(
        organization,
        customerId,
        month,
        InvoiceFinalizationAttempt.FAILED,
        exception.toString(),
        null,
        now);
  }

  private String record(
      Organization organization,
      UUID customerId,
      YearMonth month,
      String result,
      String reason,
      String unpricedUsages,
      OffsetDateTime now) {
    invoiceFinalizationAttemptRepository.save(
        new InvoiceFinalizationAttempt(
            organization.getId(),
            BusinessEnvironmentContext.environmentId(),
            customerId,
            month.toString(),
            result,
            reason,
            unpricedUsages,
            now));
    return result;
  }

  private static String unpricedReason(List<DraftInvoiceUnpricedUsage> unpricedUsages) {
    return "단가 없는 사용량: "
        + unpricedUsages.stream()
            .map(
                unpricedUsage ->
                    "%s %s %s"
                        .formatted(
                            unpricedUsage.billableMetricCode(),
                            unpricedUsage.dimensionValues(),
                            unpricedUsage.quantity().toPlainString()))
            .collect(Collectors.joining(", "));
  }

  // 그 달 마감 시각 뒤에 등록된 고객은 마감 전에 받은 이벤트가 있을 수 없다. 자동 확정은 이 달부터 훑는다.
  static YearMonth firstBillableMonth(OffsetDateTime createdAt) {
    YearMonth month =
        YearMonth.from(createdAt.atZoneSameInstant(BillableMetricUsageService.BILLING_ZONE))
            .minusMonths(1);
    while (!BillableMetricUsageService.cutoffAt(month).isAfter(createdAt)) {
      month = month.plusMonths(1);
    }
    return month;
  }

  private static YearMonth lastAutoFinalizedMonth(OffsetDateTime now) {
    YearMonth previousMonth =
        YearMonth.from(now.atZoneSameInstant(BillableMetricUsageService.BILLING_ZONE))
            .minusMonths(1);
    return autoFinalizeAt(previousMonth).isAfter(now)
        ? previousMonth.minusMonths(1)
        : previousMonth;
  }

  private static ScheduledJobResult sum(ScheduledJobResult left, ScheduledJobResult right) {
    return new ScheduledJobResult(
        left.processedCount() + right.processedCount(),
        left.skippedCount() + right.skippedCount(),
        left.failedCount() + right.failedCount());
  }
}
