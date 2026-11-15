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
    @DefaultValue("1s") Duration consumeRetryInterval) {}
