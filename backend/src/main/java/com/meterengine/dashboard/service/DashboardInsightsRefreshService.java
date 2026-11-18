package com.meterengine.dashboard.service;

import com.meterengine.dashboard.client.DashboardInsightsStore;
import com.meterengine.dashboard.dto.DashboardInsightsKey;
import com.meterengine.global.security.BusinessEnvironment;
import com.meterengine.global.security.BusinessEnvironmentContext;
import com.meterengine.global.security.OrganizationEnvironments;
import com.meterengine.global.time.BillingMonth;
import com.meterengine.organization.entity.Organization;
import com.meterengine.organization.repository.OrganizationRepository;
import com.meterengine.scheduling.dto.ScheduledJobResult;
import java.time.Clock;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DashboardInsightsRefreshService {
  private static final Logger log = LoggerFactory.getLogger(DashboardInsightsRefreshService.class);
  private final OrganizationRepository organizationRepository;
  private final OrganizationEnvironments organizationEnvironments;
  private final DashboardInsightsCalculationService dashboardInsightsCalculationService;
  private final DashboardInsightsStore dashboardInsightsStore;
  private final Clock clock;

  DashboardInsightsRefreshService(
      OrganizationRepository organizationRepository,
      OrganizationEnvironments organizationEnvironments,
      DashboardInsightsCalculationService dashboardInsightsCalculationService,
      DashboardInsightsStore dashboardInsightsStore,
      Clock clock) {
    this.organizationRepository = organizationRepository;
    this.organizationEnvironments = organizationEnvironments;
    this.dashboardInsightsCalculationService = dashboardInsightsCalculationService;
    this.dashboardInsightsStore = dashboardInsightsStore;
    this.clock = clock;
  }

  /** 모든 조직의 이번 달 인사이트를 업무 환경마다 계산해 저장소에 올린다. 한 조직이 실패해도 나머지는 계속한다. */
  public ScheduledJobResult refresh() {
    YearMonth month = YearMonth.from(BillingMonth.now(clock));
    int processed = 0;
    int failed = 0;
    for (Organization organization :
        organizationRepository.findByStatusOrderByCreatedAtAscIdAsc(Organization.ACTIVE)) {
      for (BusinessEnvironment environment : environments(organization)) {
        try {
          BusinessEnvironmentContext.run(environment, () -> store(organization, month));
          processed++;
        } catch (RuntimeException exception) {
          failed++;
          log.error(
              "dashboard insights refresh failed for organization {} environment {}",
              organization.getId(),
              environment.mode(),
              exception);
        }
      }
    }
    return new ScheduledJobResult(processed, 0, failed);
  }

  // 환경 구분 이전 범위와 그 조직의 환경을 따로 계산한다.
  private List<BusinessEnvironment> environments(Organization organization) {
    List<BusinessEnvironment> environments = new ArrayList<>();
    environments.add(new BusinessEnvironment(organization.getId(), null, null));
    environments.addAll(organizationEnvironments.list(organization.getId()));
    return environments;
  }

  private void store(Organization organization, YearMonth month) {
    dashboardInsightsStore.save(
        new DashboardInsightsKey(organization.getId(), BusinessEnvironmentContext.scopeId(), month),
        dashboardInsightsCalculationService.calculate(organization.getId(), month));
  }
}
