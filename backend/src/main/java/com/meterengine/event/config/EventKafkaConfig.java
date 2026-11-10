package com.meterengine.event.config;

import com.meterengine.global.config.ConditionalOnServerRole;
import com.meterengine.global.config.ServerRole;
import java.time.Duration;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = EventKafkaConfig.WRITE_PATH, havingValue = EventKafkaConfig.KAFKA)
public class EventKafkaConfig {

  public static final String WRITE_PATH = "meterengine.event.write-path";
  public static final String DIRECT = "direct";
  public static final String KAFKA = "kafka";

  private static final Duration RETENTION = Duration.ofDays(7);
  private static final Duration DEAD_LETTER_RETENTION = Duration.ofDays(30);
  private static final int DEAD_LETTER_PARTITIONS = 1;
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

  // 다시 해도 안 되는 메시지만 와서 양이 거의 없다. 한 줄로 남아야 재처리가 쉬워 파티션은 하나다.
  @Bean
  @ConditionalOnProperty(name = "meterengine.event.kafka.create-topics", havingValue = "true")
  NewTopic eventDeadLetterTopic(EventKafkaProperties properties) {
    int minInSyncReplicas = Math.min(MIN_IN_SYNC_REPLICAS, properties.replicas());
    return TopicBuilder.name(properties.deadLetterTopic())
        .partitions(DEAD_LETTER_PARTITIONS)
        .replicas(properties.replicas())
        .config(TopicConfig.RETENTION_MS_CONFIG, Long.toString(DEAD_LETTER_RETENTION.toMillis()))
        .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, Integer.toString(minInSyncReplicas))
        .config(TopicConfig.UNCLEAN_LEADER_ELECTION_ENABLE_CONFIG, "false")
        .build();
  }

  // 저장에 실패하면 오프셋을 넘기지 않고 같은 묶음을 될 때까지 다시 읽는다. DB가 돌아오면 이어서 간다.
  @Bean
  @ConditionalOnServerRole({ServerRole.ALL, ServerRole.CONSUMER})
  DefaultErrorHandler eventIngestErrorHandler(EventKafkaProperties properties) {
    return new DefaultErrorHandler(
        new FixedBackOff(
            properties.consumeRetryInterval().toMillis(), FixedBackOff.UNLIMITED_ATTEMPTS));
  }
}
