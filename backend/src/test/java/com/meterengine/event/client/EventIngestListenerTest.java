package com.meterengine.event.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.meterengine.event.service.EventConsumeService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

// 리스너가 뜨는 서버만 토픽을 읽는다. 수집 서버와 관리 서버에서 뜨면 요청을 받는 서버가 저장까지 떠맡는다.
class EventIngestListenerTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(EventIngestListener.class)
          .withBean(EventConsumeService.class, () -> mock(EventConsumeService.class))
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
}
