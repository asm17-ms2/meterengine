package com.meterengine.global.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class DatabaseOperationTimerTest {

  private static final String TIMER_NAME = "meterengine.db.operation";
  private static final long WORK_BEFORE_COMMIT_MILLIS = 100;

  private SimpleMeterRegistry meterRegistry;
  private DatabaseOperationTimer databaseOperationTimer;
  private TransactionTemplate transactionTemplate;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    databaseOperationTimer = new DatabaseOperationTimer(meterRegistry);
    transactionTemplate = new TransactionTemplate(new InMemoryTransactionManager());
  }

  @Test
  void select_결과를_돌려주고_operation과_table_태그로_잰다() {
    String result = databaseOperationTimer.timeSelect("api_key", () -> "found");

    assertThat(result).isEqualTo("found");
    assertThat(timer("select", "api_key").count()).isEqualTo(1);
  }

  @Test
  void 값을_돌려주지_않는_select도_잰다() {
    databaseOperationTimer.timeSelect("customer", () -> {});

    assertThat(timer("select", "customer").count()).isEqualTo(1);
  }

  @Test
  void 예외로_끝난_select도_잰다() {
    assertThatThrownBy(
            () ->
                databaseOperationTimer.timeSelect(
                    "customer",
                    () -> {
                      throw new IllegalStateException("없는 고객");
                    }))
        .isInstanceOf(IllegalStateException.class);

    assertThat(timer("select", "customer").count()).isEqualTo(1);
  }

  @Test
  void insert_결과를_돌려주고_operation과_table_태그로_잰다() {
    int inserted = databaseOperationTimer.timeInsert("event", () -> 1);

    assertThat(inserted).isEqualTo(1);
    assertThat(timer("insert", "event").count()).isEqualTo(1);
  }

  @Test
  void 커밋한_트랜잭션의_커밋을_잰다() {
    transactionTemplate.executeWithoutResult(status -> databaseOperationTimer.timeCommit("event"));

    assertThat(timer("commit", "event").count()).isEqualTo(1);
  }

  @Test
  void 커밋_전에_한_일은_커밋_시간에_넣지_않는다() {
    transactionTemplate.executeWithoutResult(
        status -> {
          databaseOperationTimer.timeCommit("event");
          pause(WORK_BEFORE_COMMIT_MILLIS);
        });

    assertThat(timer("commit", "event").totalTime(TimeUnit.MILLISECONDS))
        .isLessThan(WORK_BEFORE_COMMIT_MILLIS);
  }

  @Test
  void 커밋이_실패한_트랜잭션은_커밋을_재지_않는다() {
    TransactionTemplate failingCommit =
        new TransactionTemplate(new FailingCommitTransactionManager());

    assertThatThrownBy(
            () ->
                failingCommit.executeWithoutResult(
                    status -> databaseOperationTimer.timeCommit("event")))
        .isInstanceOf(TransactionSystemException.class);

    assertThat(meterRegistry.find(TIMER_NAME).timers()).isEmpty();
  }

  @Test
  void 태그는_operation과_table_둘뿐이다() {
    databaseOperationTimer.timeSelect("api_key", () -> "found");

    assertThat(timer("select", "api_key").getId().getTags()).hasSize(2);
  }

  @Test
  void 롤백한_트랜잭션은_커밋을_재지_않는다() {
    assertThatThrownBy(
            () ->
                transactionTemplate.executeWithoutResult(
                    status -> {
                      databaseOperationTimer.timeCommit("event");
                      throw new IllegalStateException("거절");
                    }))
        .isInstanceOf(IllegalStateException.class);

    assertThat(meterRegistry.find(TIMER_NAME).timers()).isEmpty();
  }

  @Test
  void 트랜잭션_밖에서는_커밋을_재지_않는다() {
    databaseOperationTimer.timeCommit("event");

    assertThat(meterRegistry.find(TIMER_NAME).timers()).isEmpty();
  }

  private static void pause(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(exception);
    }
  }

  private Timer timer(String operation, String table) {
    return meterRegistry.get(TIMER_NAME).tag("operation", operation).tag("table", table).timer();
  }

  // 트랜잭션 동기화 순서만 실제와 같게 돌리고 DB에는 가지 않는다.
  private static class InMemoryTransactionManager extends AbstractPlatformTransactionManager {
    @Override
    protected Object doGetTransaction() {
      return new Object();
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {}

    @Override
    protected void doCommit(DefaultTransactionStatus status) {}

    @Override
    protected void doRollback(DefaultTransactionStatus status) {}
  }

  private static final class FailingCommitTransactionManager extends InMemoryTransactionManager {
    @Override
    protected void doCommit(DefaultTransactionStatus status) {
      throw new TransactionSystemException("커밋 실패");
    }
  }
}
