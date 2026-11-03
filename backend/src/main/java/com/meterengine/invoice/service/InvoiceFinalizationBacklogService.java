package com.meterengine.invoice.service;

import com.meterengine.global.security.BusinessEnvironment;
import com.meterengine.global.security.BusinessEnvironmentContext;
import com.meterengine.global.security.OrganizationEnvironments;
import com.meterengine.global.time.BillingMonth;
import com.meterengine.invoice.dto.DraftInvoiceResponse.DraftInvoiceCustomer;
import com.meterengine.invoice.repository.InvoiceRepository;
import com.meterengine.metric.service.BillableMetricUsageService;
import com.meterengine.organization.entity.Organization;
import com.meterengine.organization.repository.OrganizationRepository;
import com.meterengine.scheduling.dto.ScheduledJobResult;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class InvoiceFinalizationBacklogService {

  private static final Logger logger =
      LoggerFactory.getLogger(InvoiceFinalizationBacklogService.class);
  private static final String BACKLOG = "meterengine.invoice.finalization.backlog";
  private static final String OLDEST_CUTOFF =
      "meterengine.invoice.finalization.backlog.oldest.cutoff";
  private static final String ENVIRONMENT_TAG = "environment";
  private static final String LEGACY = "legacy";
  private static final List<String> ENVIRONMENTS = List.of(LEGACY, "test", "live");
  // 달마다 그 도입사 이벤트를 다시 집계하는 무거운 계산이라 최근 1년만 본다.
  private static final int LOOKBACK_MONTHS = 12;
  private static final long NO_WAITING_MONTH = 0;

  private final OrganizationRepository organizationRepository;
  private final OrganizationEnvironments organizationEnvironments;
  private final DraftInvoiceService draftInvoiceService;
  private final InvoiceRepository invoiceRepository;
  private final MeterRegistry meterRegistry;
  private final Clock clock;
  private final Map<String, AtomicLong> backlogByEnvironment = new ConcurrentHashMap<>();
  private final Map<String, AtomicLong> oldestCutoffByEnvironment = new ConcurrentHashMap<>();
  // 집계 마감 뒤에는 사용량이 바뀌지 않고 확정도 되돌리지 않으므로, 처음 센 대기 고객에서 그 뒤 확정된 고객만 뺀다.
  private final Map<EnvironmentMonth, Set<UUID>> waitingCustomerIdsByMonth =
      new ConcurrentHashMap<>();

  InvoiceFinalizationBacklogService(
      OrganizationRepository organizationRepository,
      OrganizationEnvironments organizationEnvironments,
      DraftInvoiceService draftInvoiceService,
      InvoiceRepository invoiceRepository,
      MeterRegistry meterRegistry,
      Clock clock) {
    this.organizationRepository = organizationRepository;
    this.organizationEnvironments = organizationEnvironments;
    this.draftInvoiceService = draftInvoiceService;
    this.invoiceRepository = invoiceRepository;
    this.meterRegistry = meterRegistry;
    this.clock = clock;
  }

  // 집계 마감이 지난 달에 0원이 아닌 청구 예정액이 남은 고객과 달의 수, 가장 오래 기다린 달의 마감 시각을 환경별로 낸다.
  // 결과의 처리는 청구 예정액을 새로 계산한 달, 건너뜀은 앞서 센 고객을 다시 쓴 달이다.
  public ScheduledJobResult measure() {
    YearMonth lastEndedMonth = lastEndedMonth(BillingMonth.now(clock));
    YearMonth firstLookbackMonth = lastEndedMonth.minusMonths(LOOKBACK_MONTHS - 1);
    waitingCustomerIdsByMonth
        .keySet()
        .removeIf(environmentMonth -> environmentMonth.month().isBefore(firstLookbackMonth));
    Tally tally = new Tally();
    for (Organization organization :
        organizationRepository.findByStatusOrderByCreatedAtAscIdAsc(Organization.ACTIVE)) {
      List<YearMonth> months =
          monthsBetween(firstMonth(organization, firstLookbackMonth), lastEndedMonth);
      for (BusinessEnvironment environment : environments(organization, tally)) {
        for (YearMonth month : months) {
          count(new EnvironmentMonth(environment, month), tally);
        }
      }
    }
    // 실패한 도입사는 합계에서 빠지고, 실패 건수는 예약 작업 지표로 드러난다.
    ENVIRONMENTS.forEach(environment -> publish(environment, tally));
    return tally.result();
  }

  private static YearMonth lastEndedMonth(OffsetDateTime now) {
    YearMonth lastMonth = YearMonth.from(now).minusMonths(1);
    return now.isBefore(BillableMetricUsageService.cutoffAt(lastMonth))
        ? lastMonth.minusMonths(1)
        : lastMonth;
  }

  private static YearMonth firstMonth(Organization organization, YearMonth firstLookbackMonth) {
    YearMonth createdMonth =
        YearMonth.from(organization.getCreatedAt().atZoneSameInstant(BillingMonth.ZONE));
    return createdMonth.isAfter(firstLookbackMonth) ? createdMonth : firstLookbackMonth;
  }

  private static List<YearMonth> monthsBetween(YearMonth firstMonth, YearMonth lastMonth) {
    return Stream.iterate(
            firstMonth, month -> !month.isAfter(lastMonth), month -> month.plusMonths(1))
        .toList();
  }

  private List<BusinessEnvironment> environments(Organization organization, Tally tally) {
    List<BusinessEnvironment> environments = new ArrayList<>();
    environments.add(new BusinessEnvironment(organization.getId(), null, null));
    try {
      environments.addAll(organizationEnvironments.list(organization.getId()));
    } catch (RuntimeException exception) {
      logger.error(
          "invoice finalization backlog environments failed: organization_id={}",
          organization.getId(),
          exception);
      tally.failed();
    }
    return environments;
  }

  private void count(EnvironmentMonth environmentMonth, Tally tally) {
    try {
      Set<UUID> counted = waitingCustomerIdsByMonth.get(environmentMonth);
      if (counted == null) {
        waitingCustomerIdsByMonth.put(
            environmentMonth, previewWaitingCustomerIds(environmentMonth));
        tally.previewed();
      } else {
        waitingCustomerIdsByMonth.put(
            environmentMonth, withoutFinalized(environmentMonth, counted));
        tally.reused();
      }
      tally.waiting(environmentMonth, waitingCustomerIdsByMonth.get(environmentMonth).size());
    } catch (RuntimeException exception) {
      logger.error(
          "invoice finalization backlog month failed: organization_id={}, environment={},"
              + " month={}",
          environmentMonth.environment().organizationId(),
          environmentMonth.label(),
          environmentMonth.month(),
          exception);
      tally.failed();
    }
  }

  private Set<UUID> previewWaitingCustomerIds(EnvironmentMonth environmentMonth) {
    BusinessEnvironment environment = environmentMonth.environment();
    return BusinessEnvironmentContext.run(
        environment,
        () ->
            draftInvoiceService
                .preview(environment.organizationId(), environmentMonth.month())
                .customers()
                .stream()
                .filter(draftInvoiceCustomer -> draftInvoiceCustomer.amount() != 0)
                .map(DraftInvoiceCustomer::customerId)
                .collect(Collectors.toUnmodifiableSet()));
  }

  private Set<UUID> withoutFinalized(EnvironmentMonth environmentMonth, Set<UUID> counted) {
    if (counted.isEmpty()) {
      return counted;
    }
    BusinessEnvironment environment = environmentMonth.environment();
    Set<UUID> finalized =
        Set.copyOf(
            invoiceRepository.findCustomerIdsScoped(
                environment.organizationId(),
                environment.environmentId(),
                environmentMonth.month().toString()));
    return counted.stream()
        .filter(customerId -> !finalized.contains(customerId))
        .collect(Collectors.toUnmodifiableSet());
  }

  private void publish(String environment, Tally tally) {
    backlogOf(environment).set(tally.backlog(environment));
    oldestCutoffOf(environment).set(tally.oldestCutoffEpochSeconds(environment));
  }

  // 하루 한 번만 세므로 처음 센 뒤에 등록해, 기동 직후 첫 계산 전까지 0으로 보이지 않게 한다.
  private AtomicLong backlogOf(String environment) {
    return backlogByEnvironment.computeIfAbsent(
        environment,
        label -> {
          AtomicLong count = new AtomicLong();
          Gauge.builder(BACKLOG, count, AtomicLong::get)
              .tag(ENVIRONMENT_TAG, label)
              .register(meterRegistry);
          return count;
        });
  }

  // 경과 대신 마감 시각을 내야 하루 한 번 세도 대시보드의 경과가 계속 늘어난다.
  private AtomicLong oldestCutoffOf(String environment) {
    return oldestCutoffByEnvironment.computeIfAbsent(
        environment,
        label -> {
          AtomicLong epochSeconds = new AtomicLong();
          Gauge.builder(OLDEST_CUTOFF, epochSeconds, AtomicLong::get)
              .tag(ENVIRONMENT_TAG, label)
              .baseUnit("seconds")
              .register(meterRegistry);
          return epochSeconds;
        });
  }

  private record EnvironmentMonth(BusinessEnvironment environment, YearMonth month) {

    String label() {
      return environment.mode() == null ? LEGACY : environment.mode();
    }
  }

  private static final class Tally {

    private final Map<String, Long> backlogByEnvironment = new HashMap<>();
    private final Map<String, YearMonth> oldestMonthByEnvironment = new HashMap<>();
    private int previewedCount;
    private int reusedCount;
    private int failedCount;

    void previewed() {
      previewedCount++;
    }

    void reused() {
      reusedCount++;
    }

    void failed() {
      failedCount++;
    }

    void waiting(EnvironmentMonth environmentMonth, long customerCount) {
      if (customerCount == 0) {
        return;
      }
      backlogByEnvironment.merge(environmentMonth.label(), customerCount, Long::sum);
      oldestMonthByEnvironment.merge(
          environmentMonth.label(),
          environmentMonth.month(),
          (oldest, month) -> month.isBefore(oldest) ? month : oldest);
    }

    long backlog(String environment) {
      return backlogByEnvironment.getOrDefault(environment, 0L);
    }

    long oldestCutoffEpochSeconds(String environment) {
      return Optional.ofNullable(oldestMonthByEnvironment.get(environment))
          .map(month -> BillableMetricUsageService.cutoffAt(month).toEpochSecond())
          .orElse(NO_WAITING_MONTH);
    }

    ScheduledJobResult result() {
      return new ScheduledJobResult(previewedCount, reusedCount, failedCount);
    }
  }
}
