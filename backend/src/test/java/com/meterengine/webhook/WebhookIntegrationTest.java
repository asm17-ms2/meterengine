package com.meterengine.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.meterengine.TestCredentials;
import com.meterengine.TestcontainersConfiguration;
import com.meterengine.global.error.ConflictException;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.webhook.client.WebhookAddressPolicy;
import com.meterengine.webhook.dto.CreateWebhookEndpointRequest;
import com.meterengine.webhook.dto.UpdateWebhookEndpointRequest;
import com.meterengine.webhook.dto.WebhookClaim;
import com.meterengine.webhook.dto.WebhookEndpointResponse;
import com.meterengine.webhook.dto.WebhookOutcome;
import com.meterengine.webhook.entity.WebhookEventType;
import com.meterengine.webhook.repository.WebhookDeliveryRepository;
import com.meterengine.webhook.service.WebhookDeliveryService;
import com.meterengine.webhook.service.WebhookEndpointService;
import com.meterengine.webhook.service.WebhookEventService;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(
    properties = {
      "meterengine.webhook.enabled=false",
      "meterengine.webhook.allow-private-network=true"
    })
@Import(TestcontainersConfiguration.class)
class WebhookIntegrationTest {
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private WebhookEndpointService webhookEndpointService;
  @Autowired private WebhookEventService webhookEventService;
  @Autowired private WebhookDeliveryService webhookDeliveryService;
  @Autowired private WebhookDeliveryRepository webhookDeliveryRepository;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private WebApplicationContext webApplicationContext;
  @MockitoSpyBean private WebhookAddressPolicy webhookAddressPolicy;
  private HttpServer httpServer;
  private UUID organizationId;
  private WebhookEndpointResponse endpoint;
  private final AtomicInteger responseStatus = new AtomicInteger(200);
  private final List<Received> received = new CopyOnWriteArrayList<>();

  @BeforeEach
  void setUp() throws Exception {
    organizationId = UUID.randomUUID();
    TestCredentials.issueApiKey(jdbcTemplate, organizationId, "admin");
    httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    httpServer.createContext(
        "/webhook",
        exchange -> {
          received.add(
              new Received(
                  exchange.getRequestHeaders().getFirst("webhook-id"),
                  exchange.getRequestHeaders().getFirst("webhook-timestamp"),
                  exchange.getRequestHeaders().getFirst("webhook-signature"),
                  new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
          exchange.sendResponseHeaders(responseStatus.get(), -1);
          exchange.close();
        });
    httpServer.start();
    endpoint =
        webhookEndpointService.create(
            organizationId,
            new CreateWebhookEndpointRequest(
                "http://127.0.0.1:" + httpServer.getAddress().getPort() + "/webhook",
                List.of("payment_succeeded", "unbilled_usage_detected"),
                "테스트 수신기"));
  }

  @AfterEach
  void tearDown() {
    httpServer.stop(0);
  }

  @Test
  void 생성_때만_비밀값을_주고_서명한_시험_이벤트가_실제로_도착한다() throws Exception {
    assertThat(endpoint.secret()).startsWith("whsec_");
    assertThat(webhookEndpointService.get(organizationId, endpoint.id()).secret()).isNull();
    String stored =
        jdbcTemplate.queryForObject(
            "SELECT secret FROM webhook_endpoint WHERE id = ?", String.class, endpoint.id());
    assertThat(stored).startsWith("v1:").doesNotContain(endpoint.secret());
    UUID eventId = webhookEndpointService.test(organizationId, endpoint.id());
    assertThat(webhookDeliveryService.deliver().processedCount()).isGreaterThanOrEqualTo(1);
    assertThat(received).hasSize(1);
    Received message = received.getFirst();
    assertThat(message.id()).isEqualTo(eventId.toString());
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(
        new SecretKeySpec(
            Base64.getDecoder().decode(endpoint.secret().substring(6)), "HmacSHA256"));
    String expected =
        "v1,"
            + Base64.getEncoder()
                .encodeToString(
                    mac.doFinal(
                        (message.id() + "." + message.timestamp() + "." + message.body())
                            .getBytes(StandardCharsets.UTF_8)));
    assertThat(message.signature()).isEqualTo(expected);
    assertThat(message.body()).contains("webhook_test");
    assertThat(
            webhookDeliveryService
                .list(organizationId, endpoint.id(), null, 0, 20)
                .deliveries()
                .getFirst()
                .status())
        .isEqualTo("done");
  }

  @Test
  void 수신_주소의_DNS_조회는_트랜잭션_밖에서_해_DB_커넥션을_쥐지_않는다() throws Exception {
    List<Boolean> transactionActiveOnResolve = new CopyOnWriteArrayList<>();
    doAnswer(
            invocation -> {
              transactionActiveOnResolve.add(
                  TransactionSynchronizationManager.isActualTransactionActive());
              return invocation.callRealMethod();
            })
        .when(webhookAddressPolicy)
        .resolve(anyString());
    String url = "http://127.0.0.1:" + httpServer.getAddress().getPort() + "/webhook";

    WebhookEndpointResponse created =
        webhookEndpointService.create(
            organizationId, new CreateWebhookEndpointRequest(url, List.of("webhook_test"), null));
    webhookEndpointService.update(
        organizationId,
        created.id(),
        new UpdateWebhookEndpointRequest(url, List.of("webhook_test"), true, null));

    assertThat(transactionActiveOnResolve).containsExactly(false, false);
  }

  @Test
  void 발행은_호출자_트랜잭션이_필수이고_롤백되면_아웃박스도_없다() {
    assertThatThrownBy(
            () ->
                webhookEventService.publish(
                    organizationId, WebhookEventType.PAYMENT_SUCCEEDED, Map.of()))
        .isInstanceOf(IllegalTransactionStateException.class);
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> {
              webhookEventService.publish(
                  organizationId, WebhookEventType.PAYMENT_SUCCEEDED, Map.of());
              status.setRollbackOnly();
            });
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM webhook_event WHERE organization_id = ?",
                Integer.class,
                organizationId))
        .isZero();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM webhook_delivery WHERE organization_id = ?",
                Integer.class,
                organizationId))
        .isZero();
  }

  @Test
  void 실패를_여섯_번까지만_재시도하고_수동_재시도에도_이력을_보존한다() {
    responseStatus.set(500);
    UUID eventId = webhookEndpointService.test(organizationId, endpoint.id());
    long[] retrySeconds = {60, 300, 1800, 7200, 28800};
    for (int attempt = 0; attempt < 6; attempt++) {
      pullNextAttempt(eventId);
      assertThat(webhookDeliveryService.deliver().failedCount()).isZero();
      assertThat(received).hasSize(attempt + 1);
      if (attempt < retrySeconds.length) {
        assertThat(
                jdbcTemplate.queryForObject(
                    "SELECT extract(epoch FROM next_attempt_at - clock_timestamp()) FROM webhook_delivery WHERE organization_id = ? AND webhook_event_id = ?",
                    Double.class,
                    organizationId,
                    eventId))
            .isCloseTo(retrySeconds[attempt], within(5.0));
      }
    }
    var delivery =
        webhookDeliveryService
            .list(organizationId, endpoint.id(), "failed", 0, 20)
            .deliveries()
            .getFirst();
    assertThat(delivery.attemptCount()).isEqualTo(6);
    assertThat(delivery.attempts()).hasSize(6);
    assertThat(received).hasSize(6);
    webhookDeliveryService.deliver();
    assertThat(received).hasSize(6);
    responseStatus.set(200);
    webhookDeliveryService.retry(organizationId, endpoint.id(), delivery.id());
    webhookDeliveryService.deliver();
    var retried =
        webhookDeliveryService
            .list(organizationId, endpoint.id(), "done", 0, 20)
            .deliveries()
            .getFirst();
    assertThat(retried.attempts()).hasSize(7);
    assertThat(received.stream().map(Received::id).distinct().toList())
        .containsExactly(eventId.toString());
  }

  @Test
  void 전송기록은_페이지의_전송마다_자기_시도_이력만_시각순으로_돌려준다() {
    responseStatus.set(500);
    UUID failingEventId = webhookEndpointService.test(organizationId, endpoint.id());
    for (int attempt = 0; attempt < 2; attempt++) {
      jdbcTemplate.update(
          "UPDATE webhook_delivery SET next_attempt_at = clock_timestamp() WHERE organization_id = ? AND webhook_event_id = ?",
          organizationId,
          failingEventId);
      webhookDeliveryService.deliver();
    }
    responseStatus.set(200);
    UUID firstEventId = webhookEndpointService.test(organizationId, endpoint.id());
    UUID secondEventId = webhookEndpointService.test(organizationId, endpoint.id());
    webhookDeliveryService.deliver();

    var deliveries =
        webhookDeliveryService.list(organizationId, endpoint.id(), null, 0, 20).deliveries();

    assertThat(deliveries)
        .extracting(delivery -> delivery.webhookEventId())
        .containsExactlyInAnyOrder(failingEventId, firstEventId, secondEventId);
    for (var delivery : deliveries) {
      assertThat(delivery.attempts()).hasSize(delivery.attemptCount());
      assertThat(delivery.attempts()).extracting(attempt -> attempt.attemptedAt()).isSorted();
    }
    var failing =
        deliveries.stream()
            .filter(delivery -> delivery.webhookEventId().equals(failingEventId))
            .findFirst()
            .orElseThrow();
    assertThat(failing.attempts())
        .extracting(attempt -> attempt.responseStatus())
        .containsExactly(500, 500);
    assertThat(
            webhookDeliveryService
                .list(organizationId, endpoint.id(), "failed", 0, 20)
                .deliveries())
        .isEmpty();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pg_indexes WHERE tablename = 'webhook_delivery_attempt' AND indexname = 'webhook_delivery_attempt_webhook_delivery_lookup'",
                Integer.class))
        .isOne();
  }

  @Test
  void 두_워커의_claim이_겹치지_않고_만료된_워커의_결과가_새_결과를_덮지_않는다() throws Exception {
    jdbcTemplate.update(
        "UPDATE webhook_delivery SET next_attempt_at = clock_timestamp() + interval '1 day' WHERE status = 'pending' AND organization_id <> ?",
        organizationId);
    for (int i = 0; i < 20; i++) {
      webhookEndpointService.test(organizationId, endpoint.id());
    }
    List<WebhookClaim> claims = new ArrayList<>();
    try (var executor = Executors.newFixedThreadPool(2)) {
      var first =
          executor.submit(
              () -> {
                List<WebhookClaim> values = new ArrayList<>();
                for (int i = 0; i < 10; i++) {
                  values.add(webhookDeliveryRepository.claim().orElseThrow());
                }
                return values;
              });
      var second =
          executor.submit(
              () -> {
                List<WebhookClaim> values = new ArrayList<>();
                for (int i = 0; i < 10; i++) {
                  values.add(webhookDeliveryRepository.claim().orElseThrow());
                }
                return values;
              });
      claims.addAll(first.get(15, TimeUnit.SECONDS));
      claims.addAll(second.get(15, TimeUnit.SECONDS));
    }
    assertThat(claims).allMatch(claim -> claim.organizationId().equals(organizationId));
    assertThat(claims.stream().map(WebhookClaim::id).distinct().count()).isEqualTo(20);
    WebhookClaim old = claims.getFirst();
    jdbcTemplate.update(
        "UPDATE webhook_delivery SET next_attempt_at = clock_timestamp() WHERE id = ?", old.id());
    WebhookClaim replacement = webhookDeliveryRepository.claim().orElseThrow();
    assertThat(replacement.id()).isEqualTo(old.id());
    assertThat(webhookDeliveryRepository.complete(old, new WebhookOutcome(200, null, 1))).isEmpty();
    assertThat(webhookDeliveryRepository.complete(replacement, new WebhookOutcome(200, null, 1)))
        .contains("done");
    for (WebhookClaim claim : claims.subList(1, claims.size())) {
      webhookDeliveryRepository.complete(claim, new WebhookOutcome(200, null, 1));
    }
  }

  @Test
  void 비활성화한_엔드포인트의_대기_전송은_보내지_않고_재시도할_수_없다() {
    UUID eventId = failOnce();
    webhookEndpointService.update(
        organizationId,
        endpoint.id(),
        new UpdateWebhookEndpointRequest(
            endpoint.url(), endpoint.eventTypes(), false, endpoint.description()));
    pullNextAttempt(eventId);
    webhookDeliveryService.deliver();

    assertThat(received).hasSize(1);
    var delivery =
        webhookDeliveryService
            .list(organizationId, endpoint.id(), "failed", 0, 20)
            .deliveries()
            .getFirst();
    assertThat(delivery.webhookEventId()).isEqualTo(eventId);
    assertThat(delivery.failureReason()).isEqualTo("endpoint_disabled");
    assertThat(delivery.nextAttemptAt()).isNull();
    assertThat(delivery.attempts())
        .extracting(attempt -> attempt.failureReason())
        .containsExactly("unexpected_status", "endpoint_disabled");
    assertThatThrownBy(
            () -> webhookDeliveryService.retry(organizationId, endpoint.id(), delivery.id()))
        .isInstanceOf(ConflictException.class)
        .extracting(exception -> ((ConflictException) exception).getErrorCode())
        .isEqualTo(ErrorCode.WEBHOOK_DELIVERY_NOT_RETRYABLE);
  }

  @Test
  void 삭제한_엔드포인트의_대기_전송은_보내지_않고_다시_대기시키지_않는다() {
    UUID eventId = failOnce();
    webhookEndpointService.delete(organizationId, endpoint.id());
    pullNextAttempt(eventId);
    webhookDeliveryService.deliver();

    assertThat(received).hasSize(1);
    Map<String, Object> delivery =
        jdbcTemplate.queryForMap(
            "SELECT id, status, failure_reason, next_attempt_at FROM webhook_delivery WHERE organization_id = ? AND webhook_event_id = ?",
            organizationId,
            eventId);
    assertThat(delivery)
        .containsEntry("status", "failed")
        .containsEntry("failure_reason", "endpoint_disabled");
    assertThat(delivery.get("next_attempt_at")).isNull();
    assertThat(
            webhookDeliveryRepository.retry(
                organizationId, endpoint.id(), (UUID) delivery.get("id")))
        .isZero();
  }

  @Test
  void 마지막_시도_중_선점이_만료된_전송은_lease_expired로_실패하고_수동_재시도로_되살린다() {
    UUID eventId = webhookEndpointService.test(organizationId, endpoint.id());
    jdbcTemplate.update(
        "UPDATE webhook_delivery SET attempt_count = 6, last_attempted_at = clock_timestamp(), next_attempt_at = clock_timestamp() - interval '1 second', claim_token = gen_random_uuid() WHERE organization_id = ? AND webhook_event_id = ?",
        organizationId,
        eventId);
    webhookDeliveryService.deliver();

    assertThat(received).isEmpty();
    var delivery =
        webhookDeliveryService
            .list(organizationId, endpoint.id(), "failed", 0, 20)
            .deliveries()
            .getFirst();
    assertThat(delivery.webhookEventId()).isEqualTo(eventId);
    assertThat(delivery.failureReason()).isEqualTo("lease_expired");
    assertThat(delivery.nextAttemptAt()).isNull();

    webhookDeliveryService.retry(organizationId, endpoint.id(), delivery.id());
    webhookDeliveryService.deliver();

    assertThat(received).hasSize(1);
    assertThat(
            webhookDeliveryService
                .list(organizationId, endpoint.id(), "done", 0, 20)
                .deliveries()
                .getFirst()
                .webhookEventId())
        .isEqualTo(eventId);
  }

  @Test
  void 비밀값을_풀지_못한_전송은_secret_unavailable_시도로_남기고_재시도_간격을_따른다() {
    String stored =
        jdbcTemplate.queryForObject(
            "SELECT secret FROM webhook_endpoint WHERE id = ?", String.class, endpoint.id());
    jdbcTemplate.update(
        "UPDATE webhook_endpoint SET secret = ? WHERE id = ?",
        "v1:" + Base64.getEncoder().encodeToString(new byte[40]),
        endpoint.id());
    UUID eventId = webhookEndpointService.test(organizationId, endpoint.id());

    assertThat(webhookDeliveryService.deliver().failedCount()).isEqualTo(1);

    assertThat(received).isEmpty();
    var delivery =
        webhookDeliveryService
            .list(organizationId, endpoint.id(), "pending", 0, 20)
            .deliveries()
            .getFirst();
    assertThat(delivery.webhookEventId()).isEqualTo(eventId);
    assertThat(delivery.attemptCount()).isEqualTo(1);
    assertThat(delivery.failureReason()).isEqualTo("secret_unavailable");
    assertThat(delivery.attempts())
        .extracting(attempt -> attempt.failureReason())
        .containsExactly("secret_unavailable");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT extract(epoch FROM next_attempt_at - clock_timestamp()) FROM webhook_delivery WHERE organization_id = ? AND webhook_event_id = ?",
                Double.class,
                organizationId,
                eventId))
        .isCloseTo(60, within(5.0));
    jdbcTemplate.update(
        "UPDATE webhook_endpoint SET secret = ? WHERE id = ?", stored, endpoint.id());
  }

  @Test
  void 전송기록은_test와_live_read_키로_읽고_다른_도입사는_거절한다() {
    MockMvcTester mvc =
        MockMvcTester.from(
            webApplicationContext, builder -> builder.apply(springSecurity()).build());
    for (String mode : new String[] {"test", "live"}) {
      String read = TestCredentials.issueApiKey(jdbcTemplate, organizationId, mode, "read");
      assertThat(
              mvc.get()
                  .uri("/v1/webhook-endpoints/" + endpoint.id() + "/deliveries")
                  .header("Authorization", read)
                  .exchange())
          .hasStatusOk();
      assertThat(
              mvc.post()
                  .uri("/v1/webhook-endpoints/" + endpoint.id() + "/test-events")
                  .header("Authorization", read)
                  .exchange())
          .hasStatus(403);
    }
    String other = TestCredentials.issueApiKey(jdbcTemplate, UUID.randomUUID(), "read");
    assertThat(
            mvc.get()
                .uri("/v1/webhook-endpoints/" + endpoint.id() + "/deliveries")
                .header("Authorization", other)
                .exchange())
        .hasStatus(404);
  }

  private UUID failOnce() {
    responseStatus.set(500);
    UUID eventId = webhookEndpointService.test(organizationId, endpoint.id());
    webhookDeliveryService.deliver();
    assertThat(received).hasSize(1);
    return eventId;
  }

  private void pullNextAttempt(UUID eventId) {
    jdbcTemplate.update(
        "UPDATE webhook_delivery SET next_attempt_at = clock_timestamp() WHERE organization_id = ? AND webhook_event_id = ?",
        organizationId,
        eventId);
  }

  private record Received(String id, String timestamp, String signature, String body) {}
}
