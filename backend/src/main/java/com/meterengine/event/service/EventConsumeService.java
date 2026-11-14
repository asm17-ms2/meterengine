package com.meterengine.event.service;

import com.meterengine.customer.api.CustomerDirectory;
import com.meterengine.event.client.EventDeadLetterPublisher;
import com.meterengine.event.config.EventKafkaConfig;
import com.meterengine.event.dto.EventDeadLetterReason;
import com.meterengine.event.dto.EventIngestMessage;
import com.meterengine.event.dto.EventIngestRecord;
import com.meterengine.event.dto.EventMessageInsertion;
import com.meterengine.event.repository.EventRejectionRepository;
import com.meterengine.event.repository.EventRepository;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.security.BusinessEnvironment;
import com.meterengine.global.security.BusinessEnvironmentContext;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// 토픽에서 읽은 묶음을 event에 넣는다. 수집 서버가 형식과 받는 기간을 이미 봤으므로 여기서는 고객과 중복만 본다.
// 메시지 탓에 못 넣는 것은 DLQ로 보내고 넘어간다. DB 탓에 못 넣으면 던져서 같은 묶음을 다시 읽게 한다.
@Service
@ConditionalOnProperty(name = EventKafkaConfig.WRITE_PATH, havingValue = EventKafkaConfig.KAFKA)
public class EventConsumeService {

  private static final String SCHEMA_VERSION = "schema_version";

  private final EventRepository eventRepository;
  private final EventRejectionRepository eventRejectionRepository;
  private final CustomerDirectory customerDirectory;
  private final EventDeadLetterPublisher eventDeadLetterPublisher;
  private final EventConsumeMetrics eventConsumeMetrics;
  private final JsonMapper jsonMapper;
  private final TransactionTemplate transactionTemplate;
  private final Clock clock;

  EventConsumeService(
      EventRepository eventRepository,
      EventRejectionRepository eventRejectionRepository,
      CustomerDirectory customerDirectory,
      EventDeadLetterPublisher eventDeadLetterPublisher,
      EventConsumeMetrics eventConsumeMetrics,
      JsonMapper jsonMapper,
      PlatformTransactionManager platformTransactionManager,
      Clock clock) {
    this.eventRepository = eventRepository;
    this.eventRejectionRepository = eventRejectionRepository;
    this.customerDirectory = customerDirectory;
    this.eventDeadLetterPublisher = eventDeadLetterPublisher;
    this.eventConsumeMetrics = eventConsumeMetrics;
    // properties의 숫자를 double로 읽으면 자릿수가 바뀌어 DB에 다른 값이 들어간다.
    this.jsonMapper =
        jsonMapper
            .rebuild()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS)
            .build();
    this.transactionTemplate = new TransactionTemplate(platformTransactionManager);
    this.clock = clock;
  }

  // 예외 없이 끝나야 호출한 쪽이 오프셋을 넘긴다. 다시 불려도 같은 결과가 되도록 저장은 ON CONFLICT로 한다.
  public void consume(List<EventIngestRecord> eventIngestRecords) {
    List<DeadLetter> deadLetters = new ArrayList<>();
    List<ReadEvent> readEvents = new ArrayList<>();
    for (EventIngestRecord eventIngestRecord : eventIngestRecords) {
      try {
        readEvents.add(new ReadEvent(eventIngestRecord, read(eventIngestRecord)));
      } catch (UnreadableMessageException unreadable) {
        deadLetters.add(new DeadLetter(eventIngestRecord, unreadable.reason));
      }
    }
    StoreResult stored = store(readEvents);
    // DLQ 발행을 기다리는 시간이 섞이지 않게 DB 커밋이 끝난 바로 뒤의 시각을 잡는다.
    Instant storedAt = clock.instant();
    // DLQ 발행이 던져 묶음을 다시 읽으면 방금 저장한 건이 중복으로 보인다. 커밋한 결과는 발행 전에 센다.
    recordCommitted(stored.tally(), storedAt);
    deadLetters.addAll(stored.unstorable());
    deadLetters.forEach(
        deadLetter -> eventDeadLetterPublisher.publish(deadLetter.record(), deadLetter.reason()));
    deadLetters.forEach(deadLetter -> eventConsumeMetrics.recordDeadLetter(deadLetter.reason()));
  }

  // 한 트랜잭션에 묶어 넣는다. 한 건이 제약에 걸리면 묶음 전체가 되돌려지므로 그때만 한 건씩 다시 넣어 그 건을 가려낸다.
  private StoreResult store(List<ReadEvent> readEvents) {
    try {
      return new StoreResult(storeInTransaction(readEvents), List.of());
    } catch (DataIntegrityViolationException violation) {
      return storeOneByOne(readEvents);
    }
  }

  // 혼자 넣어도 DB가 받지 않는 건이 묶음을 실패시킨 건이다.
  private StoreResult storeOneByOne(List<ReadEvent> readEvents) {
    Tally tally = new Tally();
    List<DeadLetter> unstorable = new ArrayList<>();
    for (ReadEvent readEvent : readEvents) {
      try {
        tally.add(storeInTransaction(List.of(readEvent)));
      } catch (DataIntegrityViolationException violation) {
        unstorable.add(
            new DeadLetter(readEvent.record(), EventDeadLetterReason.CONSTRAINT_VIOLATION));
      }
    }
    return new StoreResult(tally, unstorable);
  }

  private Tally storeInTransaction(List<ReadEvent> readEvents) {
    return transactionTemplate.execute(status -> storeByEnvironment(readEvents));
  }

  // 고객과 중복은 도입사의 환경 안에서 판정하므로 환경별로 나눠 넣는다.
  private Tally storeByEnvironment(List<ReadEvent> readEvents) {
    Map<BusinessEnvironment, List<EventIngestMessage>> messagesByEnvironment =
        new LinkedHashMap<>();
    for (ReadEvent readEvent : readEvents) {
      EventIngestMessage message = readEvent.message();
      messagesByEnvironment
          .computeIfAbsent(
              new BusinessEnvironment(message.organizationId(), message.environmentId(), null),
              environment -> new ArrayList<>())
          .add(message);
    }
    Tally tally = new Tally();
    messagesByEnvironment.forEach(
        (environment, messages) ->
            tally.add(
                BusinessEnvironmentContext.run(
                    environment, () -> storeEnvironment(environment.organizationId(), messages))));
    return tally;
  }

  // 판정 순서는 요청 안에서 저장하는 배치(EventBatchService)와 같다: 없는 고객, 중복, 보관한 고객.
  private Tally storeEnvironment(UUID organizationId, List<EventIngestMessage> messages) {
    Set<String> existing =
        new HashSet<>(
            eventRepository.findExistingTransactionIds(
                organizationId,
                messages.stream().map(EventIngestMessage::transactionId).distinct().toList()));
    Map<UUID, Boolean> customers =
        customerDirectory.active(
            organizationId,
            messages.stream().map(EventIngestMessage::customerId).distinct().toList());
    Tally tally = new Tally();
    Set<String> accepted = new HashSet<>();
    List<EventMessageInsertion> insertions = new ArrayList<>();
    for (EventIngestMessage message : messages) {
      if (!customers.containsKey(message.customerId())) {
        reject(message, ErrorCode.CUSTOMER_NOT_FOUND, tally);
      } else if (existing.contains(message.transactionId())
          || accepted.contains(message.transactionId())) {
        tally.duplicate++;
      } else if (!customers.get(message.customerId())) {
        reject(message, ErrorCode.CUSTOMER_ARCHIVED, tally);
      } else {
        accepted.add(message.transactionId());
        insertions.add(
            new EventMessageInsertion(
                message, jsonMapper.writeValueAsString(message.properties())));
      }
    }
    insertions.sort(Comparator.comparing(insertion -> insertion.message().transactionId()));
    Set<String> inserted = new HashSet<>(eventRepository.insertAllMessagesIfAbsent(insertions));
    for (EventMessageInsertion insertion : insertions) {
      if (inserted.contains(insertion.message().transactionId())) {
        tally.storedAcceptedAts.add(insertion.message().acceptedAt());
      } else {
        tally.duplicate++;
      }
    }
    return tally;
  }

  // 묶음을 다시 읽어 이미 남긴 거절은 다시 남기지 않고 세지도 않는다.
  private void reject(EventIngestMessage message, ErrorCode errorCode, Tally tally) {
    if (eventRejectionRepository.insertMessageIfAbsent(message, errorCode)) {
      tally.rejectedCountByErrorCode.merge(errorCode, 1, Integer::sum);
    }
  }

  // schema_version을 먼저 봐야 모르는 형식을 지금 형식으로 잘못 읽지 않는다.
  private EventIngestMessage read(EventIngestRecord eventIngestRecord) {
    if (eventIngestRecord.value() == null) {
      throw new UnreadableMessageException(EventDeadLetterReason.INVALID_MESSAGE);
    }
    try {
      JsonNode node = jsonMapper.readTree(eventIngestRecord.value());
      if (node.path(SCHEMA_VERSION).asInt(0) != EventIngestMessage.CURRENT_SCHEMA_VERSION) {
        throw new UnreadableMessageException(EventDeadLetterReason.UNKNOWN_SCHEMA_VERSION);
      }
      return complete(jsonMapper.treeToValue(node, EventIngestMessage.class))
          .orElseThrow(() -> new UnreadableMessageException(EventDeadLetterReason.INVALID_MESSAGE));
    } catch (JacksonException | IllegalArgumentException unreadable) {
      throw new UnreadableMessageException(EventDeadLetterReason.INVALID_MESSAGE);
    }
  }

  // event 행의 NOT NULL 열을 채우는 값이 다 있어야 넣을 수 있다.
  private static Optional<EventIngestMessage> complete(EventIngestMessage message) {
    boolean complete =
        message.organizationId() != null
            && message.transactionId() != null
            && message.customerId() != null
            && message.type() != null
            && message.occurredAt() != null
            && message.acceptedAt() != null
            && message.actorType() != null;
    return complete ? Optional.of(message) : Optional.empty();
  }

  private void recordCommitted(Tally tally, Instant storedAt) {
    tally.storedAcceptedAts.forEach(
        acceptedAt -> eventConsumeMetrics.recordStored(Duration.between(acceptedAt, storedAt)));
    eventConsumeMetrics.recordDuplicate(tally.duplicate);
    tally.rejectedCountByErrorCode.forEach(
        (errorCode, count) -> {
          for (int rejection = 0; rejection < count; rejection++) {
            eventConsumeMetrics.recordRejected(errorCode);
          }
        });
  }

  private record ReadEvent(EventIngestRecord record, EventIngestMessage message) {}

  private record DeadLetter(EventIngestRecord record, EventDeadLetterReason reason) {}

  // unstorable은 DB가 받지 않아 DLQ로 보낼 것이다.
  private record StoreResult(Tally tally, List<DeadLetter> unstorable) {}

  private static final class Tally {
    // 저장한 이벤트마다 수집 서버가 받은 시각. 개수가 저장 건수다.
    private final List<Instant> storedAcceptedAts = new ArrayList<>();
    private int duplicate;
    private final Map<ErrorCode, Integer> rejectedCountByErrorCode = new EnumMap<>(ErrorCode.class);

    private void add(Tally other) {
      storedAcceptedAts.addAll(other.storedAcceptedAts);
      duplicate += other.duplicate;
      other.rejectedCountByErrorCode.forEach(
          (errorCode, count) -> rejectedCountByErrorCode.merge(errorCode, count, Integer::sum));
    }
  }

  private static final class UnreadableMessageException extends RuntimeException {
    private final EventDeadLetterReason reason;

    private UnreadableMessageException(EventDeadLetterReason reason) {
      this.reason = reason;
    }
  }
}
