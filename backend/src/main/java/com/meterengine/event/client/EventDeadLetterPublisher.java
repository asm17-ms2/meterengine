package com.meterengine.event.client;

import com.meterengine.event.config.EventKafkaConfig;
import com.meterengine.event.config.EventKafkaProperties;
import com.meterengine.event.dto.EventDeadLetterReason;
import com.meterengine.event.dto.EventIngestRecord;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

// 저장할 수 없는 레코드를 받은 그대로 DLQ 토픽에 옮기고, 이유와 시각을 헤더에 더한다.
@Component
@ConditionalOnProperty(name = EventKafkaConfig.WRITE_PATH, havingValue = EventKafkaConfig.KAFKA)
public class EventDeadLetterPublisher {

  static final String FAILURE_REASON_HEADER = "failure_reason";
  static final String FAILED_AT_HEADER = "failed_at";

  private final KafkaTemplate<String, byte[]> kafkaTemplate;
  private final String topic;
  private final Duration publishTimeout;
  private final Clock clock;

  EventDeadLetterPublisher(
      KafkaTemplate<String, byte[]> kafkaTemplate,
      EventKafkaProperties eventKafkaProperties,
      Clock clock) {
    this.kafkaTemplate = kafkaTemplate;
    this.topic = eventKafkaProperties.deadLetterTopic();
    this.publishTimeout = eventKafkaProperties.publishTimeout();
    this.clock = clock;
  }

  // 브로커 확인까지 기다린다. 확인하지 못하면 던져서 컨슈머가 오프셋을 넘기지 않게 한다.
  public void publish(EventIngestRecord eventIngestRecord, EventDeadLetterReason reason) {
    ProducerRecord<String, byte[]> producerRecord =
        new ProducerRecord<>(topic, eventIngestRecord.key(), eventIngestRecord.value());
    eventIngestRecord.headers().forEach((name, value) -> producerRecord.headers().add(name, value));
    producerRecord.headers().add(FAILURE_REASON_HEADER, bytes(reason.code()));
    producerRecord.headers().add(FAILED_AT_HEADER, bytes(Instant.now(clock).toString()));
    try {
      kafkaTemplate.send(producerRecord).get(publishTimeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (InterruptedException interruption) {
      Thread.currentThread().interrupt();
      throw new EventDeadLetterException(topic, interruption);
    } catch (ExecutionException | TimeoutException | RuntimeException failure) {
      throw new EventDeadLetterException(topic, failure);
    }
  }

  private static byte[] bytes(String text) {
    return text.getBytes(StandardCharsets.UTF_8);
  }
}
