package com.meterengine.webhook.config;

import com.meterengine.scheduling.service.ScheduledJobRunService;
import com.meterengine.webhook.service.WebhookDeliveryService;
import java.time.OffsetDateTime;
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
public class WebhookDeliveryConfig {
  private final ScheduledJobRunService scheduledJobRunService;
  private final WebhookDeliveryService webhookDeliveryService;

  WebhookDeliveryConfig(
      ScheduledJobRunService scheduledJobRunService,
      WebhookDeliveryService webhookDeliveryService) {
    this.scheduledJobRunService = scheduledJobRunService;
    this.webhookDeliveryService = webhookDeliveryService;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void register() {
    scheduledJobRunService.register("webhook_delivery");
  }

  @Scheduled(fixedDelay = 30000, initialDelay = 30000)
  public void deliver() {
    OffsetDateTime now = OffsetDateTime.now();
    OffsetDateTime slotAt = now.withNano(0).withSecond(now.getSecond() < 30 ? 0 : 30);
    scheduledJobRunService.run("webhook_delivery", slotAt, webhookDeliveryService::deliver);
  }
}
