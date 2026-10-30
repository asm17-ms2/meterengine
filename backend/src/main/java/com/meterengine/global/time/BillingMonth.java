package com.meterengine.global.time;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;

public final class BillingMonth {
  public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

  private BillingMonth() {}

  public static OffsetDateTime now(Clock clock) {
    return OffsetDateTime.now(clock).atZoneSameInstant(ZONE).toOffsetDateTime();
  }

  public static OffsetDateTime start(YearMonth month) {
    return month.atDay(1).atStartOfDay(ZONE).toOffsetDateTime();
  }

  public static OffsetDateTime end(YearMonth month) {
    return start(month.plusMonths(1));
  }

  public static OffsetDateTime cutoff(YearMonth month, OffsetDateTime now) {
    OffsetDateTime end = end(month);
    return now.isBefore(end) ? now : end;
  }
}
