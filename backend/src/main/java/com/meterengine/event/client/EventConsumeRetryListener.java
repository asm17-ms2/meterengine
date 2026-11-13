package com.meterengine.event.client;

import com.meterengine.event.config.EventKafkaConfig;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.listener.RetryListener;
import org.springframework.stereotype.Component;

// 묶음 저장이 실패해 다시 할 때마다 불린다. spring-kafka는 재시도 실패를 DEBUG로만 남겨서,
// 여기서 세고 알리지 않으면 컨슈머가 한자리에 멈춰 있어도 랙이 쌓일 때까지 모른다.
@Component
@ConditionalOnProperty(name = EventKafkaConfig.WRITE_PATH, havingValue = EventKafkaConfig.KAFKA)
public class EventConsumeRetryListener implements RetryListener {

  static final String RETRY_COUNTER_NAME = "meterengine.event.consume.retry";

  private static final Logger logger = LoggerFactory.getLogger(EventConsumeRetryListener.class);
  private static final int FIRST_ATTEMPT = 1;

  private final Counter retries;

  public EventConsumeRetryListener(MeterRegistry meterRegistry) {
    this.retries =
        Counter.builder(RETRY_COUNTER_NAME)
            .description("컨슈머가 묶음 저장에 실패해 같은 묶음을 다시 하게 된 횟수")
            .register(meterRegistry);
  }

  @Override
  public void failedDelivery(
      ConsumerRecords<?, ?> consumerRecords, Exception failure, int deliveryAttempt) {
    retries.increment();
    // 같은 원인으로 1초마다 되풀이되므로 스택은 처음 한 번만 남긴다.
    if (deliveryAttempt == FIRST_ATTEMPT) {
      logger.warn(
          "event batch of {} records was not stored, retrying until it is",
          consumerRecords.count(),
          failure);
    } else {
      logger.warn(
          "event batch of {} records was not stored on attempt {}: {}",
          consumerRecords.count(),
          deliveryAttempt,
          failure.toString());
    }
  }

  // 리스너가 묶음으로 받으므로 레코드 하나짜리 재시도는 오지 않는다.
  @Override
  public void failedDelivery(
      ConsumerRecord<?, ?> consumerRecord, Exception failure, int deliveryAttempt) {}
}
