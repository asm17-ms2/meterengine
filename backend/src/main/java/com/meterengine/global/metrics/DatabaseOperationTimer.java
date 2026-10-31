package com.meterengine.global.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class DatabaseOperationTimer {

  private static final String TIMER_NAME = "meterengine.db.operation";
  private static final String OPERATION_TAG = "operation";
  private static final String TABLE_TAG = "table";

  private static final String SELECT = "select";
  private static final String INSERT = "insert";
  private static final String COMMIT = "commit";
  // 같은 네트워크 안 PostgreSQL 왕복은 1밀리초 아래로 떨어지므로 기본 하한 1밀리초보다 낮춘다.
  private static final Duration MINIMUM_EXPECTED_DURATION = Duration.of(100, ChronoUnit.MICROS);

  private final MeterRegistry meterRegistry;
  private final Map<TimerTags, Timer> timersByTags = new ConcurrentHashMap<>();

  public DatabaseOperationTimer(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  public <T> T timeSelect(String table, Supplier<T> select) {
    return timer(SELECT, table).record(select);
  }

  public void timeSelect(String table, Runnable select) {
    timer(SELECT, table).record(select);
  }

  public <T> T timeInsert(String table, Supplier<T> insert) {
    return timer(INSERT, table).record(insert);
  }

  // 지금 트랜잭션이 실제로 커밋할 때만 커밋 시간을 남기고, 롤백하면 남기지 않는다.
  public void timeCommit(String table) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()
        || !TransactionSynchronizationManager.isActualTransactionActive()) {
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(new CommitTiming(table));
  }

  private Timer timer(String operation, String table) {
    return timersByTags.computeIfAbsent(new TimerTags(operation, table), this::registerTimer);
  }

  private Timer registerTimer(TimerTags tags) {
    return Timer.builder(TIMER_NAME)
        .description("요청 하나가 DB 왕복 한 번에 쓴 시간. operation은 SQL 종류, table은 대상 테이블이다")
        .publishPercentileHistogram()
        .minimumExpectedValue(MINIMUM_EXPECTED_DURATION)
        .tag(OPERATION_TAG, tags.operation())
        .tag(TABLE_TAG, tags.table())
        .register(meterRegistry);
  }

  private record TimerTags(String operation, String table) {}

  private final class CommitTiming implements TransactionSynchronization {
    private final String table;
    private Timer.Sample sample;

    private CommitTiming(String table) {
      this.table = table;
    }

    @Override
    public void beforeCommit(boolean readOnly) {
      sample = Timer.start(meterRegistry);
    }

    @Override
    public void afterCommit() {
      sample.stop(timer(COMMIT, table));
    }
  }
}
