package com.meterengine.dashboard.client;

import com.meterengine.dashboard.dto.DashboardInsightsKey;
import com.meterengine.dashboard.dto.DashboardInsightsResponse;
import java.util.Optional;

/** 계산해 둔 인사이트를 모든 서버가 같이 보는 곳에 둔다. DB에서 다시 계산할 수 있는 값이라 잃어도 된다. */
public interface DashboardInsightsStore {
  Optional<DashboardInsightsResponse> find(DashboardInsightsKey key);

  void save(DashboardInsightsKey key, DashboardInsightsResponse insights);
}
