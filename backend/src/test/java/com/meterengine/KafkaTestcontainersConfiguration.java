package com.meterengine;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

// 수집 경로가 kafka인 시험만 가져온다. 기본 컨텍스트는 브로커 없이 뜬다.
@TestConfiguration(proxyBeanMethods = false)
public class KafkaTestcontainersConfiguration {

  @Bean
  @ServiceConnection
  KafkaContainer kafkaContainer() {
    return new KafkaContainer(DockerImageName.parse("apache/kafka:4.3.1"));
  }
}
