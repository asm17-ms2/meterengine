package com.meterengine.event.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;

// 카프카 경보는 데이터가 없으면 정상으로 본다. 지표 이름이 경보와 어긋나면 경보가 조용히 꺼지므로 둘을 묶어 둔다.
class EventConsumeMetricsAlertNamesTest {
  private static final Path ALERTING =
      Path.of("..", "deploy", "grafana", "provisioning", "alerting", "alerting.yml");
  private static final Path APPLICATION_PROPERTIES =
      Path.of("src", "main", "resources", "application.properties");
  private static final String GROUP_ID_PROPERTY = "spring.kafka.consumer.group-id";

  @Test
  void 경보가_컨슈머가_내는_지표_이름과_결과_값을_읽는다() throws Exception {
    String alerting = Files.readString(ALERTING);
    String consumeCounter = prometheusName(EventConsumeMetrics.CONSUME_COUNTER_NAME) + "_total";
    String storageDelayBuckets =
        prometheusName(EventConsumeMetrics.STORAGE_DELAY_TIMER_NAME) + "_seconds_bucket";

    assertThat(alerting)
        .contains("rate(" + consumeCounter + "{")
        .contains(
            consumeCounter
                + "{env!~\"test-.*\", "
                + EventConsumeMetrics.RESULT_TAG
                + "=\""
                + EventConsumeMetrics.DEAD_LETTER
                + "\"}")
        .contains("rate(" + storageDelayBuckets + "{");
  }

  @Test
  void 경보가_컨슈머_그룹_이름으로_밀린_양을_읽는다() throws Exception {
    Properties properties = new Properties();
    try (var reader = Files.newBufferedReader(APPLICATION_PROPERTIES)) {
      properties.load(reader);
    }

    assertThat(Files.readString(ALERTING))
        .contains("groupId=\"" + properties.getProperty(GROUP_ID_PROPERTY) + "\"");
  }

  private static String prometheusName(String meterName) {
    return meterName.replace('.', '_');
  }
}
