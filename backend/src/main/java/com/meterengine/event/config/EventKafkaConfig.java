package com.meterengine.event.config;

import java.time.Duration;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = EventKafkaConfig.WRITE_PATH, havingValue = EventKafkaConfig.KAFKA)
public class EventKafkaConfig {

  public static final String WRITE_PATH = "meterengine.event.write-path";
  public static final String DIRECT = "direct";
  public static final String KAFKA = "kafka";

  private static final Duration RETENTION = Duration.ofDays(7);
  private static final int MIN_IN_SYNC_REPLICAS = 2;

  // 운영 토픽은 클러스터 쪽에서 만든다. 로컬과 시험에서만 켠다.
  @Bean
  @ConditionalOnProperty(name = "meterengine.event.kafka.create-topics", havingValue = "true")
  NewTopic eventIngestTopic(EventKafkaProperties properties) {
    int minInSyncReplicas = Math.min(MIN_IN_SYNC_REPLICAS, properties.replicas());
    return TopicBuilder.name(properties.topic())
        .partitions(properties.partitions())
        .replicas(properties.replicas())
        .config(TopicConfig.RETENTION_MS_CONFIG, Long.toString(RETENTION.toMillis()))
        .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, Integer.toString(minInSyncReplicas))
        .config(TopicConfig.UNCLEAN_LEADER_ELECTION_ENABLE_CONFIG, "false")
        .build();
  }
}
