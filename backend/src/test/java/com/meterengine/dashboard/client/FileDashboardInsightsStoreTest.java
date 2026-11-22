package com.meterengine.dashboard.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meterengine.dashboard.dto.DashboardInsightsKey;
import com.meterengine.dashboard.dto.DashboardInsightsResponse;
import com.meterengine.dashboard.dto.DashboardInsightsResponse.DailyEventCount;
import com.meterengine.dashboard.dto.DashboardInsightsResponse.MonthlyAmount;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class FileDashboardInsightsStoreTest {
  private final DashboardInsightsKey key =
      new DashboardInsightsKey(UUID.randomUUID(), UUID.randomUUID(), YearMonth.of(2026, 10));
  @TempDir private Path directory;
  private FileDashboardInsightsStore store;

  @BeforeEach
  void setUp() {
    store = new FileDashboardInsightsStore(directory, JsonMapper.builder().build());
  }

  @Test
  void 저장한_적이_없으면_없음을_준다() {
    assertThat(store.find(key)).isEmpty();
  }

  @Test
  void 저장한_값을_같은_조직과_환경과_달로_다시_읽는다() {
    store.save(key, insights(12));

    assertThat(store.find(key))
        .hasValueSatisfying(
            found -> {
              assertThat(found.calculatedAt())
                  .isEqualTo(OffsetDateTime.parse("2026-10-15T05:00:00+09:00"));
              assertThat(found.monthlyAmounts())
                  .containsExactly(new MonthlyAmount("2026-10", 1100, 1100, 1000, 0, 0));
              assertThat(found.dailyEventCounts())
                  .containsExactly(new DailyEventCount(LocalDate.of(2026, 10, 1), 12));
            });
    assertThat(
            store.find(
                new DashboardInsightsKey(key.organizationId(), UUID.randomUUID(), key.month())))
        .isEmpty();
    assertThat(
            store.find(
                new DashboardInsightsKey(
                    key.organizationId(), key.environmentScopeId(), key.month().plusMonths(1))))
        .isEmpty();
  }

  @Test
  void 다시_저장하면_앞의_값을_바꿔_놓는다() {
    store.save(key, insights(1));
    store.save(key, insights(2));

    assertThat(store.find(key).orElseThrow().dailyEventCounts())
        .extracting(DailyEventCount::eventCount)
        .containsExactly(2L);
  }

  @Test
  void 깨진_파일은_어느_파일인지_담은_예외로_알린다() throws IOException {
    store.save(key, insights(1));
    Files.writeString(directory.resolve(key.path()), "{not json");

    assertThatThrownBy(() -> store.find(key))
        .isInstanceOf(DashboardInsightsStoreException.class)
        .hasMessageContaining("2026-10.json");
  }

  static DashboardInsightsResponse insights(long eventCount) {
    return new DashboardInsightsResponse(
        "2026-10",
        OffsetDateTime.parse("2026-10-15T05:00:00+09:00"),
        List.of(new MonthlyAmount("2026-10", 1100, 1100, 1000, 0, 0)),
        List.of(new DailyEventCount(LocalDate.of(2026, 10, 1), eventCount)));
  }
}
