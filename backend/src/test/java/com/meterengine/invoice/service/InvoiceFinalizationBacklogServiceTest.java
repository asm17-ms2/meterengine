package com.meterengine.invoice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.meterengine.global.database.DatabaseScope;
import com.meterengine.global.database.DatabaseScopes;
import com.meterengine.global.security.BusinessEnvironment;
import com.meterengine.global.security.BusinessEnvironmentContext;
import com.meterengine.global.security.OrganizationEnvironments;
import com.meterengine.invoice.dto.DraftInvoiceResponse;
import com.meterengine.invoice.dto.DraftInvoiceResponse.DraftInvoiceCustomer;
import com.meterengine.invoice.repository.InvoiceRepository;
import com.meterengine.organization.entity.Organization;
import com.meterengine.organization.repository.OrganizationRepository;
import com.meterengine.scheduling.dto.ScheduledJobResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;

class InvoiceFinalizationBacklogServiceTest {

  private static final String BACKLOG = "meterengine.invoice.finalization.backlog";
  private static final String OLDEST_CUTOFF =
      "meterengine.invoice.finalization.backlog.oldest.cutoff";
  private static final Instant OCTOBER_30_KST_10 = Instant.parse("2026-10-30T01:00:00Z");
  private static final OffsetDateTime LONG_AGO = OffsetDateTime.parse("2020-01-01T00:00:00Z");
  private static final UUID ORGANIZATION_ID = UUID.randomUUID();
  private static final UUID OTHER_ORGANIZATION_ID = UUID.randomUUID();
  private static final UUID LIVE_ENVIRONMENT_ID = UUID.randomUUID();
  private static final YearMonth AUGUST = YearMonth.of(2026, 8);
  private static final YearMonth SEPTEMBER = YearMonth.of(2026, 9);
  private static final int ENVIRONMENT_COUNT = 2;
  private static final int MONTHS_PER_ENVIRONMENT = 12;
  private static final int ALL_MONTHS = ENVIRONMENT_COUNT * MONTHS_PER_ENVIRONMENT;

  private final OrganizationRepository organizationRepository = mock(OrganizationRepository.class);
  private final OrganizationEnvironments organizationEnvironments =
      mock(OrganizationEnvironments.class);
  private final DraftInvoiceService draftInvoiceService = mock(DraftInvoiceService.class);
  private final InvoiceRepository invoiceRepository = mock(InvoiceRepository.class);
  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final Map<String, Map<YearMonth, List<DraftInvoiceCustomer>>> draftsByModeAndMonth =
      new HashMap<>();
  private final Set<UUID> failingOrganizationIds = new HashSet<>();
  private final Set<String> failingModes = new HashSet<>();
  private final List<Organization> organizations = new ArrayList<>();

  @BeforeEach
  void setUp() {
    activeOrganization(ORGANIZATION_ID, LIVE_ENVIRONMENT_ID, LONG_AGO);
    when(organizationRepository.findByStatusOrderByCreatedAtAscIdAsc(Organization.ACTIVE))
        .thenReturn(organizations);
    when(draftInvoiceService.preview(any(), any())).thenAnswer(this::stubbedDraft);
  }

  @Test
  void 집계_마감이_지난_달에_0원이_아닌_고객_수를_환경별로_센다() {
    draft("live", SEPTEMBER, customer(1000), customer(0), customer(250));
    draft("test", AUGUST, customer(500));

    measureAll(service(OCTOBER_30_KST_10));

    assertThat(backlog("live")).isEqualTo(2);
    assertThat(backlog("test")).isEqualTo(1);
    assertThat(meterRegistry.find(BACKLOG).tag("environment", "legacy").gauge()).isNull();
  }

  @Test
  void 가장_오래_기다린_달의_집계_마감_시각을_낸다() {
    draft("live", AUGUST, customer(1000));
    draft("live", SEPTEMBER, customer(1000));

    measureAll(service(OCTOBER_30_KST_10));

    long augustCutoff = OffsetDateTime.parse("2026-09-01T13:00:00+09:00").toEpochSecond();
    assertThat(oldestCutoff("live")).isEqualTo(augustCutoff);
  }

  @Test
  void 대기가_없는_환경은_마감_시각을_0으로_둔다() {
    measureAll(service(OCTOBER_30_KST_10));

    assertThat(oldestCutoff("test")).isZero();
  }

  @Test
  void 집계_마감_전인_지난달은_세지_않는다() {
    measureAll(service(Instant.parse("2026-10-01T03:00:00Z")));

    verify(draftInvoiceService, never()).preview(ORGANIZATION_ID, SEPTEMBER);
    verify(draftInvoiceService, atLeastOnce()).preview(ORGANIZATION_ID, AUGUST);
  }

  @Test
  void 마지막으로_끝난_달부터_12개월만_본다() {
    measureAll(service(OCTOBER_30_KST_10));

    verify(draftInvoiceService, atLeastOnce()).preview(ORGANIZATION_ID, YearMonth.of(2025, 10));
    verify(draftInvoiceService, never()).preview(ORGANIZATION_ID, YearMonth.of(2025, 9));
  }

  @Test
  void 도입사가_생기기_전_달은_보지_않는다() {
    organizations.clear();
    activeOrganization(
        ORGANIZATION_ID, LIVE_ENVIRONMENT_ID, OffsetDateTime.parse("2026-08-01T00:30:00+09:00"));

    measureAll(service(OCTOBER_30_KST_10));

    verify(draftInvoiceService, atLeastOnce()).preview(ORGANIZATION_ID, AUGUST);
    verify(draftInvoiceService, never()).preview(ORGANIZATION_ID, YearMonth.of(2026, 7));
  }

  @Test
  void 한_번_센_달은_다시_집계하지_않고_그_뒤_확정된_고객만_뺀다() {
    DraftInvoiceCustomer finalized = customer(1000);
    draft("live", SEPTEMBER, finalized, customer(250));
    InvoiceFinalizationBacklogService service = service(OCTOBER_30_KST_10);
    measureAll(service);
    when(invoiceRepository.findCustomerIds(ORGANIZATION_ID, SEPTEMBER.toString()))
        .thenReturn(List.of(finalized.customerId()));

    measureAll(service);

    assertThat(backlog("live")).isEqualTo(1);
    verify(draftInvoiceService, times(ENVIRONMENT_COUNT)).preview(ORGANIZATION_ID, SEPTEMBER);
  }

  @Test
  void 대기가_0인_달은_다음_계산에서_확정_조회도_하지_않는다() {
    draft("live", SEPTEMBER, customer(1000));
    InvoiceFinalizationBacklogService service = service(OCTOBER_30_KST_10);
    measureAll(service);

    measureAll(service);

    verify(invoiceRepository).findCustomerIds(ORGANIZATION_ID, SEPTEMBER.toString());
    verifyNoMoreInteractions(invoiceRepository);
  }

  @Test
  void 새로_집계한_달은_처리로_앞서_센_고객을_다시_쓴_달은_건너뜀으로_센다() {
    InvoiceFinalizationBacklogService service = service(OCTOBER_30_KST_10);

    ScheduledJobResult first = measureAll(service);
    ScheduledJobResult second = measureAll(service);

    assertThat(first).isEqualTo(new ScheduledJobResult(ALL_MONTHS, 0, 0));
    assertThat(second).isEqualTo(new ScheduledJobResult(0, ALL_MONTHS, 0));
  }

  @Test
  void 처음_세기_전에는_게이지를_내지_않는다() {
    InvoiceFinalizationBacklogService service = service(OCTOBER_30_KST_10);

    assertThat(meterRegistry.find(BACKLOG).gauges()).isEmpty();

    measureAll(service);

    assertThat(meterRegistry.find(BACKLOG).gauges()).hasSize(2);
    assertThat(meterRegistry.find(OLDEST_CUTOFF).gauges()).hasSize(2);
  }

  @Test
  void 한_환경의_집계가_실패해도_다른_환경을_센다() {
    draft("live", SEPTEMBER, customer(1000));
    failingModes.add("test");

    ScheduledJobResult result = measureAll(service(OCTOBER_30_KST_10));

    assertThat(backlog("live")).isEqualTo(1);
    assertThat(result.failedCount()).isEqualTo(MONTHS_PER_ENVIRONMENT);
  }

  @Test
  void 집계가_실패한_도입사만_빼고_합계를_낸다() {
    draft("live", SEPTEMBER, customer(1000));
    activeOrganization(OTHER_ORGANIZATION_ID, UUID.randomUUID(), LONG_AGO);
    failingOrganizationIds.add(OTHER_ORGANIZATION_ID);

    ScheduledJobResult result = measureAll(service(OCTOBER_30_KST_10));

    assertThat(backlog("live")).isEqualTo(1);
    assertThat(result.failedCount()).isEqualTo(ALL_MONTHS);
  }

  @Test
  void 환경_목록을_못_받은_도입사는_test와_live_합계에서_빠진다() {
    draft("live", SEPTEMBER, customer(1000));
    activeOrganization(OTHER_ORGANIZATION_ID, UUID.randomUUID(), LONG_AGO);
    when(organizationEnvironments.list(OTHER_ORGANIZATION_ID))
        .thenThrow(new IllegalStateException("환경 조회 실패"));

    ScheduledJobResult result = measureAll(service(OCTOBER_30_KST_10));

    assertThat(backlog("live")).isEqualTo(1);
    assertThat(result.failedCount()).isEqualTo(ENVIRONMENT_COUNT);
  }

  @Test
  void 집계가_실패한_달은_다음_계산에서_다시_집계한다() {
    draft("live", SEPTEMBER, customer(1000));
    failingModes.add("live");
    InvoiceFinalizationBacklogService service = service(OCTOBER_30_KST_10);
    measureAll(service);
    failingModes.clear();

    measureAll(service);

    assertThat(backlog("live")).isEqualTo(1);
  }

  @Test
  void 현재_TEST_scope만_한번_측정하고_LIVE_게이지를_만들지_않는다() {
    draft("test", SEPTEMBER, customer(500));
    draft("live", SEPTEMBER, customer(1000));
    InvoiceFinalizationBacklogService service = service(OCTOBER_30_KST_10);
    ScheduledJobResult result = DatabaseScopes.call(DatabaseScope.TEST, service::measure);
    assertThat(result).isEqualTo(new ScheduledJobResult(12, 0, 0));
    assertThat(backlog("test")).isEqualTo(1);
    assertThat(meterRegistry.find(BACKLOG).tag("environment", "live").gauge()).isNull();
    assertThat(DatabaseScopes.current()).isEqualTo(DatabaseScope.SHARED);
  }

  @Test
  void SHARED에서는_legacy_환경을_만들어_청구_적체를_조회하지_않는다() {
    assertThatThrownBy(() -> service(OCTOBER_30_KST_10).measure())
        .isInstanceOf(IllegalStateException.class);
    assertThat(meterRegistry.find(BACKLOG).gauges()).isEmpty();
  }

  private InvoiceFinalizationBacklogService service(Instant now) {
    return new InvoiceFinalizationBacklogService(
        organizationRepository,
        organizationEnvironments,
        draftInvoiceService,
        invoiceRepository,
        meterRegistry,
        Clock.fixed(now, ZoneOffset.UTC),
        unitTransactions());
  }

  private static com.meterengine.global.database.ScopeTransactions unitTransactions() {
    var manager = mock(org.springframework.transaction.PlatformTransactionManager.class);
    when(manager.getTransaction(any()))
        .thenAnswer(
            invocation -> new org.springframework.transaction.support.SimpleTransactionStatus());
    return new com.meterengine.global.database.ScopeTransactions(manager);
  }

  private static ScheduledJobResult measureAll(InvoiceFinalizationBacklogService service) {
    int processed = 0, skipped = 0, failed = 0;
    for (DatabaseScope scope : List.of(DatabaseScope.TEST, DatabaseScope.LIVE)) {
      ScheduledJobResult result = DatabaseScopes.call(scope, service::measure);
      processed += result.processedCount();
      skipped += result.skippedCount();
      failed += result.failedCount();
    }
    return new ScheduledJobResult(processed, skipped, failed);
  }

  private void activeOrganization(
      UUID organizationId, UUID liveEnvironmentId, OffsetDateTime createdAt) {
    Organization organization = mock(Organization.class);
    when(organization.getId()).thenReturn(organizationId);
    when(organization.getCreatedAt()).thenReturn(createdAt);
    organizations.add(organization);
    when(organizationEnvironments.list(organizationId))
        .thenReturn(
            List.of(
                new BusinessEnvironment(organizationId, UUID.randomUUID(), "test"),
                new BusinessEnvironment(organizationId, liveEnvironmentId, "live")));
  }

  private void draft(String mode, YearMonth month, DraftInvoiceCustomer... customers) {
    draftsByModeAndMonth
        .computeIfAbsent(mode, key -> new HashMap<>())
        .put(month, List.of(customers));
  }

  private double backlog(String environment) {
    return meterRegistry.get(BACKLOG).tag("environment", environment).gauge().value();
  }

  private double oldestCutoff(String environment) {
    return meterRegistry.get(OLDEST_CUTOFF).tag("environment", environment).gauge().value();
  }

  private DraftInvoiceResponse stubbedDraft(InvocationOnMock invocation) {
    String mode = String.valueOf(BusinessEnvironmentContext.mode());
    if (failingOrganizationIds.contains(invocation.<UUID>getArgument(0))
        || failingModes.contains(mode)) {
      throw new IllegalStateException("집계 실패");
    }
    List<DraftInvoiceCustomer> customers =
        draftsByModeAndMonth
            .getOrDefault(mode, Map.of())
            .getOrDefault(invocation.<YearMonth>getArgument(1), List.of());
    return new DraftInvoiceResponse(
        "2026-09",
        OffsetDateTime.parse("2026-10-30T10:00:00+09:00"),
        customers.stream().mapToLong(DraftInvoiceCustomer::amount).sum(),
        customers);
  }

  private static DraftInvoiceCustomer customer(long amount) {
    return new DraftInvoiceCustomer(
        UUID.randomUUID(), "고객", amount, List.of(), List.of(), List.of());
  }
}
