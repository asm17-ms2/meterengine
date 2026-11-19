package com.meterengine.dashboard.service;

import com.meterengine.global.time.BillingMonth;
import java.time.LocalTime;
import java.time.OffsetDateTime;

/** 인사이트를 하루에 한 번 다시 계산하는 때. 예약 작업과 "오늘 계산한 값인가"의 판단이 같은 시각을 쓴다. */
public final class DashboardInsightsSchedule {
  public static final String JOB_NAME = "dashboard_insights_refresh";
  // 04:00의 청구서 자동 확정이 끝난 뒤에 돌아야 그날 확정한 청구서가 금액에 들어간다.
  public static final String REFRESH_CRON = "0 0 5 * * *";
  private static final LocalTime REFRESH_TIME = LocalTime.of(5, 0);

  private DashboardInsightsSchedule() {}

  /** now 이전의 가장 가까운 다시 계산 시각. 이 뒤에 계산한 값이면 오늘 값이다. */
  public static OffsetDateTime latestRefreshAt(OffsetDateTime now) {
    OffsetDateTime billingNow = now.atZoneSameInstant(BillingMonth.ZONE).toOffsetDateTime();
    OffsetDateTime todayRefreshAt =
        billingNow.toLocalDate().atTime(REFRESH_TIME).atZone(BillingMonth.ZONE).toOffsetDateTime();
    return billingNow.isBefore(todayRefreshAt) ? todayRefreshAt.minusDays(1) : todayRefreshAt;
  }
}
