package com.meterengine.scheduling.entity;

import com.meterengine.scheduling.dto.ScheduledJobResult;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

@Entity
public class ScheduledJobRun implements Persistable<UUID> {

  public static final String RUNNING = "running";
  public static final String SUCCEEDED = "succeeded";
  public static final String FAILED = "failed";

  @Id private UUID id;

  @Column(name = "job_name", nullable = false)
  private String jobName;

  @Column(name = "scheduled_at", nullable = false)
  private OffsetDateTime scheduledAt;

  @Column(nullable = false)
  private String status;

  @Column(name = "processed_count", nullable = false)
  private int processedCount;

  @Column(name = "skipped_count", nullable = false)
  private int skippedCount;

  @Column(name = "failed_count", nullable = false)
  private int failedCount;

  @Column(name = "error_summary")
  private String errorSummary;

  @Column(name = "started_at", nullable = false)
  private OffsetDateTime startedAt;

  @Column(name = "finished_at")
  private OffsetDateTime finishedAt;

  @Transient private boolean isNew = true;

  protected ScheduledJobRun() {}

  private ScheduledJobRun(
      UUID id, String jobName, OffsetDateTime scheduledAt, OffsetDateTime startedAt) {
    this.id = id;
    this.jobName = jobName;
    this.scheduledAt = scheduledAt;
    this.status = RUNNING;
    this.startedAt = startedAt;
  }

  public static ScheduledJobRun running(
      UUID id, String jobName, OffsetDateTime scheduledAt, OffsetDateTime startedAt) {
    return new ScheduledJobRun(id, jobName, scheduledAt, startedAt);
  }

  @PostLoad
  @PostPersist
  private void markNotNew() {
    this.isNew = false;
  }

  public void succeed(ScheduledJobResult result, OffsetDateTime finishedAt) {
    this.status = SUCCEEDED;
    this.processedCount = result.processedCount();
    this.skippedCount = result.skippedCount();
    this.failedCount = result.failedCount();
    this.finishedAt = finishedAt;
  }

  public void fail(String errorSummary, OffsetDateTime finishedAt) {
    this.status = FAILED;
    this.errorSummary = errorSummary;
    this.finishedAt = finishedAt;
  }

  @Override
  public UUID getId() {
    return id;
  }

  @Override
  public boolean isNew() {
    return isNew;
  }

  public String getJobName() {
    return jobName;
  }

  public OffsetDateTime getScheduledAt() {
    return scheduledAt;
  }

  public String getStatus() {
    return status;
  }

  public int getProcessedCount() {
    return processedCount;
  }

  public int getSkippedCount() {
    return skippedCount;
  }

  public int getFailedCount() {
    return failedCount;
  }

  public String getErrorSummary() {
    return errorSummary;
  }

  public OffsetDateTime getStartedAt() {
    return startedAt;
  }

  public OffsetDateTime getFinishedAt() {
    return finishedAt;
  }
}
