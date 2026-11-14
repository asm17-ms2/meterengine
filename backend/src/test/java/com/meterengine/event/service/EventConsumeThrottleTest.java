package com.meterengine.event.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meterengine.TestClock;
import com.meterengine.event.config.EventKafkaProperties;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

// 밀린 것을 비울 때 서버 한 대가 DB에 넣는 속도를 묶는다. 평소처럼 묶음이 작으면 기다리지 않아야 한다.
class EventConsumeThrottleTest {

  private static final OffsetDateTime START = OffsetDateTime.parse("2026-11-14T00:00:00Z");
  private static final int FULL_BATCH = 500;

  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final TestClock clock = new TestClock();

  @Test
  void 상한이_없으면_기다리지_않는다() {
    EventConsumeThrottle throttle = throttle(0);
    clock.fix(START);

    throttle.reserveTurn(FULL_BATCH);

    assertThat(throttle.reserveTurn(FULL_BATCH)).isZero();
  }

  @Test
  void 첫_묶음은_기다리지_않는다() {
    EventConsumeThrottle throttle = throttle(4_000);
    clock.fix(START);

    assertThat(throttle.reserveTurn(FULL_BATCH)).isZero();
  }

  // 초당 4,000건이면 500건의 몫은 125ms다. 40ms 뒤에 온 다음 묶음은 남은 85ms를 기다린다.
  @Test
  void 앞_묶음의_몫이_남았으면_그만큼_기다린다() {
    EventConsumeThrottle throttle = throttle(4_000);
    clock.fix(START);
    throttle.reserveTurn(FULL_BATCH);

    clock.fix(START.plus(Duration.ofMillis(40)));

    assertThat(throttle.reserveTurn(FULL_BATCH)).isEqualTo(Duration.ofMillis(85));
  }

  // 밀린 것이 없을 때는 몇 건씩 읽는다. 그 몫은 저장하는 동안 이미 지나가 있다.
  @Test
  void 묶음이_작으면_기다리지_않는다() {
    EventConsumeThrottle throttle = throttle(4_000);
    clock.fix(START);
    throttle.reserveTurn(5);

    clock.fix(START.plus(Duration.ofMillis(7)));

    assertThat(throttle.reserveTurn(5)).isZero();
  }

  @Test
  void 한_서버의_스레드들은_같은_줄에_선다() {
    EventConsumeThrottle throttle = throttle(4_000);
    clock.fix(START);

    assertThat(throttle.reserveTurn(FULL_BATCH)).isZero();
    assertThat(throttle.reserveTurn(FULL_BATCH)).isEqualTo(Duration.ofMillis(125));
    assertThat(throttle.reserveTurn(FULL_BATCH)).isEqualTo(Duration.ofMillis(250));
  }

  // 쉰 시간을 모아 두었다가 한꺼번에 넣으면 그 순간 DB가 다시 찬다.
  @Test
  void 한가했던_시간을_모아_두었다가_몰아서_넣지_못한다() {
    EventConsumeThrottle throttle = throttle(4_000);
    clock.fix(START);
    throttle.reserveTurn(FULL_BATCH);

    clock.fix(START.plus(Duration.ofSeconds(10)));

    assertThat(throttle.reserveTurn(FULL_BATCH)).isZero();
    assertThat(throttle.reserveTurn(FULL_BATCH)).isEqualTo(Duration.ofMillis(125));
  }

  // 상한을 너무 낮게 넣어도 다음 poll까지 5분을 넘기면 브로커가 이 서버를 그룹에서 뺀다.
  @Test
  void 한_번에_기다리는_시간은_5초를_넘지_않는다() {
    EventConsumeThrottle throttle = throttle(1);
    clock.fix(START);
    throttle.reserveTurn(FULL_BATCH);

    assertThat(throttle.reserveTurn(FULL_BATCH)).isEqualTo(Duration.ofSeconds(5));
    assertThat(throttle.reserveTurn(FULL_BATCH)).isEqualTo(Duration.ofSeconds(5));
  }

  @Test
  void 기다린_시간을_지표로_낸다() {
    EventConsumeThrottle throttle = throttle(100_000);
    clock.fix(START);

    throttle.awaitTurn(FULL_BATCH);
    throttle.awaitTurn(FULL_BATCH);

    assertThat(throttleTimer().count()).isEqualTo(1);
    assertThat(throttleTimer().totalTime(TimeUnit.MILLISECONDS)).isEqualTo(5.0);
  }

  @Test
  void 기다리지_않았으면_지표에_세지_않는다() {
    EventConsumeThrottle throttle = throttle(4_000);
    clock.fix(START);

    throttle.awaitTurn(FULL_BATCH);

    assertThat(throttleTimer().count()).isZero();
  }

  // 예외로 나가야 리스너가 오프셋을 넘기지 않아, 저장하지 못한 묶음을 다시 읽는다.
  @Test
  void 기다리다_중단되면_예외를_던지고_중단_표시를_남긴다() {
    EventConsumeThrottle throttle = throttle(4_000);
    clock.fix(START);
    throttle.reserveTurn(FULL_BATCH);
    Thread.currentThread().interrupt();

    try {
      assertThatThrownBy(() -> throttle.awaitTurn(FULL_BATCH))
          .isInstanceOf(IllegalStateException.class)
          .hasCauseInstanceOf(InterruptedException.class);
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
    } finally {
      Thread.interrupted();
    }
  }

  private EventConsumeThrottle throttle(int consumeMaxRecordsPerSecond) {
    return new EventConsumeThrottle(
        new EventKafkaProperties(
            "event.ingest",
            6,
            (short) 3,
            false,
            Duration.ofSeconds(20),
            "test",
            "event.dlq",
            Duration.ofSeconds(1),
            consumeMaxRecordsPerSecond),
        new EventConsumeMetrics(meterRegistry),
        clock);
  }

  private Timer throttleTimer() {
    return meterRegistry.get("meterengine.event.consume.throttle").timer();
  }
}
