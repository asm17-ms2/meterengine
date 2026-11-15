package com.meterengine.event.client;

import com.meterengine.event.config.EventKafkaConfig;
import com.meterengine.event.config.EventKafkaProperties;
import com.meterengine.event.dto.EventIngestMessage;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

// 카프카를 부르는 곳은 여기 하나다. 값은 우리 JsonMapper로 만든 JSON 바이트라 자바 클래스 이름이 메시지에 실리지 않는다.
@Component
@ConditionalOnProperty(name = EventKafkaConfig.WRITE_PATH, havingValue = EventKafkaConfig.KAFKA)
public class EventIngestPublisher {

  static final String PRODUCER_HEADER = "producer";
  static final String PUBLISH_TIMER_NAME = "meterengine.event.publish";
  static final String RESULT_TAG = "result";

  private final KafkaTemplate<String, byte[]> kafkaTemplate;
  private final JsonMapper jsonMapper;
  private final String topic;
  private final byte[] producerName;
  private final MeterRegistry meterRegistry;
  private final Timer succeeded;
  private final Timer failed;

  EventIngestPublisher(
      KafkaTemplate<String, byte[]> kafkaTemplate,
      JsonMapper jsonMapper,
      EventKafkaProperties eventKafkaProperties,
      MeterRegistry meterRegistry) {
    this.kafkaTemplate = kafkaTemplate;
    this.jsonMapper = jsonMapper;
    this.topic = eventKafkaProperties.topic();
    this.producerName = eventKafkaProperties.producerName().getBytes(StandardCharsets.UTF_8);
    this.meterRegistry = meterRegistry;
    this.succeeded = publishTimer("success");
    this.failed = publishTimer("failure");
  }

  // 브로커가 받았다고 확인하면 끝나고, 못 받았으면 예외로 끝나는 Future를 돌려준다.
  public CompletableFuture<Void> publish(EventIngestMessage message, Map<String, String> headers) {
    Timer.Sample sample = Timer.start(meterRegistry);
    return send(message, headers)
        .whenComplete((result, failure) -> sample.stop(failure == null ? succeeded : failed))
        .thenApply(result -> null);
  }

  // JSON으로 바꾸다 난 실패와 브로커 주소를 못 얻는 실패는 그 자리에서 던져지므로 같은 Future 실패로 맞춘다.
  private CompletableFuture<SendResult<String, byte[]>> send(
      EventIngestMessage message, Map<String, String> headers) {
    try {
      return kafkaTemplate.send(producerRecord(message, headers));
    } catch (RuntimeException failure) {
      return CompletableFuture.failedFuture(failure);
    }
  }

  private ProducerRecord<String, byte[]> producerRecord(
      EventIngestMessage message, Map<String, String> headers) {
    ProducerRecord<String, byte[]> producerRecord =
        new ProducerRecord<>(topic, message.key(), jsonMapper.writeValueAsBytes(message));
    producerRecord.headers().add(PRODUCER_HEADER, producerName);
    headers.forEach(
        (name, value) ->
            producerRecord.headers().add(name, value.getBytes(StandardCharsets.UTF_8)));
    return producerRecord;
  }

  private Timer publishTimer(String result) {
    return Timer.builder(PUBLISH_TIMER_NAME)
        .description("이벤트를 JSON으로 바꿔 카프카에 발행하고 브로커 확인을 받기까지 걸린 시간")
        .tag(RESULT_TAG, result)
        .publishPercentileHistogram()
        .register(meterRegistry);
  }
}
