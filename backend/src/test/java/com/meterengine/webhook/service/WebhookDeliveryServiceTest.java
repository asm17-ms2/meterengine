package com.meterengine.webhook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meterengine.audit.service.AuditLogService;
import com.meterengine.global.crypto.EncryptedStringConverter;
import com.meterengine.webhook.client.WebhookClient;
import com.meterengine.webhook.dto.WebhookClaim;
import com.meterengine.webhook.dto.WebhookOutcome;
import com.meterengine.webhook.repository.WebhookDeliveryRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class WebhookDeliveryServiceTest {
  @Test
  void 실행_시간이_끝나면_추가로_선점하지_않고_다음_실행에서_남은_전송을_처리한다() {
    WebhookDeliveryRepository repository = mock(WebhookDeliveryRepository.class);
    EncryptedStringConverter converter = mock(EncryptedStringConverter.class);
    WebhookSignatureService signer = mock(WebhookSignatureService.class);
    WebhookClient client = mock(WebhookClient.class);
    AtomicLong now = new AtomicLong();
    WebhookClaim first = claim();
    WebhookClaim second = claim();
    when(repository.claim()).thenReturn(Optional.of(first), Optional.of(second), Optional.empty());
    when(repository.complete(any(), any())).thenReturn(true);
    when(converter.convertToEntityAttribute(anyString())).thenReturn("whsec_secret");
    when(signer.sign(anyString(), anyString(), anyString(), anyString()))
        .thenReturn("v1,signature");
    when(client.send(anyString(), anyString(), anyString(), anyString(), anyString()))
        .thenAnswer(
            invocation -> {
              now.set(15_000_000_001L);
              return new WebhookOutcome(200, null, 15_000);
            });
    WebhookDeliveryService service =
        new WebhookDeliveryService(
            repository,
            mock(WebhookEndpointService.class),
            converter,
            signer,
            client,
            mock(AuditLogService.class),
            new SimpleMeterRegistry(),
            now::get);
    assertThat(service.deliver().processedCount()).isEqualTo(1);
    verify(repository, times(1)).claim();
    verify(repository).complete(first, new WebhookOutcome(200, null, 15_000));
    assertThat(service.deliver().processedCount()).isEqualTo(1);
    verify(repository, times(3)).claim();
    verify(repository).complete(second, new WebhookOutcome(200, null, 15_000));
  }

  private static WebhookClaim claim() {
    return new WebhookClaim(
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        "https://example.com/webhook",
        "v1:cipher",
        "{}",
        true,
        false,
        1,
        OffsetDateTime.now());
  }
}
