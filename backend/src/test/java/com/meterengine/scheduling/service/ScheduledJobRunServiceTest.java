package com.meterengine.scheduling.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meterengine.scheduling.dto.ScheduledJobResult;
import com.meterengine.scheduling.entity.ScheduledJobRun;
import com.meterengine.scheduling.repository.ScheduledJobRunRepository;
import io.micrometer.core.instrument.search.Search;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScheduledJobRunServiceTest {

  private static final String JOB_NAME = "invoice_finalization";
  private static final OffsetDateTime SCHEDULED_AT = OffsetDateTime.parse("2026-11-01T00:00:00Z");

  @Mock private ScheduledJobRunRepository scheduledJobRunRepository;

  private SimpleMeterRegistry meterRegistry;
  private ScheduledJobRunService scheduledJobRunService;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    scheduledJobRunService = new ScheduledJobRunService(scheduledJobRunRepository, meterRegistry);
  }

  @Test
  void 성공하면_succeeded와_세_카운트와_끝난_시각을_남긴다() {
    claimSlot();

    scheduledJobRunService.run(JOB_NAME, SCHEDULED_AT, () -> new ScheduledJobResult(3, 1, 2));

    ScheduledJobRun saved = savedRun();
    assertThat(saved.getStatus()).isEqualTo(ScheduledJobRun.SUCCEEDED);
    assertThat(saved.getProcessedCount()).isEqualTo(3);
    assertThat(saved.getSkippedCount()).isEqualTo(1);
    assertThat(saved.getFailedCount()).isEqualTo(2);
    assertThat(saved.getErrorSummary()).isNull();
    assertThat(saved.getFinishedAt()).isNotNull();
  }

  @Test
  void 성공하면_succeeded_건수를_올리고_마지막_성공_시각을_기록한다() {
    claimSlot();

    scheduledJobRunService.run(JOB_NAME, SCHEDULED_AT, () -> new ScheduledJobResult(0, 0, 0));

    assertThat(runCounter("succeeded").counter().count()).isEqualTo(1.0);
    assertThat(runCounter("failed").counter()).isNull();
    assertThat(
            meterRegistry
                .get(ScheduledJobRunService.LAST_SUCCESS_GAUGE_NAME)
                .tag(ScheduledJobRunService.JOB_TAG, JOB_NAME)
                .gauge()
                .value())
        .isCloseTo(OffsetDateTime.now().toEpochSecond(), within(5.0));
  }

  @Test
  void 성공하면_결과_건수만큼_items_카운터를_올린다() {
    claimSlot();

    scheduledJobRunService.run(JOB_NAME, SCHEDULED_AT, () -> new ScheduledJobResult(3, 1, 2));

    assertThat(itemsCounter(ScheduledJobRunService.PROCESSED).counter().count()).isEqualTo(3.0);
    assertThat(itemsCounter(ScheduledJobRunService.SKIPPED).counter().count()).isEqualTo(1.0);
    assertThat(itemsCounter(ScheduledJobRunService.FAILED).counter().count()).isEqualTo(2.0);
  }

  @Test
  void 실패하면_items_카운터를_올리지_않는다() {
    claimSlot();

    assertThatThrownBy(
        () ->
            scheduledJobRunService.run(
                JOB_NAME,
                SCHEDULED_AT,
                () -> {
                  throw new IllegalStateException("DB 연결 끊김");
                }));

    assertThat(meterRegistry.find(ScheduledJobRunService.ITEMS_COUNTER_NAME).counter()).isNull();
  }

  @Test
  void 실패하면_failed와_오류_요약을_남기고_예외를_다시_던진다() {
    claimSlot();
    IllegalStateException failure = new IllegalStateException("DB 연결 끊김");

    assertThatThrownBy(
            () ->
                scheduledJobRunService.run(
                    JOB_NAME,
                    SCHEDULED_AT,
                    () -> {
                      throw failure;
                    }))
        .isSameAs(failure);

    ScheduledJobRun saved = savedRun();
    assertThat(saved.getStatus()).isEqualTo(ScheduledJobRun.FAILED);
    assertThat(saved.getErrorSummary()).isEqualTo("java.lang.IllegalStateException: DB 연결 끊김");
    assertThat(saved.getFinishedAt()).isNotNull();
    assertThat(runCounter("failed").counter().count()).isEqualTo(1.0);
    assertThat(runCounter("succeeded").counter()).isNull();
  }

  @Test
  void 오류_요약은_2000자에서_자른다() {
    claimSlot();

    assertThatThrownBy(
        () ->
            scheduledJobRunService.run(
                JOB_NAME,
                SCHEDULED_AT,
                () -> {
                  throw new IllegalStateException("가".repeat(3000));
                }));

    assertThat(savedRun().getErrorSummary()).hasSize(2000);
  }

  @Test
  void 다른_인스턴스가_슬롯을_잡았으면_작업을_실행하지_않고_기록도_남기지_않는다() {
    when(scheduledJobRunRepository.insertIfAbsent(any(), eq(JOB_NAME), eq(SCHEDULED_AT), any()))
        .thenReturn(0);
    AtomicInteger executions = new AtomicInteger();

    scheduledJobRunService.run(
        JOB_NAME,
        SCHEDULED_AT,
        () -> {
          executions.incrementAndGet();
          return new ScheduledJobResult(1, 0, 0);
        });

    assertThat(executions).hasValue(0);
    verify(scheduledJobRunRepository, never()).findById(any());
    verify(scheduledJobRunRepository, never()).save(any());
    assertThat(meterRegistry.find(ScheduledJobRunService.RUN_COUNTER_NAME).counter()).isNull();
  }

  @Test
  void 등록하면_DB의_마지막_성공_시각으로_게이지를_낸다() {
    OffsetDateTime lastSucceededAt = OffsetDateTime.parse("2026-10-31T23:59:30Z");
    when(scheduledJobRunRepository.findLastSucceededFinishedAt(JOB_NAME))
        .thenReturn(Optional.of(lastSucceededAt));

    scheduledJobRunService.register(JOB_NAME);

    assertThat(lastSuccessGauge().gauge().value())
        .isEqualTo((double) lastSucceededAt.toEpochSecond());
  }

  @Test
  void 성공한_적이_없는_작업은_등록해도_게이지를_내지_않는다() {
    when(scheduledJobRunRepository.findLastSucceededFinishedAt(JOB_NAME))
        .thenReturn(Optional.empty());

    scheduledJobRunService.register(JOB_NAME);

    assertThat(lastSuccessGauge().gauge()).isNull();
  }

  @Test
  void 실행으로_기록한_성공_시각을_더_이른_DB_시각으로_되돌리지_않는다() {
    claimSlot();
    scheduledJobRunService.run(JOB_NAME, SCHEDULED_AT, () -> new ScheduledJobResult(0, 0, 0));
    double recordedByRun = lastSuccessGauge().gauge().value();
    when(scheduledJobRunRepository.findLastSucceededFinishedAt(JOB_NAME))
        .thenReturn(Optional.of(OffsetDateTime.parse("2026-01-01T00:00:00Z")));

    scheduledJobRunService.register(JOB_NAME);

    assertThat(lastSuccessGauge().gauge().value()).isEqualTo(recordedByRun);
  }

  private void claimSlot() {
    ArgumentCaptor<UUID> idCaptor = ArgumentCaptor.forClass(UUID.class);
    when(scheduledJobRunRepository.insertIfAbsent(
            idCaptor.capture(), eq(JOB_NAME), eq(SCHEDULED_AT), any()))
        .thenReturn(1);
    when(scheduledJobRunRepository.findById(any()))
        .thenAnswer(
            invocation ->
                Optional.of(
                    ScheduledJobRun.running(
                        idCaptor.getValue(), JOB_NAME, SCHEDULED_AT, OffsetDateTime.now())));
  }

  private ScheduledJobRun savedRun() {
    ArgumentCaptor<ScheduledJobRun> captor = ArgumentCaptor.forClass(ScheduledJobRun.class);
    verify(scheduledJobRunRepository).save(captor.capture());
    verify(scheduledJobRunRepository).findById(captor.getValue().getId());
    return captor.getValue();
  }

  private Search lastSuccessGauge() {
    return meterRegistry
        .find(ScheduledJobRunService.LAST_SUCCESS_GAUGE_NAME)
        .tag(ScheduledJobRunService.JOB_TAG, JOB_NAME);
  }

  private Search itemsCounter(String result) {
    return meterRegistry
        .find(ScheduledJobRunService.ITEMS_COUNTER_NAME)
        .tag(ScheduledJobRunService.JOB_TAG, JOB_NAME)
        .tag(ScheduledJobRunService.RESULT_TAG, result);
  }

  private Search runCounter(String result) {
    return meterRegistry
        .find(ScheduledJobRunService.RUN_COUNTER_NAME)
        .tag(ScheduledJobRunService.JOB_TAG, JOB_NAME)
        .tag(ScheduledJobRunService.RESULT_TAG, result);
  }
}
