package com.meterengine;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

public class TestClock extends Clock {

  private volatile Instant fixedInstant;

  public void fix(OffsetDateTime at) {
    fixedInstant = at.toInstant();
  }

  public void reset() {
    fixedInstant = null;
  }

  @Override
  public Instant instant() {
    Instant instant = fixedInstant;
    return instant == null ? Instant.now() : instant;
  }

  @Override
  public ZoneId getZone() {
    return ZoneOffset.UTC;
  }

  @Override
  public Clock withZone(ZoneId zone) {
    return new ZonedView(zone);
  }

  // 같은 시각을 다른 시간대로 읽는다. 고정한 시각을 바꾸면 이 시계도 따라간다.
  private final class ZonedView extends Clock {

    private final ZoneId zone;

    private ZonedView(ZoneId zone) {
      this.zone = zone;
    }

    @Override
    public Instant instant() {
      return TestClock.this.instant();
    }

    @Override
    public ZoneId getZone() {
      return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return TestClock.this.withZone(zone);
    }
  }
}
