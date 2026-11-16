package com.meterengine.event.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import com.meterengine.event.service.EventConsumeService;
import com.meterengine.event.service.EventConsumeThrottle;
import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

// 리스너가 뜨는 서버만 토픽을 읽는다. 수집 서버와 관리 서버에서 뜨면 요청을 받는 서버가 저장까지 떠맡는다.
class EventIngestListenerTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(EventIngestListener.class)
          .withBean(EventConsumeService.class, () -> mock(EventConsumeService.class))
          .withBean(EventConsumeThrottle.class, () -> mock(EventConsumeThrottle.class))
          .withPropertyValues("meterengine.event.write-path=kafka");

  @Test
  void 역할이_all이면_리스너를_만든다() {
    contextRunner
        .withPropertyValues("meterengine.role=all")
        .run(context -> assertThat(context).hasSingleBean(EventIngestListener.class));
  }

  @Test
  void 역할이_consumer면_리스너를_만든다() {
    contextRunner
        .withPropertyValues("meterengine.role=consumer")
        .run(context -> assertThat(context).hasSingleBean(EventIngestListener.class));
  }

  @Test
  void 역할이_ingest면_리스너를_만들지_않는다() {
    contextRunner
        .withPropertyValues("meterengine.role=ingest")
        .run(context -> assertThat(context).doesNotHaveBean(EventIngestListener.class));
  }

  @Test
  void 역할이_api면_리스너를_만들지_않는다() {
    contextRunner
        .withPropertyValues("meterengine.role=api")
        .run(context -> assertThat(context).doesNotHaveBean(EventIngestListener.class));
  }

  // 저장한 뒤에 기다리면 묶음이 이미 DB에 들어가 속도를 묶지 못한다.
  @Test
  void 묶음의_건수만큼_차례를_기다린_뒤에_저장한다() {
    EventConsumeService eventConsumeService = mock(EventConsumeService.class);
    EventConsumeThrottle eventConsumeThrottle = mock(EventConsumeThrottle.class);
    EventIngestListener eventIngestListener =
        new EventIngestListener(eventConsumeService, eventConsumeThrottle);

    eventIngestListener.onRecords(
        List.of(
            new ConsumerRecord<>("event.ingest", 0, 0L, "key-1", new byte[0]),
            new ConsumerRecord<>("event.ingest", 0, 1L, "key-2", new byte[0])));

    InOrder inOrder = inOrder(eventConsumeThrottle, eventConsumeService);
    inOrder.verify(eventConsumeThrottle).awaitTurn(2);
    inOrder.verify(eventConsumeService).consume(argThat(records -> records.size() == 2));
  }
}
