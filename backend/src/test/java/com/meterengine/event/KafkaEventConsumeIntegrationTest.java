package com.meterengine.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.meterengine.KafkaTestcontainersConfiguration;
import com.meterengine.TestCredentials;
import com.meterengine.TestcontainersConfiguration;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.AbstractMessageListenerContainer;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.web.context.WebApplicationContext;

// 수집 API부터 DB까지 한 번 흘려 본다. 묶음 안의 갈래는 EventConsumeIntegrationTest가 본다.
@Import({TestcontainersConfiguration.class, KafkaTestcontainersConfiguration.class})
@SpringBootTest(
    properties = {
      "meterengine.event.write-path=kafka",
      "meterengine.role=all",
      "meterengine.event.kafka.create-topics=true",
      "meterengine.event.kafka.replicas=1"
    })
class KafkaEventConsumeIntegrationTest {

  private static final Duration STORE_TIMEOUT = Duration.ofSeconds(30);
  private static final Duration POLL_INTERVAL = Duration.ofMillis(100);

  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;
  @Autowired private DefaultErrorHandler eventIngestErrorHandler;

  private MockMvcTester mvc;
  private UUID organizationId;
  private String authorization;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcTester.from(
            webApplicationContext, builder -> builder.apply(springSecurity()).build());
    organizationId = UUID.randomUUID();
    authorization = TestCredentials.issueApiKey(jdbcTemplate, organizationId);
  }

  @Test
  void 수집_API가_202로_받은_이벤트를_컨슈머가_event에_저장한다() throws InterruptedException {
    UUID customerId =
        jdbcTemplate.queryForObject(
            "INSERT INTO customer (organization_id, name) VALUES (?, 'acme') RETURNING id",
            UUID.class,
            organizationId);
    String event =
        """
        {"transaction_id":"tx-1","customer_id":"%s","type":"api_call",
         "properties":{"tokens":1520},"timestamp":"%s"}
        """
            .formatted(customerId, OffsetDateTime.now(ZoneOffset.UTC));

    assertThat(
            mvc.post()
                .uri("/v1/events")
                .header(AUTHORIZATION, authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .content(event)
                .exchange())
        .hasStatus(202);

    assertThat(storedWithin(STORE_TIMEOUT)).isEqualTo(1);
  }

  // 이 연결이 빠지면 spring-kafka 기본 핸들러가 몇 번 다시 하다 묶음을 건너뛰어, 202로 답한 이벤트를 잃는다.
  @Test
  void 리스너_컨테이너는_될_때까지_다시_하는_오류_핸들러를_쓴다() {
    assertThat(kafkaListenerEndpointRegistry.getListenerContainers())
        .isNotEmpty()
        .allSatisfy(
            container ->
                assertThat(commonErrorHandler(container)).isSameAs(eventIngestErrorHandler));
  }

  private static CommonErrorHandler commonErrorHandler(MessageListenerContainer container) {
    return ((AbstractMessageListenerContainer<?, ?>) container).getCommonErrorHandler();
  }

  private int storedWithin(Duration timeout) throws InterruptedException {
    Instant deadline = Instant.now().plus(timeout);
    while (stored() == 0 && Instant.now().isBefore(deadline)) {
      Thread.sleep(POLL_INTERVAL.toMillis());
    }
    return stored();
  }

  private int stored() {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM event WHERE organization_id = ? AND transaction_id = 'tx-1'",
            Integer.class,
            organizationId);
    return count == null ? 0 : count;
  }
}
