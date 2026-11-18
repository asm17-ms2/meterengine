package com.meterengine.dashboard.service;

import com.meterengine.dashboard.dto.DashboardInsightsKey;
import com.meterengine.dashboard.dto.DashboardInsightsResponse;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** 이 서버에서 오늘 조회된 인사이트만 들고 있는다. 다시 계산하는 시각이 지나면 모두 버린다. */
@Component
public class DashboardInsightsMemory {
  private final Map<DashboardInsightsKey, DashboardInsightsResponse> insightsByKey =
      new ConcurrentHashMap<>();
  private OffsetDateTime heldSinceAt;

  public Optional<DashboardInsightsResponse> find(
      DashboardInsightsKey key, OffsetDateTime refreshedAt) {
    forgetBefore(refreshedAt);
    return Optional.ofNullable(insightsByKey.get(key))
        .filter(insights -> !insights.calculatedAt().isBefore(refreshedAt));
  }

  public void hold(
      DashboardInsightsKey key, DashboardInsightsResponse insights, OffsetDateTime refreshedAt) {
    forgetBefore(refreshedAt);
    insightsByKey.put(key, insights);
  }

  private synchronized void forgetBefore(OffsetDateTime refreshedAt) {
    if (heldSinceAt != null && heldSinceAt.isEqual(refreshedAt)) return;
    insightsByKey.clear();
    heldSinceAt = refreshedAt;
  }
}
