package com.meterengine.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class DashboardInsightsScheduleTest {
  @Test
  void 새벽_다섯_시가_지났으면_오늘_다섯_시가_기준이다() {
    assertThat(
            DashboardInsightsSchedule.latestRefreshAt(
                OffsetDateTime.parse("2026-10-15T05:00:00+09:00")))
        .isEqualTo(OffsetDateTime.parse("2026-10-15T05:00:00+09:00"));
    assertThat(
            DashboardInsightsSchedule.latestRefreshAt(
                OffsetDateTime.parse("2026-10-15T23:59:59+09:00")))
        .isEqualTo(OffsetDateTime.parse("2026-10-15T05:00:00+09:00"));
  }

  @Test
  void 새벽_다섯_시_전이면_어제_다섯_시가_기준이다() {
    assertThat(
            DashboardInsightsSchedule.latestRefreshAt(
                OffsetDateTime.parse("2026-10-15T04:59:59+09:00")))
        .isEqualTo(OffsetDateTime.parse("2026-10-14T05:00:00+09:00"));
  }

  @Test
  void 다른_시간대로_준_시각도_한국_시간으로_본다() {
    assertThat(
            DashboardInsightsSchedule.latestRefreshAt(OffsetDateTime.parse("2026-10-14T20:30:00Z")))
        .isEqualTo(OffsetDateTime.parse("2026-10-15T05:00:00+09:00"));
  }
}
