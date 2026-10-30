package com.meterengine.global.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class BillingMonthTest {
  private static final YearMonth AUGUST = YearMonth.of(2026, 8);

  @Test
  void 달의_시작과_끝은_KST_1일_자정이다() {
    assertThat(BillingMonth.start(AUGUST))
        .isEqualTo(OffsetDateTime.parse("2026-08-01T00:00:00+09:00"));
    assertThat(BillingMonth.end(AUGUST))
        .isEqualTo(OffsetDateTime.parse("2026-09-01T00:00:00+09:00"));
  }

  @Test
  void 지나간_달의_cutoff는_달의_끝이고_진행_중인_달은_지금이다() {
    OffsetDateTime now = OffsetDateTime.parse("2026-10-26T10:00:00+09:00");
    assertThat(BillingMonth.cutoff(AUGUST, now)).isEqualTo(BillingMonth.end(AUGUST));
    assertThat(BillingMonth.cutoff(YearMonth.of(2026, 10), now)).isEqualTo(now);
  }
}
