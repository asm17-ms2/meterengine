package com.meterengine.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.jayway.jsonpath.JsonPath;
import com.meterengine.TestCredentials;
import com.meterengine.TestcontainersConfiguration;
import com.meterengine.dashboard.service.OperationsService;
import com.meterengine.global.error.NotFoundException;
import com.meterengine.global.security.BusinessEnvironment;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(
    properties = {
      "meterengine.webhook.enabled=false",
      "meterengine.webhook.allow-private-network=true"
    })
@Import(TestcontainersConfiguration.class)
class WebhookEnvironmentIntegrationTest {
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
    for (String mode : List.of("legacy", "test", "live")) {
      BusinessEnvironmentContext.run(
          scope(mode),
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
    deliveries.deliver();
    assertThat(received).hasSize(3);
    assertThat(received.stream().map(payload -> JsonPath.<String>read(payload, "$.environment")))
        .containsExactlyInAnyOrder("legacy", "test", "live");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM webhook_delivery d JOIN webhook_event e ON e.id=d.webhook_event_id WHERE d.organization_id=? AND d.environment_scope_id=e.environment_scope_id AND d.status='done'",
                Integer.class,
                organization))
        .isEqualTo(3);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM webhook_delivery_attempt a JOIN webhook_delivery d ON d.id=a.webhook_delivery_id WHERE a.organization_id=? AND a.environment_scope_id=d.environment_scope_id",
                Integer.class,
                organization))
        .isEqualTo(3);
    assertThat(BusinessEnvironmentContext.current()).isNull();
    for (String environment : List.of("legacy", "test", "live")) {
      assertThat(
              meterRegistry
                  .find("meterengine.webhook.delivery")
                  .tags("result", "done", "environment", environment)
                  .counter())
          .as(environment)
          .isNotNull();
    }
  }

  @Test
  void 운영_현황의_웹훅_실패_수는_test_환경_전송을_세지_않는다() {
    for (String mode : List.of("test", "live")) {
      BusinessEnvironmentContext.run(
          scope(mode),
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
    jdbc.update(
        "UPDATE webhook_delivery SET status='failed', failure_reason='timeout', next_attempt_at=NULL"
            + " WHERE organization_id=?",
        organization);
    assertThat(operations.get().failedWebhookCount()).isEqualTo(before + 1);
  }

  @Test
  void 다른_환경의_수신처_조회와_수정과_재시도가_차단된다() {
    WebhookEndpointResponse live = BusinessEnvironmentContext.run(scope("live"), this::create);
    UUID event =
        BusinessEnvironmentContext.run(
            scope("live"), () -> endpoints.test(organization, live.id()));
    UUID delivery =
        jdbc.queryForObject(
            "SELECT id FROM webhook_delivery WHERE webhook_event_id=?", UUID.class, event);
    jdbc.update("UPDATE webhook_delivery SET status='failed' WHERE id=?", delivery);
    BusinessEnvironmentContext.run(
        scope("test"),
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
    BusinessEnvironmentContext.run(
        scope("live"),
        () -> {
          assertThat(deliveryRepository.retry(organization, live.id(), delivery)).isEqualTo(1);
          endpoints.delete(organization, live.id());
          assertThat(endpoints.list(organization)).isEmpty();
          assertThat(endpoints.list(organization, "archived")).hasSize(1);
          endpoints.restore(organization, live.id());
        });
  }

  @Test
  void 직접_SQL의_환경이_다른_전송_참조도_FK가_거절한다() {
    WebhookEndpointResponse endpoint = BusinessEnvironmentContext.run(scope("test"), this::create);
    UUID event =
        BusinessEnvironmentContext.run(
            scope("test"), () -> endpoints.test(organization, endpoint.id()));
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO webhook_delivery (id,organization_id,environment_id,webhook_endpoint_id,webhook_event_id,next_attempt_at) VALUES (?,?,?,?,?,now())",
                    UUID.randomUUID(),
                    organization,
                    scope("live").environmentId(),
                    endpoint.id(),
                    event))
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

  private BusinessEnvironment scope(String mode) {
    UUID id =
        mode.equals("legacy")
            ? null
            : jdbc.queryForObject(
                "SELECT id FROM organization_environment WHERE organization_id=? AND mode=?",
                UUID.class,
                organization,
                mode);
    return new BusinessEnvironment(organization, id, mode.equals("legacy") ? null : mode);
  }
}
