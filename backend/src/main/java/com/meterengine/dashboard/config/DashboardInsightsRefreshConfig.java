package com.meterengine.dashboard.config;

import com.meterengine.dashboard.service.DashboardInsightsRefreshService;
import com.meterengine.dashboard.service.DashboardInsightsSchedule;
import com.meterengine.scheduling.service.ScheduledJobRunService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@ConditionalOnBooleanProperty(name = "meterengine.scheduling.enabled", matchIfMissing = true)
public class DashboardInsightsRefreshConfig {
  private final ScheduledJobRunService scheduledJobRunService;
  private final DashboardInsightsRefreshService dashboardInsightsRefreshService;

  DashboardInsightsRefreshConfig(
      ScheduledJobRunService scheduledJobRunService,
      DashboardInsightsRefreshService dashboardInsightsRefreshService) {
    this.scheduledJobRunService = scheduledJobRunService;
    this.dashboardInsightsRefreshService = dashboardInsightsRefreshService;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void register() {
    scheduledJobRunService.register(DashboardInsightsSchedule.JOB_NAME);
  }

  // 같은 시각의 실행은 서버 한 대만 맡는다. 나머지 서버는 저장소에 올라온 값을 조회 때 읽는다.
  @Scheduled(cron = DashboardInsightsSchedule.REFRESH_CRON, zone = "Asia/Seoul")
  public void refresh() {
    OffsetDateTime slotAt = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MINUTES);
    scheduledJobRunService.run(
        DashboardInsightsSchedule.JOB_NAME, slotAt, dashboardInsightsRefreshService::refresh);
  }
}
