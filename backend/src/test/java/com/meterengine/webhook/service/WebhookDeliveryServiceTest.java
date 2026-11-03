package com.meterengine.webhook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meterengine.audit.service.AuditLogService;
import com.meterengine.global.crypto.EncryptedStringConverter;
import com.meterengine.scheduling.dto.ScheduledJobResult;
import com.meterengine.webhook.client.WebhookClient;
import com.meterengine.webhook.dto.WebhookClaim;
import com.meterengine.webhook.dto.WebhookOutcome;
import com.meterengine.webhook.repository.WebhookDeliveryRepository;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
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
    when(repository.claim(any()))
        .thenReturn(Optional.of(first), Optional.of(second), Optional.empty());
    when(repository.complete(any(), any())).thenReturn(Optional.of("done"));
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
            new WebhookDeliveryMetrics(new SimpleMeterRegistry()),
            now::get);
    assertThat(service.deliver().processedCount()).isEqualTo(1);
    verify(repository, times(1)).claim(any());
    verify(repository).complete(first, new WebhookOutcome(200, null, 15_000));
    assertThat(service.deliver().processedCount()).isEqualTo(1);
    verify(repository, times(3)).claim(any());
    verify(repository).complete(second, new WebhookOutcome(200, null, 15_000));
  }

  @Test
  void 수신_실패로_대기에_남은_전송은_시도_횟수와_무관하게_재시도로_세고_작업_실패로_세지_않는다() {
    WebhookDeliveryRepository repository = mock(WebhookDeliveryRepository.class);
    WebhookClient client = mock(WebhookClient.class);
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    WebhookClaim claim = claim("v1:cipher", true, 6);
    when(repository.claim(any())).thenReturn(Optional.of(claim), Optional.empty());
    when(repository.complete(any(), any())).thenReturn(Optional.of("pending"));
    when(client.send(anyString(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(new WebhookOutcome(503, "unexpected_status", 10));

    ScheduledJobResult result = service(repository, client, meterRegistry).deliver();

    assertThat(result.failedCount()).isZero();
    assertThat(result.processedCount()).isEqualTo(1);
    assertThat(deliveryCount(meterRegistry, "retry", "unexpected_status")).isEqualTo(1);
    assertThat(meterRegistry.find("meterengine.webhook.delivery").tag("result", "failed").counter())
        .isNull();
  }

  @Test
  void 비활성_엔드포인트로_끝낸_전송은_endpoint_disabled_사유로_센다() {
    WebhookDeliveryRepository repository = mock(WebhookDeliveryRepository.class);
    WebhookClient client = mock(WebhookClient.class);
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    when(repository.claim(any()))
        .thenReturn(Optional.of(claim("v1:cipher", false, 1)), Optional.empty());
    when(repository.complete(any(), any())).thenReturn(Optional.of("failed"));

    ScheduledJobResult result = service(repository, client, meterRegistry).deliver();

    assertThat(result.failedCount()).isZero();
    assertThat(deliveryCount(meterRegistry, "failed", "endpoint_disabled")).isEqualTo(1);
    verify(client, never()).send(anyString(), anyString(), anyString(), anyString(), anyString());
  }

  @Test
  void 비밀값을_풀지_못한_전송은_secret_unavailable로_기록하고_실패로_센_뒤_다음_전송을_보낸다() {
    WebhookDeliveryRepository repository = mock(WebhookDeliveryRepository.class);
    WebhookClient client = mock(WebhookClient.class);
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    WebhookClaim broken = claim("v1:broken", true, 1);
    WebhookClaim next = claim("v1:cipher", true, 1);
    when(repository.claim(any()))
        .thenReturn(Optional.of(broken), Optional.of(next), Optional.empty());
    when(repository.complete(eq(broken), any())).thenReturn(Optional.of("pending"));
    when(repository.complete(eq(next), any())).thenReturn(Optional.of("done"));
    when(client.send(anyString(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(new WebhookOutcome(200, null, 10));

    ScheduledJobResult result = service(repository, client, meterRegistry).deliver();

    assertThat(result.failedCount()).isEqualTo(1);
    assertThat(result.processedCount()).isEqualTo(1);
    verify(repository).complete(broken, new WebhookOutcome(null, "secret_unavailable", 0));
    verify(repository).complete(eq(next), any());
    verify(client, times(1)).send(anyString(), anyString(), anyString(), anyString(), anyString());
    assertThat(deliveryCount(meterRegistry, "retry", "secret_unavailable")).isEqualTo(1);
  }

  @Test
  void 전달에_성공하면_웹훅_이벤트가_생긴_때부터의_소요_시간을_기록한다() {
    WebhookDeliveryRepository repository = mock(WebhookDeliveryRepository.class);
    WebhookClient client = mock(WebhookClient.class);
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    WebhookClaim claim = claim("v1:cipher", true, 2, OffsetDateTime.now().minusMinutes(5));
    when(repository.claim(any())).thenReturn(Optional.of(claim), Optional.empty());
    when(repository.complete(any(), any())).thenReturn(Optional.of("done"));
    when(client.send(anyString(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(new WebhookOutcome(200, null, 10));

    service(repository, client, meterRegistry).deliver();

    assertThat(
            meterRegistry
                .get(WebhookDeliveryMetrics.LATENCY_TIMER_NAME)
                .tag("environment", "legacy")
                .timer()
                .totalTime(TimeUnit.SECONDS))
        .isGreaterThanOrEqualTo(300);
  }

  @Test
  void 재시도로_남은_전송은_전달_소요_시간을_기록하지_않는다() {
    WebhookDeliveryRepository repository = mock(WebhookDeliveryRepository.class);
    WebhookClient client = mock(WebhookClient.class);
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    when(repository.claim(any())).thenReturn(Optional.of(claim()), Optional.empty());
    when(repository.complete(any(), any())).thenReturn(Optional.of("pending"));
    when(client.send(anyString(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(new WebhookOutcome(503, "unexpected_status", 10));

    service(repository, client, meterRegistry).deliver();

    assertThat(meterRegistry.find(WebhookDeliveryMetrics.LATENCY_TIMER_NAME).timer()).isNull();
  }

  @Test
  void 수신_주소에_보낸_전송은_걸린_시간을_수신_측_응답_시간으로_기록한다() {
    WebhookDeliveryRepository repository = mock(WebhookDeliveryRepository.class);
    WebhookClient client = mock(WebhookClient.class);
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    when(repository.claim(any())).thenReturn(Optional.of(claim()), Optional.empty());
    when(repository.complete(any(), any())).thenReturn(Optional.of("pending"));
    when(client.send(anyString(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(new WebhookOutcome(503, "unexpected_status", 1500));

    service(repository, client, meterRegistry).deliver();

    Timer responseTimer =
        meterRegistry
            .get(WebhookDeliveryMetrics.RESPONSE_TIMER_NAME)
            .tag("environment", "legacy")
            .timer();
    assertThat(responseTimer.count()).isEqualTo(1);
    assertThat(responseTimer.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(1500);
  }

  @Test
  void 비활성_엔드포인트로_끝낸_전송은_수신_측_응답_시간을_기록하지_않는다() {
    WebhookDeliveryRepository repository = mock(WebhookDeliveryRepository.class);
    WebhookClient client = mock(WebhookClient.class);
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    when(repository.claim(any()))
        .thenReturn(Optional.of(claim("v1:cipher", false, 1)), Optional.empty());
    when(repository.complete(any(), any())).thenReturn(Optional.of("failed"));

    service(repository, client, meterRegistry).deliver();

    assertThat(meterRegistry.find(WebhookDeliveryMetrics.RESPONSE_TIMER_NAME).timer()).isNull();
  }

  @Test
  void 처음_보내는_전송의_시도를_첫_시도로_센다() {
    assertThat(firstAttemptTagOf(claim())).isEqualTo("true");
  }

  @Test
  void 두_번째_시도부터는_첫_시도로_세지_않는다() {
    assertThat(firstAttemptTagOf(claim("v1:cipher", true, 2))).isEqualTo("false");
  }

  @Test
  void 수동_재시도로_시도_횟수가_되돌아간_전송은_첫_시도로_세지_않는다() {
    WebhookClaim retried = claim("v1:cipher", true, 1, OffsetDateTime.now(), true);

    assertThat(firstAttemptTagOf(retried)).isEqualTo("false");
  }

  private static String firstAttemptTagOf(WebhookClaim claim) {
    WebhookDeliveryRepository repository = mock(WebhookDeliveryRepository.class);
    WebhookClient client = mock(WebhookClient.class);
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    when(repository.claim(any())).thenReturn(Optional.of(claim), Optional.empty());
    when(repository.complete(any(), any())).thenReturn(Optional.of("done"));
    when(client.send(anyString(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(new WebhookOutcome(200, null, 10));

    service(repository, client, meterRegistry).deliver();

    return meterRegistry.get("meterengine.webhook.delivery").counters().stream()
        .filter(counter -> counter.count() > 0)
        .findFirst()
        .orElseThrow()
        .getId()
        .getTag("first_attempt");
  }

  private static WebhookDeliveryService service(
      WebhookDeliveryRepository repository, WebhookClient client, SimpleMeterRegistry registry) {
    EncryptedStringConverter converter = mock(EncryptedStringConverter.class);
    when(converter.convertToEntityAttribute("v1:cipher")).thenReturn("whsec_secret");
    when(converter.convertToEntityAttribute("v1:broken"))
        .thenThrow(new IllegalStateException("decryption failed"));
    WebhookSignatureService signer = mock(WebhookSignatureService.class);
    when(signer.sign(anyString(), anyString(), anyString(), anyString()))
        .thenReturn("v1,signature");
    return new WebhookDeliveryService(
        repository,
        mock(WebhookEndpointService.class),
        converter,
        signer,
        client,
        mock(AuditLogService.class),
        new WebhookDeliveryMetrics(registry),
        System::nanoTime);
  }

  private static double deliveryCount(
      SimpleMeterRegistry meterRegistry, String result, String reason) {
    return meterRegistry
        .get("meterengine.webhook.delivery")
        .tag("result", result)
        .tag("reason", reason)
        .counter()
        .count();
  }

  private static WebhookClaim claim() {
    return claim("v1:cipher", true, 1);
  }

  private static WebhookClaim claim(String secret, boolean enabled, int attemptCount) {
    return claim(secret, enabled, attemptCount, OffsetDateTime.now());
  }

  private static WebhookClaim claim(
      String secret, boolean enabled, int attemptCount, OffsetDateTime eventCreatedAt) {
    return claim(secret, enabled, attemptCount, eventCreatedAt, false);
  }

  private static WebhookClaim claim(
      String secret,
      boolean enabled,
      int attemptCount,
      OffsetDateTime eventCreatedAt,
      boolean attemptedBefore) {
    return new WebhookClaim(
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        "https://example.com/webhook",
        secret,
        "{}",
        enabled,
        false,
        attemptCount,
        OffsetDateTime.now(),
        null,
        eventCreatedAt,
        attemptedBefore);
  }
}
