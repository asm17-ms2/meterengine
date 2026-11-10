package com.meterengine.event.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.meterengine.customer.api.CustomerDirectory;
import com.meterengine.event.client.EventDeadLetterPublisher;
import com.meterengine.event.dto.EventIngestMessage;
import com.meterengine.event.dto.EventIngestRecord;
import com.meterengine.event.repository.EventRejectionRepository;
import com.meterengine.event.repository.EventRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class EventConsumeServiceTest {

  @Mock private EventRepository eventRepository;
  @Mock private EventRejectionRepository eventRejectionRepository;
  @Mock private CustomerDirectory customerDirectory;
  @Mock private EventDeadLetterPublisher eventDeadLetterPublisher;
  @Mock private PlatformTransactionManager platformTransactionManager;

  private final JsonMapper jsonMapper = JsonMapper.builder().build();

  // DLQ로 보내고 넘어가면 DB가 죽은 동안의 이벤트가 전부 DLQ에 쌓인다. 던져서 같은 묶음을 다시 읽게 한다.
  @Test
  void DB가_답하지_않으면_DLQ로_보내지_않고_던진다() {
    EventConsumeService eventConsumeService =
        new EventConsumeService(
            eventRepository,
            eventRejectionRepository,
            customerDirectory,
            eventDeadLetterPublisher,
            new EventConsumeMetrics(new SimpleMeterRegistry()),
            jsonMapper,
            platformTransactionManager);
    when(eventRepository.findExistingTransactionIds(any(), any()))
        .thenThrow(new QueryTimeoutException("statement timeout"));

    assertThatThrownBy(() -> eventConsumeService.consume(List.of(eventIngestRecord())))
        .isInstanceOf(QueryTimeoutException.class);
    verifyNoInteractions(eventDeadLetterPublisher);
  }

  private EventIngestRecord eventIngestRecord() {
    EventIngestMessage message =
        new EventIngestMessage(
            EventIngestMessage.CURRENT_SCHEMA_VERSION,
            UUID.randomUUID(),
            null,
            "tx-1",
            UUID.randomUUID(),
            "api_call",
            Map.of(),
            OffsetDateTime.now(),
            Instant.now(),
            UUID.randomUUID(),
            "api_key",
            UUID.randomUUID());
    return new EventIngestRecord(message.key(), jsonMapper.writeValueAsBytes(message), Map.of());
  }
}
