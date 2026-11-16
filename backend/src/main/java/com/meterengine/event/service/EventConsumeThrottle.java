package com.meterengine.event.service;

import com.meterengine.event.config.EventKafkaProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

// 밀린 것을 비울 때 컨슈머가 DB가 받는 만큼 다 넣어 DB CPU가 96%까지 찼다(Discussion #601).
// 서버 한 대가 초당 넣는 건수를 묶는다. 밀린 것이 없으면 묶음이 작아 기다릴 일이 없다.
@Component
public class EventConsumeThrottle {

  private static final int UNLIMITED = 0;
  private static final long NANOS_PER_SECOND = Duration.ofSeconds(1).toNanos();
  // 상한을 너무 낮게 넣어도 다음 poll까지 5분(max.poll.interval.ms)을 넘기지 않게 묶는다.
  private static final Duration MAX_WAIT = Duration.ofSeconds(5);

  private final int maxRecordsPerSecond;
  private final EventConsumeMetrics eventConsumeMetrics;
  private final Clock clock;
  private Instant nextTurnAt = Instant.EPOCH;

  EventConsumeThrottle(
      EventKafkaProperties eventKafkaProperties,
      EventConsumeMetrics eventConsumeMetrics,
      Clock clock) {
    this.maxRecordsPerSecond = eventKafkaProperties.consumeMaxRecordsPerSecond();
    this.eventConsumeMetrics = eventConsumeMetrics;
    this.clock = clock;
  }

  public void awaitTurn(int recordCount) {
    Duration wait = reserveTurn(recordCount);
    if (wait.isZero()) {
      return;
    }
    eventConsumeMetrics.recordThrottled(wait);
    try {
      Thread.sleep(wait);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("저장할 차례를 기다리다 중단됐다. 이 묶음은 저장하지 않았다", interrupted);
    }
  }

  // 묶음의 몫은 건수를 상한으로 나눈 시간이다. 앞 묶음들의 몫이 끝나는 때가 이 묶음의 차례이고, 서버의 스레드들이 한 줄에 선다.
  synchronized Duration reserveTurn(int recordCount) {
    if (maxRecordsPerSecond == UNLIMITED) {
      return Duration.ZERO;
    }
    Instant now = clock.instant();
    Instant turnAt = earliest(latest(nextTurnAt, now), now.plus(MAX_WAIT));
    nextTurnAt = turnAt.plusNanos(recordCount * NANOS_PER_SECOND / maxRecordsPerSecond);
    return Duration.between(now, turnAt);
  }

  private static Instant latest(Instant left, Instant right) {
    return left.isAfter(right) ? left : right;
  }

  private static Instant earliest(Instant left, Instant right) {
    return left.isBefore(right) ? left : right;
  }
}
