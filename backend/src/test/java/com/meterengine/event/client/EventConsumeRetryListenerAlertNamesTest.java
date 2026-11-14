package com.meterengine.event.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

// 카프카 경보는 데이터가 없으면 정상으로 본다. 지표 이름이 경보와 어긋나면 경보가 조용히 꺼지므로 둘을 묶어 둔다.
class EventConsumeRetryListenerAlertNamesTest {
  private static final Path ALERTING =
      Path.of("..", "deploy", "grafana", "provisioning", "alerting", "alerting.yml");

  @Test
  void 경보가_재시도_카운터의_이름을_읽는다() throws Exception {
    String retryCounter = EventConsumeRetryListener.RETRY_COUNTER_NAME.replace('.', '_') + "_total";

    assertThat(Files.readString(ALERTING)).contains("rate(" + retryCounter + "{");
  }
}
