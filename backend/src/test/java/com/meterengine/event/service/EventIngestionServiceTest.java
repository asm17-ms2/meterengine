package com.meterengine.event.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meterengine.customer.api.CustomerDirectory;
import com.meterengine.event.dto.IngestEventRequest;
import com.meterengine.event.dto.IngestEventResponse;
import com.meterengine.event.repository.EventRepository;
import com.meterengine.global.error.BusinessException;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.NotFoundException;
import com.meterengine.global.metrics.DatabaseOperationTimer;
import com.meterengine.metric.service.BillableMetricUsageService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionSystemException;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class EventIngestionServiceTest {

  private static final UUID ORGANIZATION_ID = UUID.randomUUID();
  private static final UUID CUSTOMER_ID = UUID.randomUUID();
  private static final OffsetDateTime OCCURRED_AT =
      OffsetDateTime.now(BillableMetricUsageService.BILLING_ZONE);

  @Mock private EventRepository eventRepository;
  @Mock private CustomerDirectory customerDirectory;
  @Mock private PlatformTransactionManager platformTransactionManager;

  private SimpleMeterRegistry meterRegistry;
  private EventIngestionService eventService;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    eventService =
        new EventIngestionService(
            eventRepository,
            customerDirectory,
            JsonMapper.builder().build(),
            new EventIngestMetrics(meterRegistry),
            new DatabaseOperationTimer(meterRegistry),
            platformTransactionManager);
  }

  @Test
  void 저장에_성공하면_중복이_아니라고_응답한다() {
    when(eventRepository.insertIfAbsent(any(), any(), any(), any(), any(), any())).thenReturn(1);

    IngestEventResponse response = eventService.ingest(ORGANIZATION_ID, request("tx-1"));

    assertThat(response.transactionId()).isEqualTo("tx-1");
    assertThat(response.duplicate()).isFalse();
  }

  @Test
  void 이미_있는_키라_저장이_건너뛰어지면_중복이라고_응답한다() {
    when(eventRepository.insertIfAbsent(any(), any(), any(), any(), any(), any())).thenReturn(0);

    assertThat(eventService.ingest(ORGANIZATION_ID, request("tx-1")).duplicate()).isTrue();
  }

  @Test
  void 저장에_성공하면_stored_건수를_기록한다() {
    when(eventRepository.insertIfAbsent(any(), any(), any(), any(), any(), any())).thenReturn(1);

    eventService.ingest(ORGANIZATION_ID, request("tx-1"));

    assertThat(ingestCount("stored")).isEqualTo(1.0);
    assertThat(ingestCount("duplicate")).isZero();
  }

  @Test
  void 저장이_건너뛰어지면_duplicate_건수만_기록한다() {
    when(eventRepository.insertIfAbsent(any(), any(), any(), any(), any(), any())).thenReturn(0);

    eventService.ingest(ORGANIZATION_ID, request("tx-1"));

    assertThat(ingestCount("duplicate")).isEqualTo(1.0);
    assertThat(ingestCount("stored")).isZero();
  }

  @Test
  void 받는_기간_밖이지만_이미_저장된_이벤트도_duplicate_건수로_센다() {
    when(eventRepository.exists(ORGANIZATION_ID, "tx-1")).thenReturn(true);
    IngestEventRequest oldRequest =
        new IngestEventRequest(
            "tx-1", CUSTOMER_ID, "chat_completion", Map.of(), OCCURRED_AT.minusDays(36));

    eventService.ingest(ORGANIZATION_ID, oldRequest);

    assertThat(ingestCount("duplicate")).isEqualTo(1.0);
    verify(eventRepository, never()).insertIfAbsent(any(), any(), any(), any(), any(), any());
  }

  @Test
  void 중복_키_예외도_중복_성공으로_바꾸지_않고_그대로_올려_보낸다() {
    DuplicateKeyException duplicateKey =
        new DuplicateKeyException("duplicate key value violates unique constraint");
    when(eventRepository.insertIfAbsent(any(), any(), any(), any(), any(), any()))
        .thenThrow(duplicateKey);

    assertThatThrownBy(() -> eventService.ingest(ORGANIZATION_ID, request("tx-1")))
        .isSameAs(duplicateKey);
  }

  @Test
  void 이_도입사의_고객이_아니면_저장하지_않고_예외를_던진다() {
    org.mockito.Mockito.doThrow(new NotFoundException(ErrorCode.CUSTOMER_NOT_FOUND))
        .when(customerDirectory)
        .check(ORGANIZATION_ID, CUSTOMER_ID);

    assertThatThrownBy(() -> eventService.ingest(ORGANIZATION_ID, request("tx-1")))
        .isInstanceOf(NotFoundException.class)
        .extracting(exception -> ((BusinessException) exception).getErrorCode())
        .isEqualTo(ErrorCode.CUSTOMER_NOT_FOUND);

    verify(eventRepository, never()).insertIfAbsent(any(), any(), any(), any(), any(), any());
  }

  @Test
  void 고객이_없어_거절하면_rejected_건수를_기록한다() {
    org.mockito.Mockito.doThrow(new NotFoundException(ErrorCode.CUSTOMER_NOT_FOUND))
        .when(customerDirectory)
        .check(ORGANIZATION_ID, CUSTOMER_ID);

    assertThatThrownBy(() -> eventService.ingest(ORGANIZATION_ID, request("tx-1")))
        .isInstanceOf(NotFoundException.class);

    assertThat(ingestCount("rejected")).isEqualTo(1.0);
  }

  @Test
  void 받는_기간_밖이라_거절하면_rejected_건수를_기록한다() {
    IngestEventRequest oldRequest =
        new IngestEventRequest(
            "tx-1", CUSTOMER_ID, "chat_completion", Map.of(), OCCURRED_AT.minusDays(36));

    assertThatThrownBy(() -> eventService.ingest(ORGANIZATION_ID, oldRequest))
        .isInstanceOf(BusinessException.class);

    assertThat(ingestCount("rejected")).isEqualTo(1.0);
  }

  @Test
  void 거절하면_오류_code를_reason으로_센다() {
    doThrow(new NotFoundException(ErrorCode.CUSTOMER_NOT_FOUND))
        .when(customerDirectory)
        .check(ORGANIZATION_ID, CUSTOMER_ID);

    assertThatThrownBy(() -> eventService.ingest(ORGANIZATION_ID, request("tx-1")))
        .isInstanceOf(NotFoundException.class);

    assertThat(ingestCount("rejected", "customer_not_found")).isEqualTo(1.0);
  }

  @Test
  void 커밋이_실패하면_stored가_아니라_failed로_센다() {
    when(eventRepository.insertIfAbsent(any(), any(), any(), any(), any(), any())).thenReturn(1);
    doThrow(new TransactionSystemException("커밋 실패")).when(platformTransactionManager).commit(any());

    assertThatThrownBy(() -> eventService.ingest(ORGANIZATION_ID, request("tx-1")))
        .isInstanceOf(TransactionSystemException.class);

    assertThat(ingestCount("failed")).isEqualTo(1.0);
    assertThat(ingestCount("stored")).isZero();
  }

  @Test
  void 저장_중_DB_오류가_나면_failed로_센다() {
    when(eventRepository.insertIfAbsent(any(), any(), any(), any(), any(), any()))
        .thenThrow(new QueryTimeoutException("시간 초과"));

    assertThatThrownBy(() -> eventService.ingest(ORGANIZATION_ID, request("tx-1")))
        .isInstanceOf(QueryTimeoutException.class);

    assertThat(ingestCount("failed")).isEqualTo(1.0);
  }

  @Test
  void 저장된_이벤트의_발생_시각부터_받은_시각까지를_도착_지연으로_기록한다() {
    when(eventRepository.insertIfAbsent(any(), any(), any(), any(), any(), any())).thenReturn(1);
    Duration anHour = Duration.ofHours(1);
    IngestEventRequest anHourLate =
        new IngestEventRequest(
            "tx-1", CUSTOMER_ID, "chat_completion", Map.of(), OffsetDateTime.now().minus(anHour));

    eventService.ingest(ORGANIZATION_ID, anHourLate);

    assertThat(arrivalDelaySeconds()).isCloseTo(anHour.toSeconds(), within(1.0));
  }

  @Test
  void 중복이면_도착_지연을_기록하지_않는다() {
    when(eventRepository.insertIfAbsent(any(), any(), any(), any(), any(), any())).thenReturn(0);

    eventService.ingest(ORGANIZATION_ID, request("tx-1"));

    assertThat(arrivalDelayCount()).isZero();
  }

  @Test
  void properties는_판정_없이_그대로_직렬화해_넘긴다() {
    when(eventRepository.insertIfAbsent(any(), any(), any(), any(), any(), any())).thenReturn(1);

    eventService.ingest(
        ORGANIZATION_ID,
        new IngestEventRequest(
            "tx-1", CUSTOMER_ID, "chat_completion", Map.of("whatever", "value"), OCCURRED_AT));

    verify(eventRepository)
        .insertIfAbsent(
            eq(ORGANIZATION_ID),
            eq("tx-1"),
            eq(CUSTOMER_ID),
            eq("chat_completion"),
            anyString(),
            eq(OCCURRED_AT));
  }

  private double ingestCount(String result) {
    return meterRegistry
        .get(EventIngestMetrics.INGEST_COUNTER_NAME)
        .tag(EventIngestMetrics.RESULT_TAG, result)
        .counters()
        .stream()
        .mapToDouble(Counter::count)
        .sum();
  }

  private double ingestCount(String result, String reason) {
    return meterRegistry
        .get(EventIngestMetrics.INGEST_COUNTER_NAME)
        .tag(EventIngestMetrics.RESULT_TAG, result)
        .tag(EventIngestMetrics.REASON_TAG, reason)
        .counters()
        .stream()
        .mapToDouble(Counter::count)
        .sum();
  }

  private double arrivalDelaySeconds() {
    return meterRegistry.get(EventIngestMetrics.ARRIVAL_DELAY_TIMER_NAME).timers().stream()
        .mapToDouble(timer -> timer.totalTime(TimeUnit.SECONDS))
        .sum();
  }

  private long arrivalDelayCount() {
    return meterRegistry.get(EventIngestMetrics.ARRIVAL_DELAY_TIMER_NAME).timers().stream()
        .mapToLong(Timer::count)
        .sum();
  }

  private IngestEventRequest request(String transactionId) {
    return new IngestEventRequest(
        transactionId,
        CUSTOMER_ID,
        "chat_completion",
        Map.of("model", "gpt-4o-mini", "token", 1200),
        OCCURRED_AT);
  }

  @Test
  void 발생한_지_35일이_되기_직전의_이벤트는_받는다() {
    Instant now = OffsetDateTime.parse("2026-10-20T14:00:00+09:00").toInstant();
    Instant occurredAt = OffsetDateTime.parse("2026-09-15T14:00:00.000000001+09:00").toInstant();

    assertThat(EventIngestionService.isTooOld(occurredAt, now)).isFalse();
  }

  @Test
  void 발생한_지_정확히_35일인_이벤트는_받는다() {
    Instant now = OffsetDateTime.parse("2026-10-20T14:00:00+09:00").toInstant();
    Instant occurredAt = Instant.parse("2026-09-15T05:00:00Z");

    assertThat(EventIngestionService.isTooOld(occurredAt, now)).isFalse();
  }

  @Test
  void 발생한_지_35일이_넘은_이벤트는_거절한다() {
    Instant now = OffsetDateTime.parse("2026-10-20T14:00:00+09:00").toInstant();
    Instant occurredAt = OffsetDateTime.parse("2026-09-15T13:59:59.999999999+09:00").toInstant();

    assertThat(EventIngestionService.isTooOld(occurredAt, now)).isTrue();
  }
}
