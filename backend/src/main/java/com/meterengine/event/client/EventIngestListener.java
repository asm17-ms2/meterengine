package com.meterengine.event.client;

import com.meterengine.event.config.EventKafkaConfig;
import com.meterengine.event.dto.EventIngestRecord;
import com.meterengine.event.service.EventConsumeService;
import com.meterengine.event.service.EventConsumeThrottle;
import com.meterengine.global.config.ConditionalOnServerRole;
import com.meterengine.global.config.ServerRole;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

// 토픽을 묶음으로 읽어 저장 서비스에 넘긴다. 예외 없이 돌아오면 컨테이너가 그 묶음의 오프셋을 넘긴다.
@Component
@ConditionalOnProperty(name = EventKafkaConfig.WRITE_PATH, havingValue = EventKafkaConfig.KAFKA)
@ConditionalOnServerRole({ServerRole.ALL, ServerRole.CONSUMER})
public class EventIngestListener {

  private final EventConsumeService eventConsumeService;
  private final EventConsumeThrottle eventConsumeThrottle;

  EventIngestListener(
      EventConsumeService eventConsumeService, EventConsumeThrottle eventConsumeThrottle) {
    this.eventConsumeService = eventConsumeService;
    this.eventConsumeThrottle = eventConsumeThrottle;
  }

  @KafkaListener(topics = "${meterengine.event.kafka.topic:event.ingest}")
  public void onRecords(List<ConsumerRecord<String, byte[]>> consumerRecords) {
    eventConsumeThrottle.awaitTurn(consumerRecords.size());
    eventConsumeService.consume(
        consumerRecords.stream().map(EventIngestListener::eventIngestRecord).toList());
  }

  private static EventIngestRecord eventIngestRecord(
      ConsumerRecord<String, byte[]> consumerRecord) {
    Map<String, byte[]> headers = new LinkedHashMap<>();
    for (Header header : consumerRecord.headers()) {
      headers.put(header.key(), header.value());
    }
    return new EventIngestRecord(consumerRecord.key(), consumerRecord.value(), headers);
  }
}
