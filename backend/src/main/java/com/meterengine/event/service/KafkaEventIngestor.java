package com.meterengine.event.service;

import com.meterengine.event.client.EventIngestPublisher;
import com.meterengine.event.config.EventKafkaConfig;
import com.meterengine.event.config.EventKafkaProperties;
import com.meterengine.event.dto.EventIngestMessage;
import com.meterengine.event.dto.IngestEventBatchRequest;
import com.meterengine.event.dto.IngestEventBatchResponse;
import com.meterengine.event.dto.IngestEventBatchResponse.EventResult;
import com.meterengine.event.dto.IngestEventRequest;
import com.meterengine.event.dto.IngestEventResponse;
import com.meterengine.event.service.EventBatchParser.ParsedEventBatch;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.ErrorResponse;
import com.meterengine.global.error.ErrorResponse.FieldError;
import com.meterengine.global.error.InvalidRequestException;
import com.meterengine.global.error.ServiceUnavailableException;
import com.meterengine.global.security.BusinessEnvironmentContext;
import com.meterengine.global.security.RequestIdentity;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

// 요청 안에서는 형식과 받는 기간만 보고 카프카에 발행한다. 고객 확인과 저장, 중복 판정은 컨슈머가 한다.
// 그래서 요청을 처리하는 동안 DB에 쓰지 않고, 여기서 거절한 이벤트는 거절 기록에 남기지 않는다.
@Component
@ConditionalOnProperty(name = EventKafkaConfig.WRITE_PATH, havingValue = EventKafkaConfig.KAFKA)
public class KafkaEventIngestor implements EventIngestor {

  private static final Logger logger = LoggerFactory.getLogger(KafkaEventIngestor.class);
  private static final String TRACEPARENT_HEADER = "traceparent";

  private final EventIngestPublisher eventIngestPublisher;
  private final EventBatchParser eventBatchParser;
  private final EventIngestMetrics eventIngestMetrics;
  private final Duration publishTimeout;
  private final Clock clock;

  KafkaEventIngestor(
      EventIngestPublisher eventIngestPublisher,
      EventBatchParser eventBatchParser,
      EventIngestMetrics eventIngestMetrics,
      EventKafkaProperties eventKafkaProperties,
      Clock clock) {
    this.eventIngestPublisher = eventIngestPublisher;
    this.eventBatchParser = eventBatchParser;
    this.eventIngestMetrics = eventIngestMetrics;
    this.publishTimeout = eventKafkaProperties.publishTimeout();
    this.clock = clock;
  }

  @Override
  public IngestEventResponse ingest(UUID organizationId, IngestEventRequest request) {
    Instant acceptedAt = now();
    if (EventIngestionService.isTooOld(request.occurredAt().toInstant(), acceptedAt)) {
      eventIngestMetrics.recordRejected(ErrorCode.EVENT_TOO_OLD);
      throw new InvalidRequestException(
          ErrorCode.EVENT_TOO_OLD,
          List.of(new FieldError("timestamp", "수집 판정 시각보다 35일 넘게 과거인 시각입니다")));
    }
    CompletableFuture<Void> publishing = publish(organizationId, request, acceptedAt);
    Optional<Exception> failure = publishFailure(publishing, acceptedAt.plus(publishTimeout));
    if (failure.isPresent()) {
      logger.warn("event publish was not confirmed", failure.get());
      eventIngestMetrics.recordFailed(1);
      throw new ServiceUnavailableException(ErrorCode.EVENT_INGEST_UNAVAILABLE);
    }
    eventIngestMetrics.recordAccepted(arrivalDelay(request, acceptedAt));
    return IngestEventResponse.accepted(request.transactionId());
  }

  // 원소마다 따로 발행하므로 일부만 실패할 수 있다. 실패한 원소는 같은 transaction_id로 다시 보내면 된다.
  @Override
  public IngestEventBatchResponse ingestBatch(UUID organizationId, IngestEventBatchRequest batch) {
    Instant acceptedAt = now();
    ParsedEventBatch parsed = eventBatchParser.parse(batch);
    List<EventResult> results = parsed.results();
    Map<Integer, CompletableFuture<Void>> publishingByIndex = new LinkedHashMap<>();
    Instant deadline = acceptedAt.plus(publishTimeout);
    parsed
        .requestsByIndex()
        .forEach(
            (index, request) -> {
              if (EventIngestionService.isTooOld(request.occurredAt().toInstant(), acceptedAt)) {
                results.set(index, EventBatchParser.tooOld(index, request.transactionId()));
              } else {
                publishingByIndex.put(
                    index, publishBefore(deadline, organizationId, request, acceptedAt));
              }
            });
    List<Exception> failures = new ArrayList<>();
    publishingByIndex.forEach(
        (index, publishing) -> {
          String transactionId = parsed.requestsByIndex().get(index).transactionId();
          Optional<Exception> failure = publishFailure(publishing, deadline);
          failure.ifPresent(failures::add);
          results.set(
              index,
              failure.isEmpty()
                  ? new EventResult(index, transactionId, EventResult.ACCEPTED, null)
                  : new EventResult(
                      index,
                      transactionId,
                      EventResult.FAILED,
                      ErrorResponse.from(ErrorCode.EVENT_INGEST_UNAVAILABLE)));
        });
    logFailures(failures, publishingByIndex.size());
    recordResults(results, parsed.requestsByIndex(), acceptedAt);
    if (nothingAcceptedBecauseOfFailure(results)) {
      throw new ServiceUnavailableException(ErrorCode.EVENT_INGEST_UNAVAILABLE);
    }
    return new IngestEventBatchResponse(results);
  }

  // 토픽 정보를 못 얻으면 발행 호출마다 max.block.ms만큼 막힌다.
  // 상한이 지나면 남은 원소는 발행을 부르지 않고 실패로 둬서 요청 스레드를 놓는다.
  private CompletableFuture<Void> publishBefore(
      Instant deadline, UUID organizationId, IngestEventRequest request, Instant acceptedAt) {
    if (!clock.instant().isBefore(deadline)) {
      return CompletableFuture.failedFuture(
          new TimeoutException("publish deadline passed before sending"));
    }
    return publish(organizationId, request, acceptedAt);
  }

  private CompletableFuture<Void> publish(
      UUID organizationId, IngestEventRequest request, Instant acceptedAt) {
    RequestIdentity identity = RequestIdentity.current();
    return eventIngestPublisher.publish(
        new EventIngestMessage(
            EventIngestMessage.CURRENT_SCHEMA_VERSION,
            organizationId,
            BusinessEnvironmentContext.environmentId(),
            request.transactionId(),
            request.customerId(),
            request.type(),
            request.properties(),
            request.occurredAt(),
            acceptedAt,
            identity.requestId(),
            identity.actorType(),
            identity.actorId()),
        traceHeaders());
  }

  // 발행이 끝나길 기다려 실패 원인을 돌려준다. 비어 있으면 브로커가 받았다.
  // 프로듀서의 delivery.timeout.ms 안에 끝나지만 스레드가 끝없이 묶이지 않게 상한을 둔다.
  private Optional<Exception> publishFailure(CompletableFuture<Void> publishing, Instant deadline) {
    long remainingMillis = Math.max(0, Duration.between(clock.instant(), deadline).toMillis());
    try {
      publishing.get(remainingMillis, TimeUnit.MILLISECONDS);
      return Optional.empty();
    } catch (InterruptedException interruption) {
      Thread.currentThread().interrupt();
      return Optional.of(interruption);
    } catch (ExecutionException | TimeoutException failure) {
      return Optional.of(failure);
    }
  }

  // 브로커가 죽으면 배치의 원소가 한꺼번에 실패하므로 건수와 첫 원인만 한 줄로 남긴다.
  private static void logFailures(List<Exception> failures, int published) {
    if (!failures.isEmpty()) {
      logger.warn(
          "{} of {} batch events were not confirmed", failures.size(), published, failures.get(0));
    }
  }

  private void recordResults(
      List<EventResult> results,
      Map<Integer, IngestEventRequest> requestsByIndex,
      Instant acceptedAt) {
    for (EventResult result : results) {
      switch (result.result()) {
        case EventResult.ACCEPTED ->
            eventIngestMetrics.recordAccepted(
                arrivalDelay(requestsByIndex.get(result.index()), acceptedAt));
        case EventResult.FAILED -> eventIngestMetrics.recordFailed(1);
        default -> eventIngestMetrics.recordRejected(result.error().code());
      }
    }
  }

  private static boolean nothingAcceptedBecauseOfFailure(List<EventResult> results) {
    return results.stream().noneMatch(result -> EventResult.ACCEPTED.equals(result.result()))
        && results.stream().anyMatch(result -> EventResult.FAILED.equals(result.result()));
  }

  private static Duration arrivalDelay(IngestEventRequest request, Instant acceptedAt) {
    return Duration.between(request.occurredAt().toInstant(), acceptedAt);
  }

  // DB의 timestamptz가 마이크로초까지라 메시지의 받은 시각도 같은 정밀도로 맞춘다.
  private Instant now() {
    return clock.instant().truncatedTo(ChronoUnit.MICROS);
  }

  // 도입사가 보낸 추적 문맥이 있으면 메시지에 그대로 잇는다.
  private static Map<String, String> traceHeaders() {
    if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
        && attributes.getRequest().getHeader(TRACEPARENT_HEADER) != null) {
      return Map.of(TRACEPARENT_HEADER, attributes.getRequest().getHeader(TRACEPARENT_HEADER));
    }
    return Map.of();
  }
}
