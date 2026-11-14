package com.meterengine.event.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("meterengine.event.kafka")
public record EventKafkaProperties(
    @DefaultValue("event.ingest") String topic,
    @DefaultValue("6") int partitions,
    @DefaultValue("3") short replicas,
    @DefaultValue("false") boolean createTopics,
    @DefaultValue("20s") Duration publishTimeout,
    @DefaultValue("unknown") String producerName,
    @DefaultValue("event.dlq") String deadLetterTopic,
    @DefaultValue("1s") Duration consumeRetryInterval,
    // 컨슈머 서버 한 대가 초당 DB에 넣는 건수의 상한. 0이면 묶지 않는다.
    @DefaultValue("0") int consumeMaxRecordsPerSecond) {

  public EventKafkaProperties {
    if (consumeMaxRecordsPerSecond < 0) {
      throw new IllegalArgumentException(
          "meterengine.event.kafka.consume-max-records-per-second는 0 이상이어야 한다: "
              + consumeMaxRecordsPerSecond);
    }
  }
}
