package com.meterengine.apikey.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meterengine.TestClock;
import com.meterengine.apikey.config.ApiKeyProperties;
import com.meterengine.apikey.repository.ApiKeySnapshotRepository;
import com.meterengine.global.security.ActiveOrganizations;
import com.meterengine.global.security.SecureTokens;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

class ApiKeyTableLoaderTest {

  private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-08T00:00:00Z");
  private static final Duration REFRESH_INTERVAL = Duration.ofMillis(10);
  private static final long REFRESH_WAIT_MILLIS = 5_000;

  private final ApiKeyTable apiKeyTable = new ApiKeyTable();
  private final ApiKeySnapshotRepository apiKeySnapshotRepository =
      mock(ApiKeySnapshotRepository.class);
  private final ActiveOrganizations activeOrganizations = mock(ActiveOrganizations.class);
  private final TestClock clock = new TestClock();
  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final ApiKeyTableLoader apiKeyTableLoader =
      new ApiKeyTableLoader(
          apiKeyTable,
          apiKeySnapshotRepository,
          activeOrganizations,
          new ApiKeyProperties(Duration.ofHours(24), Duration.ZERO, REFRESH_INTERVAL, 300),
          clock,
          meterRegistry);

  @Test
  void 시작할_때_DB가_답하지_않으면_읽힐_때까지_다시_읽는다() throws Exception {
    String rawKey = SecureTokens.generate("me_live_");
    when(apiKeySnapshotRepository.findUsableAt(any()))
        .thenThrow(new DataAccessResourceFailureException("connection refused"))
        .thenReturn(List.of(snapshotOf(rawKey)));

    apiKeyTableLoader.run(null);

    verify(apiKeySnapshotRepository, atLeast(2)).findUsableAt(any());
    assertThat(apiKeyTable.find(ApiKeyHash.of(rawKey))).isPresent();
  }

  @AfterEach
  void stopRefreshing() {
    apiKeyTableLoader.stopRefreshing();
  }

  @Test
  void 시작해_채운_뒤에는_예약_작업_설정_없이도_스스로_다시_읽는다() throws Exception {
    when(apiKeySnapshotRepository.findUsableAt(any())).thenReturn(List.of());

    apiKeyTableLoader.run(null);

    verify(apiKeySnapshotRepository, timeout(REFRESH_WAIT_MILLIS).atLeast(2)).findUsableAt(any());
  }

  @Test
  void 갱신이_실패하면_옛_테이블을_그대로_쓴다() {
    String rawKey = SecureTokens.generate("me_live_");
    when(apiKeySnapshotRepository.findUsableAt(any()))
        .thenReturn(List.of(snapshotOf(rawKey)))
        .thenThrow(new DataAccessResourceFailureException("connection refused"));
    apiKeyTableLoader.refresh();

    apiKeyTableLoader.refreshPeriodically();

    assertThat(apiKeyTable.find(ApiKeyHash.of(rawKey))).isPresent();
  }

  @Test
  void 지표는_테이블_크기와_마지막_성공_갱신_뒤_지난_초다() {
    when(apiKeySnapshotRepository.findUsableAt(any()))
        .thenReturn(List.of(snapshotOf(SecureTokens.generate("me_live_"))))
        .thenThrow(new DataAccessResourceFailureException("connection refused"));
    clock.fix(NOW);
    apiKeyTableLoader.refresh();
    clock.fix(NOW.plusSeconds(45));
    apiKeyTableLoader.refreshPeriodically();

    assertThat(meterRegistry.get(ApiKeyTableLoader.SIZE_GAUGE_NAME).gauge().value()).isEqualTo(1);
    assertThat(meterRegistry.get(ApiKeyTableLoader.REFRESH_AGE_GAUGE_NAME).gauge().value())
        .isEqualTo(45);
  }

  @Test
  void 다시_읽을_때_활성_도입사_목록도_함께_넣는다() {
    ApiKeySnapshot activeKey = snapshotOf(SecureTokens.generate("me_live_"));
    ApiKeySnapshot suspendedKey = snapshotOf(SecureTokens.generate("me_live_"));
    when(apiKeySnapshotRepository.findUsableAt(any())).thenReturn(List.of(activeKey, suspendedKey));
    when(activeOrganizations.list()).thenReturn(Set.of(activeKey.organizationId()));

    apiKeyTableLoader.refresh();

    assertThat(apiKeyTable.isOrganizationActive(activeKey.organizationId())).isTrue();
    assertThat(apiKeyTable.isOrganizationActive(suspendedKey.organizationId())).isFalse();
  }

  private static ApiKeySnapshot snapshotOf(String rawKey) {
    return new ApiKeySnapshot(
        ApiKeyHash.of(rawKey),
        UUID.randomUUID(),
        UUID.randomUUID(),
        null,
        "live",
        Set.of("read"),
        null,
        null,
        null);
  }
}
