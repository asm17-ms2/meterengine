package com.meterengine.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meterengine.MeteringEnvironmentTestSupport;
import com.meterengine.global.database.*;
import com.meterengine.payment.client.TossPaymentsClient;
import com.meterengine.scheduling.dto.ScheduledJobResult;
import com.meterengine.scheduling.service.ScheduledJobRunService;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(
    classes = SideEffectEnvironmentTestConfig.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.docker.compose.enabled=false",
      "meterengine.scheduling.enabled=false",
      "meterengine.webhook.enabled=false",
      "METERENGINE_DEPLOYMENT_ENVIRONMENT=test",
      "METERENGINE_CUTOVER_MODE=active",
      "crypto.encryption-key=AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE="
    })
class SchemaScheduledJobIntegrationTest extends MeteringEnvironmentTestSupport {
  @MockitoBean TossPaymentsClient toss;
  @Autowired ScheduledJobRunService jobs;
  @Autowired io.micrometer.core.instrument.MeterRegistry meterRegistry;

  @Test
  void 같은_슬롯은_환경별로_한번만_실행하고_재시작에도_중복하지_않는다() {
    String name = "fixture_" + java.util.UUID.randomUUID();
    var slot = OffsetDateTime.now(java.time.ZoneOffset.UTC).withNano(0);
    var calls = new AtomicInteger();
    jobs.run(
        name,
        slot,
        () -> {
          calls.incrementAndGet();
          assertThat(DatabaseScopes.current()).isNotEqualTo(DatabaseScope.SHARED);
          return new ScheduledJobResult(1, 2, 0);
        });
    var restarted =
        context.getAutowireCapableBeanFactory().createBean(ScheduledJobRunService.class);
    restarted.run(
        name,
        slot,
        () -> {
          throw new AssertionError("duplicate slot");
        });
    assertThat(calls).hasValue(2);
    assertThat(
            ownerJdbc()
                .queryForObject(
                    "SELECT count(*) FROM shared.scheduled_job_run WHERE job_name=? AND status='succeeded'",
                    Integer.class,
                    name))
        .isEqualTo(2);
    for (var scope : java.util.List.of(DatabaseScope.TEST, DatabaseScope.LIVE)) {
      assertThat(
              meterRegistry
                  .get("meterengine.scheduled.job.last.success")
                  .tags("job_name", name, "scope", scope.name().toLowerCase(java.util.Locale.ROOT))
                  .gauge()
                  .value())
          .isPositive();
      DatabaseScopes.call(
          scope,
          () -> {
            assertThat(jobs.watermark(name, slot)).isEmpty();
            assertThat(jobs.watermark(name, slot.plusMinutes(1))).contains(slot);
            assertThat(jobs.earliest(name, slot.plusMinutes(1))).contains(slot);
            return null;
          });
    }
  }

  @Test
  void 같은_scope의_두_서비스는_하나만_실행하고_중복슬롯도_후처리를_수행한다() throws Exception {
    String name = "fixture_" + java.util.UUID.randomUUID();
    var slot = OffsetDateTime.now(java.time.ZoneOffset.UTC).withNano(0);
    var executions = new AtomicInteger();
    var after = new AtomicInteger();
    var entered = new java.util.concurrent.CountDownLatch(1);
    var release = new java.util.concurrent.CountDownLatch(1);
    var second = context.getAutowireCapableBeanFactory().createBean(ScheduledJobRunService.class);
    try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      var first =
          executor.submit(
              () ->
                  DatabaseScopes.call(
                      DatabaseScope.TEST,
                      () -> {
                        jobs.run(
                            name,
                            slot,
                            () -> {
                              executions.incrementAndGet();
                              entered.countDown();
                              try {
                                if (!release.await(10, java.util.concurrent.TimeUnit.SECONDS))
                                  throw new IllegalStateException("slot timeout");
                              } catch (InterruptedException exception) {
                                Thread.currentThread().interrupt();
                                throw new IllegalStateException(exception);
                              }
                              return new ScheduledJobResult(1, 0, 0);
                            },
                            after::incrementAndGet);
                        return null;
                      }));
      assertThat(entered.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
      try {
        executor
            .submit(
                () ->
                    DatabaseScopes.call(
                        DatabaseScope.TEST,
                        () -> {
                          second.run(
                              name,
                              slot,
                              () -> {
                                throw new AssertionError("duplicate execution");
                              },
                              after::incrementAndGet);
                          return null;
                        }))
            .get(10, java.util.concurrent.TimeUnit.SECONDS);
      } finally {
        release.countDown();
      }
      first.get(10, java.util.concurrent.TimeUnit.SECONDS);
    }
    assertThat(executions).hasValue(1);
    assertThat(after).hasValue(2);
    assertThat(
            ownerJdbc()
                .queryForObject(
                    "SELECT count(*) FROM shared.scheduled_job_run WHERE job_name=? AND scope='test' AND status='succeeded'",
                    Integer.class,
                    name))
        .isEqualTo(1);
  }

  @Test
  void 두_인스턴스의_같은_슬롯은_각_scope에서_한번만_선점한다() throws Exception {
    String name = "fixture_" + java.util.UUID.randomUUID();
    var slot = OffsetDateTime.now(java.time.ZoneOffset.UTC).withNano(0);
    var calls = new AtomicInteger();
    var started = new java.util.concurrent.CountDownLatch(2);
    var release = new java.util.concurrent.CountDownLatch(1);
    java.util.function.Supplier<ScheduledJobResult> operation =
        () -> {
          calls.incrementAndGet();
          started.countDown();
          try {
            if (!release.await(10, java.util.concurrent.TimeUnit.SECONDS))
              throw new IllegalStateException("job fixture timeout");
          } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
          }
          return new ScheduledJobResult(1, 0, 0);
        };
    try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      var one = executor.submit(() -> jobs.run(name, slot, operation));
      var two = executor.submit(() -> jobs.run(name, slot, operation));
      try {
        assertThat(started.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
      } finally {
        release.countDown();
      }
      one.get(10, java.util.concurrent.TimeUnit.SECONDS);
      two.get(10, java.util.concurrent.TimeUnit.SECONDS);
    }
    assertThat(calls).hasValue(2);
    assertThat(
            ownerJdbc()
                .queryForObject(
                    "SELECT count(*) FROM shared.scheduled_job_run WHERE job_name=? AND status='succeeded'",
                    Integer.class,
                    name))
        .isEqualTo(2);
    assertThat(DatabaseScopes.current()).isEqualTo(DatabaseScope.SHARED);
  }

  @Test
  void 한쪽_실패와_불완전_성공은_다른_환경의_실행과_깨끗한_watermark를_막지_않는다() {
    String name = "fixture_" + java.util.UUID.randomUUID();
    var slot = OffsetDateTime.now(java.time.ZoneOffset.UTC).withNano(0);
    assertThatThrownBy(
            () ->
                jobs.run(
                    name,
                    slot,
                    () -> {
                      if (DatabaseScopes.current() == DatabaseScope.TEST)
                        throw new IllegalStateException("test failure");
                      return new ScheduledJobResult(1, 0, 0);
                    }))
        .isInstanceOf(IllegalStateException.class);
    assertThat(DatabaseScopes.current()).isEqualTo(DatabaseScope.SHARED);
    DatabaseScopes.call(
        DatabaseScope.TEST,
        () -> {
          assertThat(jobs.watermark(name, slot.plusMinutes(1))).isEmpty();
          return null;
        });
    DatabaseScopes.call(
        DatabaseScope.LIVE,
        () -> {
          assertThat(jobs.watermark(name, slot.plusMinutes(1))).contains(slot);
          return null;
        });
    jobs.run(name, slot.plusMinutes(1), () -> new ScheduledJobResult(0, 0, 1));
    DatabaseScopes.call(
        DatabaseScope.LIVE,
        () -> {
          assertThat(jobs.watermark(name, slot.plusMinutes(2))).contains(slot);
          return null;
        });
    assertThat(
            ownerJdbc()
                .queryForObject(
                    "SELECT count(*) FROM shared.scheduled_job_run WHERE job_name=?",
                    Integer.class,
                    name))
        .isEqualTo(4);
  }
}
