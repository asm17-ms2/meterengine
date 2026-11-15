package com.meterengine.event.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

// 카프카 경보는 데이터가 없으면 정상으로 본다. 지표 이름이 경보와 어긋나면 경보가 조용히 꺼지므로 둘을 묶어 둔다.
class EventIngestPublisherAlertNamesTest {
  private static final Path ALERTING =
      Path.of("..", "deploy", "grafana", "provisioning", "alerting", "alerting.yml");
  // 발행 타이머가 실패에 붙이는 result 값이다. 운영 코드에 상수가 없어 여기 적는다.
  private static final String FAILURE_RESULT = "failure";

  @Test
  void 경보가_발행_타이머의_이름과_실패_값을_읽는다() throws Exception {
    String publishCount =
        EventIngestPublisher.PUBLISH_TIMER_NAME.replace('.', '_') + "_seconds_count";

    assertThat(Files.readString(ALERTING))
        .contains(
            "rate("
                + publishCount
                + "{env!~\"test-.*\", "
                + EventIngestPublisher.RESULT_TAG
                + "=\""
                + FAILURE_RESULT
                + "\"}");
  }
}
