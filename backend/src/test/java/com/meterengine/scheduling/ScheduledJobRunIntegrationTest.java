package com.meterengine.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meterengine.TestcontainersConfiguration;
import com.meterengine.scheduling.config.SchedulingConfig;
import com.meterengine.scheduling.dto.ScheduledJobResult;
import com.meterengine.scheduling.entity.ScheduledJobRun;
import com.meterengine.scheduling.repository.ScheduledJobRunRepository;
import com.meterengine.scheduling.service.ScheduledJobRunService;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ScheduledJobRunIntegrationTest {

  private static final String JOB_NAME_PREFIX = "scheduling_test_";
  private static final String LAST_SUCCESS_GAUGE_NAME = "meterengine.scheduled.job.last.success";
  private static final String JOB_TAG = "job_name";
  private static final OffsetDateTime SCHEDULED_AT = OffsetDateTime.parse("2026-11-01T00:00:00Z");

  @Autowired private ScheduledJobRunService scheduledJobRunService;
  @Autowired private ScheduledJobRunRepository scheduledJobRunRepository;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private ApplicationContext applicationContext;
  @Autowired private MeterRegistry meterRegistry;

  @AfterEach
  void cleanUp() {
    jdbcTemplate.update(
        "DELETE FROM scheduled_job_run WHERE job_name LIKE ?", JOB_NAME_PREFIX + "%");
  }

  @Test
  void 같은_슬롯을_두_스레드가_동시에_돌리면_한_번만_실행되고_기록은_하나다() throws Exception {
    String jobName = newJobName();
    AtomicInteger executions = new AtomicInteger();
    CountDownLatch start = new CountDownLatch(1);

    List<CompletableFuture<Void>> runs =
        List.of(
            runAfter(start, jobName, SCHEDULED_AT, executions),
            runAfter(start, jobName, SCHEDULED_AT, executions));
    start.countDown();
    CompletableFuture.allOf(runs.toArray(CompletableFuture[]::new)).get(10, TimeUnit.SECONDS);

    assertThat(executions).hasValue(1);
    assertThat(statusesOf(jobName)).containsExactly(ScheduledJobRun.SUCCEEDED);
  }

  @Test
  void 슬롯이_다르면_둘_다_실행된다() throws Exception {
    String jobName = newJobName();
    AtomicInteger executions = new AtomicInteger();
    CountDownLatch start = new CountDownLatch(1);

    List<CompletableFuture<Void>> runs =
        List.of(
            runAfter(start, jobName, SCHEDULED_AT, executions),
            runAfter(start, jobName, SCHEDULED_AT.plusMinutes(1), executions));
    start.countDown();
    CompletableFuture.allOf(runs.toArray(CompletableFuture[]::new)).get(10, TimeUnit.SECONDS);

    assertThat(executions).hasValue(2);
    assertThat(statusesOf(jobName))
        .containsExactly(ScheduledJobRun.SUCCEEDED, ScheduledJobRun.SUCCEEDED);
  }

  @Test
  void 실행_중인_기록은_running이고_끝난_시각이_비어_있다() {
    String jobName = newJobName();
    AtomicInteger observed = new AtomicInteger();

    scheduledJobRunService.run(
        jobName,
        SCHEDULED_AT,
        () -> {
          Map<String, Object> row =
              jdbcTemplate.queryForMap(
                  "SELECT status, finished_at FROM scheduled_job_run WHERE job_name = ?", jobName);
          assertThat(row.get("status")).isEqualTo(ScheduledJobRun.RUNNING);
          assertThat(row.get("finished_at")).isNull();
          observed.incrementAndGet();
          return new ScheduledJobResult(0, 0, 0);
        });

    assertThat(observed).hasValue(1);
  }

  @Test
  void 성공한_실행은_세_카운트와_끝난_시각을_남긴다() {
    String jobName = newJobName();

    scheduledJobRunService.run(jobName, SCHEDULED_AT, () -> new ScheduledJobResult(5, 2, 1));

    ScheduledJobRun run = onlyRunOf(jobName);
    assertThat(run.getStatus()).isEqualTo(ScheduledJobRun.SUCCEEDED);
    assertThat(run.getScheduledAt()).isAtSameInstantAs(SCHEDULED_AT);
    assertThat(run.getProcessedCount()).isEqualTo(5);
    assertThat(run.getSkippedCount()).isEqualTo(2);
    assertThat(run.getFailedCount()).isEqualTo(1);
    assertThat(run.getErrorSummary()).isNull();
    assertThat(run.getFinishedAt()).isAfterOrEqualTo(run.getStartedAt());
  }

  @Test
  void 실패한_실행은_failed와_오류_요약을_남긴다() {
    String jobName = newJobName();

    assertThatThrownBy(
            () ->
                scheduledJobRunService.run(
                    jobName,
                    SCHEDULED_AT,
                    () -> {
                      throw new IllegalStateException("메일 서버 응답 없음");
                    }))
        .isInstanceOf(IllegalStateException.class);

    ScheduledJobRun run = onlyRunOf(jobName);
    assertThat(run.getStatus()).isEqualTo(ScheduledJobRun.FAILED);
    assertThat(run.getErrorSummary()).isEqualTo("java.lang.IllegalStateException: 메일 서버 응답 없음");
    assertThat(run.getFinishedAt()).isNotNull();
  }

  @Test
  void 등록하면_실패한_실행은_빼고_가장_늦은_성공_시각으로_게이지를_낸다() {
    String jobName = newJobName();
    OffsetDateTime lastSucceededAt = OffsetDateTime.parse("2026-10-31T00:00:10Z");
    insertRun(jobName, SCHEDULED_AT, "succeeded", lastSucceededAt.minusDays(1));
    insertRun(jobName, SCHEDULED_AT.plusDays(1), "succeeded", lastSucceededAt);
    insertRun(jobName, SCHEDULED_AT.plusDays(2), "failed", lastSucceededAt.plusDays(1));

    scheduledJobRunService.register(jobName);

    assertThat(meterRegistry.get(LAST_SUCCESS_GAUGE_NAME).tag(JOB_TAG, jobName).gauge().value())
        .isEqualTo((double) lastSucceededAt.toEpochSecond());
  }

  @Test
  void 성공한_실행이_없으면_마지막_성공_시각이_없다() {
    String jobName = newJobName();
    insertRun(jobName, SCHEDULED_AT, "failed", OffsetDateTime.now());

    assertThat(scheduledJobRunRepository.findLastSucceededFinishedAt(jobName)).isEmpty();
  }

  @Test
  void 정한_상태가_아닌_실행_기록은_저장되지_않는다() {
    assertThatThrownBy(() -> insertRun(newJobName(), "cancelled", OffsetDateTime.now()))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("scheduled_job_run_status_check");
  }

  @Test
  void running이면서_끝난_시각이_있는_실행_기록은_저장되지_않는다() {
    assertThatThrownBy(() -> insertRun(newJobName(), "running", OffsetDateTime.now()))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("scheduled_job_run_status_finished_at_check");
  }

  @Test
  void 끝난_실행_기록에_끝난_시각이_없으면_저장되지_않는다() {
    assertThatThrownBy(() -> insertRun(newJobName(), "succeeded", null))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("scheduled_job_run_status_finished_at_check");
  }

  @Test
  void 같은_작업의_같은_슬롯은_두_번_저장되지_않는다() {
    String jobName = newJobName();
    insertRun(jobName, "running", null);

    assertThatThrownBy(() -> insertRun(jobName, "running", null))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("scheduled_job_run_job_name_scheduled_at_unique");
  }

  @Test
  void 작업별_최근_실행을_찾는_인덱스를_가진다() {
    String indexDefinition =
        jdbcTemplate.queryForObject(
            "SELECT indexdef FROM pg_indexes WHERE indexname = 'scheduled_job_run_job_name_started_at'",
            String.class);

    assertThat(indexDefinition)
        .contains("scheduled_job_run")
        .contains("(job_name, started_at DESC)");
  }

  @Test
  void 테스트에서는_예약_작업이_저절로_돌지_않는다() {
    assertThat(applicationContext.getBeanNamesForType(SchedulingConfig.class)).isEmpty();
  }

  private CompletableFuture<Void> runAfter(
      CountDownLatch start, String jobName, OffsetDateTime scheduledAt, AtomicInteger executions) {
    return CompletableFuture.runAsync(
        () -> {
          awaitQuietly(start);
          scheduledJobRunService.run(
              jobName,
              scheduledAt,
              () -> {
                executions.incrementAndGet();
                sleepQuietly();
                return new ScheduledJobResult(1, 0, 0);
              });
        });
  }

  private static void awaitQuietly(CountDownLatch latch) {
    try {
      latch.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  private static void sleepQuietly() {
    try {
      Thread.sleep(200);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  private void insertRun(String jobName, String status, OffsetDateTime finishedAt) {
    insertRun(jobName, SCHEDULED_AT, status, finishedAt);
  }

  private void insertRun(
      String jobName, OffsetDateTime scheduledAt, String status, OffsetDateTime finishedAt) {
    jdbcTemplate.update(
        """
        INSERT INTO scheduled_job_run (id, job_name, scheduled_at, status, started_at, finished_at)
        VALUES (?, ?, ?, ?, now(), ?)
        """,
        UUID.randomUUID(),
        jobName,
        scheduledAt,
        status,
        finishedAt);
  }

  private List<String> statusesOf(String jobName) {
    return jdbcTemplate.queryForList(
        "SELECT status FROM scheduled_job_run WHERE job_name = ?", String.class, jobName);
  }

  private ScheduledJobRun onlyRunOf(String jobName) {
    List<ScheduledJobRun> runs =
        scheduledJobRunRepository.findAll().stream()
            .filter(run -> run.getJobName().equals(jobName))
            .toList();
    assertThat(runs).hasSize(1);
    return runs.getFirst();
  }

  private static String newJobName() {
    return JOB_NAME_PREFIX + UUID.randomUUID();
  }
}
