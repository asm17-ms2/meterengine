package com.meterengine.scheduling.repository;

import com.meterengine.scheduling.entity.ScheduledJobRun;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface ScheduledJobRunRepository extends JpaRepository<ScheduledJobRun, UUID> {

  @Transactional
  @Query(
      value =
          """
          SELECT CASE WHEN shared.claim_job(:id, :jobName, :scheduledAt, :startedAt) THEN 1 ELSE 0 END
          """,
      nativeQuery = true)
  int insertIfAbsent(UUID id, String jobName, OffsetDateTime scheduledAt, OffsetDateTime startedAt);

  @Query(value = "SELECT shared.job_last_success(:jobName)", nativeQuery = true)
  Optional<java.time.Instant> lastSuccessInstant(String jobName);

  default Optional<OffsetDateTime> findLastSucceededFinishedAt(String jobName) {
    return lastSuccessInstant(jobName).map(value -> value.atOffset(java.time.ZoneOffset.UTC));
  }

  @Query(value = "SELECT shared.job_last_completion(:jobName)", nativeQuery = true)
  Optional<java.time.Instant> lastCompletionInstant(String jobName);

  default Optional<OffsetDateTime> findLastFinishedAt(String jobName) {
    return lastCompletionInstant(jobName).map(value -> value.atOffset(java.time.ZoneOffset.UTC));
  }

  @Query(value = "SELECT shared.job_watermark(:jobName,:before)", nativeQuery = true)
  Optional<java.time.Instant> watermarkInstant(String jobName, OffsetDateTime before);

  default Optional<OffsetDateTime> findLastCleanScheduledAt(String jobName, OffsetDateTime before) {
    return watermarkInstant(jobName, before).map(value -> value.atOffset(java.time.ZoneOffset.UTC));
  }

  @Query(value = "SELECT shared.job_earliest(:jobName,:before)", nativeQuery = true)
  Optional<java.time.Instant> earliestInstant(String jobName, OffsetDateTime before);

  default Optional<OffsetDateTime> findFirstScheduledAt(String jobName, OffsetDateTime before) {
    return earliestInstant(jobName, before).map(value -> value.atOffset(java.time.ZoneOffset.UTC));
  }

  @Transactional
  @Query(
      value =
          "SELECT shared.complete_job(:id,:status,:processed,:skipped,:failed,:error,:finished)",
      nativeQuery = true)
  boolean complete(
      UUID id,
      String status,
      int processed,
      int skipped,
      int failed,
      String error,
      OffsetDateTime finished);
}
