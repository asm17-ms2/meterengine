package com.meterengine.webhook.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.meterengine.audit.service.AuditLogService;
import com.meterengine.global.crypto.EncryptedStringConverter;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.InvalidRequestException;
import com.meterengine.webhook.client.WebhookAddressPolicy;
import com.meterengine.webhook.dto.CreateWebhookEndpointRequest;
import com.meterengine.webhook.repository.WebhookEndpointRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;

class WebhookEndpointServiceTest {
  @Test
  void 운영_정책은_HTTP와_차단_주소_URL을_invalid_webhook_url로_거절하고_저장하지_않는다() {
    WebhookEndpointRepository repository = mock(WebhookEndpointRepository.class);
    WebhookEndpointService service =
        new WebhookEndpointService(
            repository,
            new WebhookAddressPolicy(false),
            mock(EncryptedStringConverter.class),
            mock(WebhookEventService.class),
            mock(AuditLogService.class),
            TransactionOperations.withoutTransaction());
    for (String url :
        new String[] {
          "http://203.0.113.10/hook", "https://127.0.0.1/hook", "https://169.254.169.254/latest"
        }) {
      assertThatThrownBy(
              () ->
                  service.create(
                      UUID.randomUUID(),
                      new CreateWebhookEndpointRequest(url, List.of("payment_succeeded"), null)))
          .isInstanceOf(InvalidRequestException.class)
          .extracting(exception -> ((InvalidRequestException) exception).getErrorCode())
          .isEqualTo(ErrorCode.INVALID_WEBHOOK_URL);
    }
    verifyNoInteractions(repository);
  }
}
