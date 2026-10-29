package com.meterengine.notification.config;

import com.meterengine.notification.service.UnbilledUsageNotificationService;
import com.meterengine.scheduling.service.ScheduledJobRunService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@ConditionalOnProperty(
    name = "meterengine.webhook.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class UnbilledUsageNotificationConfig {
  private final ScheduledJobRunService scheduledJobRunService;
  private final UnbilledUsageNotificationService unbilledUsageNotificationService;

  UnbilledUsageNotificationConfig(
      ScheduledJobRunService scheduledJobRunService,
      UnbilledUsageNotificationService unbilledUsageNotificationService) {
    this.scheduledJobRunService = scheduledJobRunService;
    this.unbilledUsageNotificationService = unbilledUsageNotificationService;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void register() {
    scheduledJobRunService.register("unbilled_usage_report");
  }

  @Scheduled(
      cron = "${meterengine.notification.unbilled-usage-cron:0 0 9 * * *}",
      zone = "Asia/Seoul")
  public void report() {
    OffsetDateTime slotAt = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MINUTES);
    scheduledJobRunService.run(
        "unbilled_usage_report", slotAt, unbilledUsageNotificationService::report);
  }
}
