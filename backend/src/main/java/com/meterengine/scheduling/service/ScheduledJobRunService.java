package com.meterengine.scheduling.service;

import com.meterengine.scheduling.dto.ScheduledJobResult;
import com.meterengine.scheduling.entity.ScheduledJobRun;
import com.meterengine.scheduling.repository.ScheduledJobRunRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

@Service
public class ScheduledJobRunService {

  static final String RUN_COUNTER_NAME = "meterengine.scheduled.job.run";
  static final String ITEMS_COUNTER_NAME = "meterengine.scheduled.job.items";
  static final String LAST_SUCCESS_GAUGE_NAME = "meterengine.scheduled.job.last.success";
  static final String JOB_TAG = "job_name";
  static final String RESULT_TAG = "result";
  static final String PROCESSED = "processed";
  static final String SKIPPED = "skipped";
  static final String FAILED = "failed";

  private static final int MAX_ERROR_SUMMARY_LENGTH = 2000;

  private final ScheduledJobRunRepository scheduledJobRunRepository;
  private final MeterRegistry meterRegistry;
  private final Map<String, AtomicLong> lastSuccessEpochSecondsByJobName =
      new ConcurrentHashMap<>();

  ScheduledJobRunService(
      ScheduledJobRunRepository scheduledJobRunRepository, MeterRegistry meterRegistry) {
    this.scheduledJobRunRepository = scheduledJobRunRepository;
    this.meterRegistry = meterRegistry;
  }

  public void register(String jobName) {
    refreshLastSuccess(jobName);
  }

  public void run(String jobName, OffsetDateTime scheduledAt, Supplier<ScheduledJobResult> job) {
    UUID id = UUID.randomUUID();
    if (scheduledJobRunRepository.insertIfAbsent(id, jobName, scheduledAt, OffsetDateTime.now())
        == 0) {
      refreshLastSuccess(jobName);
      return;
    }

    ScheduledJobResult result;
    try {
      result = job.get();
    } catch (RuntimeException exception) {
      finish(id, run -> run.fail(errorSummaryOf(exception), OffsetDateTime.now()));
      countRun(jobName, ScheduledJobRun.FAILED);
      throw exception;
    }

    OffsetDateTime finishedAt = OffsetDateTime.now();
    finish(id, run -> run.succeed(result, finishedAt));
    countRun(jobName, ScheduledJobRun.SUCCEEDED);
    countItems(jobName, result);
    recordSuccess(jobName, finishedAt);
  }

  public Optional<OffsetDateTime> watermark(String jobName, OffsetDateTime before) {
    return scheduledJobRunRepository.findLastCleanScheduledAt(jobName, before);
  }

  public Optional<OffsetDateTime> earliest(String jobName, OffsetDateTime before) {
    return scheduledJobRunRepository.findFirstScheduledAt(jobName, before);
  }

  private void refreshLastSuccess(String jobName) {
    scheduledJobRunRepository
        .findLastSucceededFinishedAt(jobName)
        .ifPresent(finishedAt -> recordSuccess(jobName, finishedAt));
  }

  private void recordSuccess(String jobName, OffsetDateTime finishedAt) {
    lastSuccessOf(jobName).accumulateAndGet(finishedAt.toEpochSecond(), Math::max);
  }

  private void finish(UUID id, Consumer<ScheduledJobRun> transition) {
    ScheduledJobRun run = scheduledJobRunRepository.findById(id).orElseThrow();
    transition.accept(run);
    scheduledJobRunRepository.save(run);
  }

  private static String errorSummaryOf(RuntimeException exception) {
    String summary = exception.toString();
    return summary.length() > MAX_ERROR_SUMMARY_LENGTH
        ? summary.substring(0, MAX_ERROR_SUMMARY_LENGTH)
        : summary;
  }

  private void countRun(String jobName, String result) {
    Counter.builder(RUN_COUNTER_NAME)
        .description("예약 작업 실행 건수. 다른 인스턴스가 먼저 잡은 슬롯은 세지 않는다")
        .tag(JOB_TAG, jobName)
        .tag(RESULT_TAG, result)
        .register(meterRegistry)
        .increment();
  }

  private void countItems(String jobName, ScheduledJobResult result) {
    countItems(jobName, PROCESSED, result.processedCount());
    countItems(jobName, SKIPPED, result.skippedCount());
    countItems(jobName, FAILED, result.failedCount());
  }

  private void countItems(String jobName, String result, int count) {
    Counter.builder(ITEMS_COUNTER_NAME)
        .description("성공한 예약 작업 실행이 처리, 건너뜀, 실패로 끝낸 대상 건수")
        .tag(JOB_TAG, jobName)
        .tag(RESULT_TAG, result)
        .register(meterRegistry)
        .increment(count);
  }

  private AtomicLong lastSuccessOf(String jobName) {
    return lastSuccessEpochSecondsByJobName.computeIfAbsent(
        jobName,
        name -> {
          AtomicLong epochSeconds = new AtomicLong();
          Gauge.builder(LAST_SUCCESS_GAUGE_NAME, epochSeconds, AtomicLong::get)
              .description("예약 작업이 마지막으로 성공한 시각")
              .baseUnit("seconds")
              .tag(JOB_TAG, name)
              .register(meterRegistry);
          return epochSeconds;
        });
  }
}
