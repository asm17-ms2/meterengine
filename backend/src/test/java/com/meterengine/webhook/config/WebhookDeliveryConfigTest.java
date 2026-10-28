package com.meterengine.webhook.config;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.meterengine.scheduling.service.ScheduledJobRunService;
import com.meterengine.webhook.service.WebhookDeliveryService;
import org.junit.jupiter.api.Test;

class WebhookDeliveryConfigTest {
  @Test
  void 슬롯을_다른_인스턴스가_잡아_발송하지_않아도_적체_지표를_갱신한다() {
    ScheduledJobRunService scheduledJobRunService = mock(ScheduledJobRunService.class);
    WebhookDeliveryService webhookDeliveryService = mock(WebhookDeliveryService.class);

    new WebhookDeliveryConfig(scheduledJobRunService, webhookDeliveryService).deliver();

    verify(webhookDeliveryService).measure();
  }
}
