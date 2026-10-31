package com.meterengine.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.jayway.jsonpath.JsonPath;
import com.meterengine.TestCredentials;
import com.meterengine.dashboard.service.OperationsService;
import com.meterengine.global.error.NotFoundException;
import com.meterengine.global.security.BusinessEnvironmentContext;
import com.meterengine.global.security.BusinessEnvironmentInterceptor;
import com.meterengine.webhook.dto.CreateWebhookEndpointRequest;
import com.meterengine.webhook.dto.WebhookEndpointResponse;
import com.meterengine.webhook.entity.WebhookEventType;
import com.meterengine.webhook.repository.WebhookDeliveryRepository;
import com.meterengine.webhook.service.WebhookDeliveryService;
import com.meterengine.webhook.service.WebhookEndpointService;
import com.meterengine.webhook.service.WebhookEventService;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

class WebhookEnvironmentIntegrationTest extends com.meterengine.SideEffectTestSupport {
  @Autowired JdbcTemplate jdbc;
  @Autowired WebhookEndpointService endpoints;
  @Autowired WebhookEventService events;
  @Autowired WebhookDeliveryService deliveries;
  @Autowired WebhookDeliveryRepository deliveryRepository;
  @Autowired PlatformTransactionManager transactions;
  @Autowired WebApplicationContext application;
  @Autowired MeterRegistry meterRegistry;
  @Autowired OperationsService operations;
  private UUID organization;
  private String session;
  private HttpServer receiver;
  private final List<String> received = new CopyOnWriteArrayList<>();

  @BeforeEach
  void setup() throws Exception {
    organization = UUID.randomUUID();
    session = TestCredentials.issueMemberSession(jdbc, organization);
    receiver = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    receiver.createContext(
        "/hook",
        exchange -> {
          received.add(
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          exchange.sendResponseHeaders(200, -1);
          exchange.close();
        });
    receiver.start();
  }

  @AfterEach
  void stop() {
    receiver.stop(0);
  }

  @Test
  void 환경별_수신처와_아웃박스와_실제_전송이_독립적이다() {
    for (String mode : List.of("test", "live")) {
      in(
          mode,
          () -> {
            WebhookEndpointResponse endpoint = create();
            new TransactionTemplate(transactions)
                .executeWithoutResult(
                    status ->
                        events.publish(
                            organization,
                            WebhookEventType.PAYMENT_SUCCEEDED,
                            Map.of("source", mode)));
            assertThat(deliveries.list(organization, endpoint.id(), null, 0, 20).deliveries())
                .hasSize(1);
          });
    }
    Map<String, Double> doneBefore = Map.of("test", done("test"), "live", done("live"));
    for (String mode : List.of("test", "live")) in(mode, () -> deliveries.deliver());
    assertThat(received).hasSize(2);
    assertThat(received.stream().map(payload -> JsonPath.<String>read(payload, "$.environment")))
        .containsExactlyInAnyOrder("test", "live");
    assertThat(
            ownerJdbc()
                .queryForObject(
                    "SELECT (SELECT count(*) FROM test.webhook_delivery d JOIN test.webhook_event e"
                        + " ON e.id=d.webhook_event_id AND e.organization_id=d.organization_id"
                        + " WHERE d.organization_id=? AND d.status='done') + (SELECT count(*) FROM"
                        + " live.webhook_delivery d JOIN live.webhook_event e ON"
                        + " e.id=d.webhook_event_id AND e.organization_id=d.organization_id WHERE"
                        + " d.organization_id=? AND d.status='done')",
                    Integer.class,
                    organization,
                    organization))
        .isEqualTo(2);
    assertThat(
            ownerJdbc()
                .queryForObject(
                    "SELECT (SELECT count(*) FROM test.webhook_delivery_attempt a JOIN"
                        + " test.webhook_delivery d ON d.id=a.webhook_delivery_id AND"
                        + " d.organization_id=a.organization_id WHERE a.organization_id=?) +"
                        + " (SELECT count(*) FROM live.webhook_delivery_attempt a JOIN"
                        + " live.webhook_delivery d ON d.id=a.webhook_delivery_id AND"
                        + " d.organization_id=a.organization_id WHERE a.organization_id=?)",
                    Integer.class,
                    organization,
                    organization))
        .isEqualTo(2);
    assertThat(BusinessEnvironmentContext.current()).isNull();
    for (String environment : List.of("test", "live")) {
      assertThat(done(environment)).as(environment).isEqualTo(doneBefore.get(environment) + 1);
    }
  }

  @Test
  void 운영_현황의_웹훅_실패_수는_test_환경_전송을_세지_않는다() {
    for (String mode : List.of("test", "live")) {
      in(
          mode,
          () -> {
            create();
            new TransactionTemplate(transactions)
                .executeWithoutResult(
                    status ->
                        events.publish(
                            organization,
                            WebhookEventType.PAYMENT_SUCCEEDED,
                            Map.of("source", mode)));
          });
    }
    long before = operations.get().failedWebhookCount();
    for (String mode : List.of("test", "live"))
      in(
          mode,
          () ->
              jdbc.update(
                  "UPDATE webhook_delivery SET status='failed', failure_reason='timeout',"
                      + " next_attempt_at=NULL WHERE organization_id=?",
                  organization));
    assertThat(operations.get().failedWebhookCount()).isEqualTo(before + 1);
    in(
        "live",
        () -> {
          var endpoint = endpoints.list(organization).getFirst();
          endpoints.delete(organization, endpoint.id());
        });
    assertThat(operations.get().failedWebhookCount()).isEqualTo(before);
    var jobKeys =
        operations.get().jobs().stream().map(job -> job.scope() + ":" + job.jobName()).toList();
    assertThat(jobKeys).hasSize(10).doesNotHaveDuplicates().isSorted();
  }

  @Test
  void 웹훅_적체는_업무_환경별로_나눠_센다() {
    for (String mode : List.of("test", "live")) in(mode, () -> deliveries.measure());
    double testBefore = backlog("test");
    double liveBefore = backlog("live");
    WebhookEndpointResponse endpoint = in("test", this::create);
    in("test", () -> endpoints.test(organization, endpoint.id()));
    in("test", () -> deliveries.measure());
    assertThat(backlog("test")).isEqualTo(testBefore + 1);
    assertThat(backlog("live")).isEqualTo(liveBefore);
    in("live", () -> deliveries.measure());
    assertThat(backlog("test")).isEqualTo(testBefore + 1);
    assertThat(backlog("live")).isEqualTo(liveBefore);
  }

  @Test
  void 다른_환경의_수신처_조회와_수정과_재시도가_차단된다() {
    WebhookEndpointResponse live = in("live", this::create);
    UUID event = in("live", () -> endpoints.test(organization, live.id()));
    UUID delivery =
        ownerJdbc()
            .queryForObject(
                "SELECT id FROM live.webhook_delivery WHERE webhook_event_id=?", UUID.class, event);
    in(
        "live",
        () -> jdbc.update("UPDATE webhook_delivery SET status='failed' WHERE id=?", delivery));
    in(
        "test",
        () -> {
          assertThat(endpoints.list(organization)).isEmpty();
          assertThatThrownBy(() -> endpoints.get(organization, live.id()))
              .isInstanceOf(NotFoundException.class);
          assertThatThrownBy(() -> endpoints.delete(organization, live.id()))
              .isInstanceOf(NotFoundException.class);
          assertThat(deliveryRepository.retry(organization, live.id(), delivery)).isZero();
        });
    MockMvcTester mvc = MockMvcTester.from(application, b -> b.apply(springSecurity()).build());
    assertThat(
            mvc.get()
                .uri("/v1/webhook-endpoints/" + live.id())
                .header("Authorization", session)
                .header(BusinessEnvironmentInterceptor.HEADER, "test")
                .exchange())
        .hasStatus(404);
    assertThat(
            mvc.get()
                .uri("/v1/webhook-endpoints/" + live.id())
                .header("Authorization", session)
                .header(BusinessEnvironmentInterceptor.HEADER, "live")
                .exchange())
        .hasStatusOk();
    in(
        "live",
        () -> {
          assertThat(deliveryRepository.retry(organization, live.id(), delivery)).isEqualTo(1);
          endpoints.delete(organization, live.id());
          assertThat(endpoints.list(organization)).isEmpty();
          assertThat(endpoints.list(organization, "archived")).hasSize(1);
          endpoints.restore(organization, live.id());
        });
  }

  @Test
  void lease_재선점은_원래_token의_완료를_거절하고_새_시도만_원자적으로_기록한다() {
    var endpoint = in("test", this::create);
    UUID event = in("test", () -> endpoints.test(organization, endpoint.id()));
    in(
        "test",
        () ->
            jdbc.update(
                "UPDATE webhook_delivery SET next_attempt_at=now()-interval '2 days' WHERE"
                    + " organization_id=?",
                organization));
    var original = in("test", () -> deliveryRepository.claim(otherOrganizations()).orElseThrow());
    assertThat(original.organizationId())
        .as(
            ownerJdbc()
                .queryForList(
                    "SELECT id,organization_id,status,next_attempt_at FROM test.webhook_delivery"
                        + " ORDER BY next_attempt_at,id")
                .toString())
        .isEqualTo(organization);
    assertThat(original.webhookEventId()).isEqualTo(event);
    in(
        "test",
        () ->
            jdbc.update(
                "UPDATE webhook_delivery SET next_attempt_at=now()-interval '3 days' WHERE id=?",
                original.id()));
    var reclaimed = in("test", () -> deliveryRepository.claim(otherOrganizations()).orElseThrow());
    assertThat(reclaimed.id()).isEqualTo(original.id());
    assertThat(reclaimed.claimToken()).isNotEqualTo(original.claimToken());
    assertThat(reclaimed.attemptCount()).isEqualTo(2);
    in(
        "test",
        () -> {
          assertThat(
                  deliveryRepository.complete(
                      original, new com.meterengine.webhook.dto.WebhookOutcome(200, null, 1)))
              .isEmpty();
          assertThat(
                  deliveryRepository.complete(
                      reclaimed, new com.meterengine.webhook.dto.WebhookOutcome(200, null, 2)))
              .contains("done");
        });
    assertThat(
            ownerJdbc()
                .queryForObject(
                    "SELECT count(*) FROM test.webhook_delivery_attempt WHERE"
                        + " webhook_delivery_id=?",
                    Integer.class,
                    original.id()))
        .isEqualTo(1);
    var last = in("test", () -> endpoints.test(organization, endpoint.id()));
    in(
        "test",
        () ->
            jdbc.update(
                "UPDATE webhook_delivery SET attempt_count=6,next_attempt_at=now()-interval '1"
                    + " second' WHERE webhook_event_id=?",
                last));
    var expired = in("test", () -> deliveryRepository.expireLeases());
    assertThat(expired).containsEntry("test", 1);
    assertThat(
            ownerJdbc()
                .queryForObject(
                    "SELECT status FROM test.webhook_delivery WHERE webhook_event_id=?",
                    String.class,
                    last))
        .isEqualTo("failed");
    assertThat(
            ownerJdbc()
                .queryForObject(
                    "SELECT count(*) FROM test.webhook_delivery WHERE webhook_event_id=? AND"
                        + " status='done'",
                    Integer.class,
                    event))
        .isEqualTo(1);
  }

  @Test
  void 잠긴_전송을_건너뛰고_다른_전송을_선택한다() throws Exception {
    var endpoint = in("test", this::create);
    UUID one = in("test", () -> endpoints.test(organization, endpoint.id()));
    UUID two = in("test", () -> endpoints.test(organization, endpoint.id()));
    in(
        "test",
        () ->
            jdbc.update(
                "UPDATE webhook_delivery SET next_attempt_at=now()-interval '2 days' WHERE"
                    + " organization_id=?",
                organization));
    var locked = new java.util.concurrent.CountDownLatch(1);
    var release = new java.util.concurrent.CountDownLatch(1);
    try (var executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
      var holding =
          executor.submit(
              () ->
                  in(
                      "test",
                      () ->
                          new TransactionTemplate(transactions)
                              .execute(
                                  status -> {
                                    jdbc.queryForObject(
                                        "SELECT id FROM webhook_delivery WHERE webhook_event_id=?"
                                            + " FOR UPDATE",
                                        UUID.class,
                                        one);
                                    locked.countDown();
                                    try {
                                      if (!release.await(10, java.util.concurrent.TimeUnit.SECONDS))
                                        throw new IllegalStateException("lock timeout");
                                    } catch (InterruptedException exception) {
                                      Thread.currentThread().interrupt();
                                      throw new IllegalStateException(exception);
                                    }
                                    return null;
                                  })));
      try {
        assertThat(locked.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        var claim = in("test", () -> deliveryRepository.claim(otherOrganizations()).orElseThrow());
        assertThat(claim.webhookEventId()).isEqualTo(two);
      } finally {
        release.countDown();
      }
      holding.get(10, java.util.concurrent.TimeUnit.SECONDS);
    }
  }

  @Test
  void 직접_SQL의_환경이_다른_전송_참조도_FK가_거절한다() {
    WebhookEndpointResponse endpoint = in("test", this::create);
    UUID event = in("test", () -> endpoints.test(organization, endpoint.id()));
    assertThatThrownBy(
            () ->
                in(
                    "live",
                    () ->
                        jdbc.update(
                            "INSERT INTO webhook_delivery"
                                + " (id,organization_id,webhook_endpoint_id,webhook_event_id,next_attempt_at)"
                                + " VALUES (?,?,?,?,now())",
                            UUID.randomUUID(),
                            organization,
                            endpoint.id(),
                            event)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private WebhookEndpointResponse create() {
    return endpoints.create(
        organization,
        new CreateWebhookEndpointRequest(
            "http://127.0.0.1:" + receiver.getAddress().getPort() + "/hook",
            List.of("payment_succeeded"),
            "environment receiver"));
  }

  private double backlog(String environment) {
    return meterRegistry
        .get("meterengine.webhook.delivery.backlog")
        .tag("environment", environment)
        .gauge()
        .value();
  }

  private double done(String environment) {
    return meterRegistry
        .find("meterengine.webhook.delivery")
        .tags("result", "done", "environment", environment)
        .counters()
        .stream()
        .mapToDouble(Counter::count)
        .sum();
  }

  private List<com.meterengine.webhook.dto.WebhookClaim> otherOrganizations() {
    return ownerJdbc()
        .queryForList("SELECT id FROM shared.organization WHERE id<>?", UUID.class, organization)
        .stream()
        .map(
            id ->
                new com.meterengine.webhook.dto.WebhookClaim(
                    null, id, null, null, null, null, null, true, false, 0, null, null, null,
                    false))
        .toList();
  }
}
