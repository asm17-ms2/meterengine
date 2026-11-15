package com.meterengine.event.service;

import static org.assertj.core.api.Assertions.assertThat;
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
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class EventConsumeServiceTest {

  private static final Instant STORED_AT = Instant.parse("2026-11-11T03:00:00Z");
  private static final Duration STORAGE_DELAY = Duration.ofSeconds(7);

  @Mock private EventRepository eventRepository;
  @Mock private EventRejectionRepository eventRejectionRepository;
  @Mock private CustomerDirectory customerDirectory;
  @Mock private EventDeadLetterPublisher eventDeadLetterPublisher;
  @Mock private PlatformTransactionManager platformTransactionManager;

  private final JsonMapper jsonMapper = JsonMapper.builder().build();
  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final UUID customerId = UUID.randomUUID();

  private EventConsumeService eventConsumeService;

  @BeforeEach
  void setUp() {
    eventConsumeService =
        new EventConsumeService(
            eventRepository,
            eventRejectionRepository,
            customerDirectory,
            eventDeadLetterPublisher,
            new EventConsumeMetrics(meterRegistry),
            jsonMapper,
            platformTransactionManager,
            Clock.fixed(STORED_AT, ZoneOffset.UTC));
  }

  // DLQ로 보내고 넘어가면 DB가 죽은 동안의 이벤트가 전부 DLQ에 쌓인다. 던져서 같은 묶음을 다시 읽게 한다.
  @Test
  void DB가_답하지_않으면_DLQ로_보내지_않고_던진다() {
    when(eventRepository.findExistingTransactionIds(any(), any()))
        .thenThrow(new QueryTimeoutException("statement timeout"));

    assertThatThrownBy(() -> eventConsumeService.consume(List.of(eventIngestRecord())))
        .isInstanceOf(QueryTimeoutException.class);
    verifyNoInteractions(eventDeadLetterPublisher);
  }

  // 수집 서버가 202로 답한 때부터 DB에 들어간 때까지가 도입사가 조회에서 겪는 지연이다.
  @Test
  void 저장한_이벤트마다_접수부터_저장까지_걸린_시간을_남긴다() {
    when(customerDirectory.active(any(), any())).thenReturn(Map.of(customerId, true));
    when(eventRepository.insertAllMessagesIfAbsent(any())).thenReturn(List.of("tx-1"));

    eventConsumeService.consume(List.of(eventIngestRecord()));

    assertThat(storageDelayTimer().count()).isEqualTo(1);
    assertThat(storageDelayTimer().totalTime(TimeUnit.SECONDS))
        .isEqualTo(STORAGE_DELAY.toSeconds());
  }

  // 이미 있던 이벤트는 이번에 저장한 것이 아니라서, 넣으면 다시 읽을 때마다 지연이 길게 찍힌다.
  @Test
  void 중복은_저장_지연을_남기지_않는다() {
    when(customerDirectory.active(any(), any())).thenReturn(Map.of(customerId, true));
    when(eventRepository.insertAllMessagesIfAbsent(any())).thenReturn(List.of());

    eventConsumeService.consume(List.of(eventIngestRecord()));

    assertThat(storageDelayTimer().count()).isZero();
  }

  private Timer storageDelayTimer() {
    return meterRegistry.get(EventConsumeMetrics.STORAGE_DELAY_TIMER_NAME).timer();
  }

  private EventIngestRecord eventIngestRecord() {
    EventIngestMessage message =
        new EventIngestMessage(
            EventIngestMessage.CURRENT_SCHEMA_VERSION,
            UUID.randomUUID(),
            null,
            "tx-1",
            customerId,
            "api_call",
            Map.of(),
            OffsetDateTime.now(),
            STORED_AT.minus(STORAGE_DELAY),
            UUID.randomUUID(),
            "api_key",
            UUID.randomUUID());
    return new EventIngestRecord(message.key(), jsonMapper.writeValueAsBytes(message), Map.of());
  }
}
