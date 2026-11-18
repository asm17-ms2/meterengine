package com.meterengine.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meterengine.dashboard.dto.DashboardInsightsKey;
import com.meterengine.dashboard.dto.DashboardInsightsResponse;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DashboardInsightsMemoryTest {
  private static final OffsetDateTime TODAY_REFRESH_AT =
      OffsetDateTime.parse("2026-10-15T05:00:00+09:00");
  private final DashboardInsightsMemory memory = new DashboardInsightsMemory();
  private final DashboardInsightsKey key =
      new DashboardInsightsKey(UUID.randomUUID(), UUID.randomUUID(), YearMonth.of(2026, 10));

  @Test
  void 올린_적이_없는_조직은_없음을_준다() {
    assertThat(memory.find(key, TODAY_REFRESH_AT)).isEmpty();
  }

  @Test
  void 오늘_계산한_값은_다시_계산하는_시각이_오기_전까지_들고_있는다() {
    DashboardInsightsResponse insights = calculatedAt("2026-10-15T09:00:00+09:00");
    memory.hold(key, insights, TODAY_REFRESH_AT);

    assertThat(memory.find(key, TODAY_REFRESH_AT)).contains(insights);
  }

  @Test
  void 다시_계산하는_시각이_지나면_들고_있던_값을_모두_버린다() {
    memory.hold(key, calculatedAt("2026-10-15T09:00:00+09:00"), TODAY_REFRESH_AT);

    assertThat(memory.find(key, TODAY_REFRESH_AT.plusDays(1))).isEmpty();
  }

  @Test
  void 어제_계산한_값을_올려도_오늘_값으로_주지_않는다() {
    memory.hold(key, calculatedAt("2026-10-14T09:00:00+09:00"), TODAY_REFRESH_AT);

    assertThat(memory.find(key, TODAY_REFRESH_AT)).isEmpty();
  }

  private static DashboardInsightsResponse calculatedAt(String at) {
    return new DashboardInsightsResponse("2026-10", OffsetDateTime.parse(at), List.of(), List.of());
  }
}
