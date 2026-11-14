package com.meterengine.event.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.meterengine.event.client.EventConsumeRetryListener;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.kafka.listener.MessageListenerContainer;

class EventKafkaConfigTest {

  private static final TopicPartition PARTITION = new TopicPartition("event.ingest", 0);
  private static final EventKafkaProperties PROPERTIES =
      new EventKafkaProperties(
          "event.ingest",
          6,
          (short) 3,
          false,
          Duration.ofSeconds(20),
          "test",
          "event.dlq",
          Duration.ofMillis(1),
          0);

  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final DefaultErrorHandler errorHandler =
      new EventKafkaConfig()
          .eventIngestErrorHandler(PROPERTIES, new EventConsumeRetryListener(meterRegistry));

  // spring-kafka는 ClassCastException 같은 몇 가지를 다시 해도 안 될 예외로 보고 묶음을 버린다.
  // 수집 서버가 이미 202로 답한 이벤트라 버리지 않고, 고친 코드가 배포될 때까지 그 자리에서 다시 한다.
  @Test
  void 다시_해도_안_될_예외로_분류된_것도_묶음을_버리지_않고_다시_한다() {
    AtomicInteger retries = new AtomicInteger();

    handle(new ClassCastException("bug"), retries::incrementAndGet);

    assertThat(retries).hasValue(1);
  }

  // 재시도는 조용히 되풀이되므로, 멈춰 있는 것을 지표로 알아챌 수 있어야 한다.
  @Test
  void 묶음_저장이_실패할_때마다_재시도_건수를_센다() {
    handle(new IllegalStateException("db down"), () -> {});

    assertThat(meterRegistry.get("meterengine.event.consume.retry").counter().count()).isEqualTo(1);
  }

  @SuppressWarnings("unchecked")
  private void handle(RuntimeException failure, Runnable retry) {
    Consumer<String, byte[]> consumer = mock(Consumer.class);
    when(consumer.assignment()).thenReturn(Set.of(PARTITION));
    MessageListenerContainer container = mock(MessageListenerContainer.class);
    when(container.isRunning()).thenReturn(true);
    when(container.getContainerFor(any(), anyInt())).thenReturn(container);
    ConsumerRecords<String, byte[]> records =
        new ConsumerRecords<>(
            Map.of(
                PARTITION,
                List.of(new ConsumerRecord<>(PARTITION.topic(), 0, 0L, "key", new byte[0]))),
            Map.of());

    errorHandler.handleBatch(
        new ListenerExecutionFailedException("listener failed", failure),
        records,
        consumer,
        container,
        retry);
  }
}
