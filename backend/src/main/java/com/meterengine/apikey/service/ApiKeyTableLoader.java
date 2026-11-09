package com.meterengine.apikey.service;

import com.meterengine.apikey.config.ApiKeyProperties;
import com.meterengine.apikey.repository.ApiKeySnapshotRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
// 순서를 주지 않은 다른 러너(수집 예열 등)보다 먼저 키 테이블을 채운다.
@Order(Ordered.LOWEST_PRECEDENCE - 1)
public class ApiKeyTableLoader implements ApplicationRunner {

  static final String SIZE_GAUGE_NAME = "meterengine.api.key.table.size";
  static final String REFRESH_AGE_GAUGE_NAME = "meterengine.api.key.table.refresh.age";

  private static final Logger logger = LoggerFactory.getLogger(ApiKeyTableLoader.class);

  private static final double MILLIS_PER_SECOND = 1000.0;

  private final ApiKeyTable apiKeyTable;
  private final ApiKeySnapshotRepository apiKeySnapshotRepository;
  private final ApiKeyProperties apiKeyProperties;
  private final Clock clock;

  // 결제와 웹훅 예약 작업을 끄는 스위치와 무관하게 서버마다 인증용 테이블을 계속 다시 읽는다.
  private final ScheduledExecutorService refreshExecutor =
      Executors.newSingleThreadScheduledExecutor(
          Thread.ofPlatform().name("api-key-table-refresh").daemon().factory());

  private volatile Instant lastRefreshSucceededAt;

  public ApiKeyTableLoader(
      ApiKeyTable apiKeyTable,
      ApiKeySnapshotRepository apiKeySnapshotRepository,
      ApiKeyProperties apiKeyProperties,
      Clock clock,
      MeterRegistry meterRegistry) {
    this.apiKeyTable = apiKeyTable;
    this.apiKeySnapshotRepository = apiKeySnapshotRepository;
    this.apiKeyProperties = apiKeyProperties;
    this.clock = clock;
    Gauge.builder(SIZE_GAUGE_NAME, apiKeyTable, ApiKeyTable::size)
        .description("서버 메모리의 API 키 테이블에 든 키 수")
        .register(meterRegistry);
    Gauge.builder(REFRESH_AGE_GAUGE_NAME, this, ApiKeyTableLoader::secondsSinceLastRefresh)
        .description("API 키 테이블을 DB에서 마지막으로 다시 읽는 데 성공한 뒤 지난 시간")
        .baseUnit("seconds")
        .register(meterRegistry);
  }

  @Override
  public void run(ApplicationArguments args) throws InterruptedException {
    loadUntilSucceeded();
    schedulePeriodicRefresh();
  }

  // readiness는 러너가 끝나야 켜지므로, 테이블을 다 채울 때까지 DB를 다시 읽으며 기다린다.
  private void loadUntilSucceeded() throws InterruptedException {
    while (true) {
      try {
        refresh();
        return;
      } catch (RuntimeException exception) {
        logger.warn(
            "API key table load failed, retrying in {}",
            apiKeyProperties.tableLoadRetryInterval(),
            exception);
        Thread.sleep(apiKeyProperties.tableLoadRetryInterval());
      }
    }
  }

  private void schedulePeriodicRefresh() {
    long intervalMillis = apiKeyProperties.tableRefreshInterval().toMillis();
    refreshExecutor.scheduleWithFixedDelay(
        this::refreshPeriodically, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
  }

  @PreDestroy
  public void stopRefreshing() {
    refreshExecutor.shutdownNow();
  }

  public void refreshPeriodically() {
    try {
      refresh();
    } catch (RuntimeException exception) {
      logger.warn("API key table refresh failed, keeping the previous table", exception);
    }
  }

  public void refresh() {
    apiKeyTable.replaceAll(this::findUsableKeys);
    lastRefreshSucceededAt = clock.instant();
  }

  private List<ApiKeySnapshot> findUsableKeys() {
    return apiKeySnapshotRepository.findUsableAt(OffsetDateTime.now(clock));
  }

  private double secondsSinceLastRefresh() {
    Instant succeededAt = lastRefreshSucceededAt;
    if (succeededAt == null) {
      return Double.NaN;
    }
    return Duration.between(succeededAt, clock.instant()).toMillis() / MILLIS_PER_SECOND;
  }
}
