package com.meterengine.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.meterengine.TestcontainersConfiguration;
import com.meterengine.event.dto.EventIngestMessage;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// 프로듀서와 컨슈머가 같은 예시 파일을 읽어 메시지 형식이 한쪽에서만 바뀌는 것을 막는다.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class EventIngestMessageContractTest {

  static final String EXAMPLE = "/kafka/event-ingest-v1.json";

  @Autowired private JsonMapper jsonMapper;

  @Test
  void 예시_메시지를_읽어_다시_쓰면_같은_JSON이다() throws IOException {
    JsonNode example = example();

    EventIngestMessage message = jsonMapper.treeToValue(example, EventIngestMessage.class);

    assertThat(jsonMapper.readTree(jsonMapper.writeValueAsBytes(message))).isEqualTo(example);
  }

  @Test
  void 키는_도입사와_transaction_id를_잇는다() throws IOException {
    EventIngestMessage message = jsonMapper.treeToValue(example(), EventIngestMessage.class);

    assertThat(message.key()).isEqualTo("3f1c2a9e-5b7d-4e0a-9c11-2d6f8b4a7e10:tx-20261109-000187");
  }

  private JsonNode example() throws IOException {
    try (InputStream input = getClass().getResourceAsStream(EXAMPLE)) {
      return jsonMapper.readTree(input);
    }
  }
}
