package com.meterengine.scheduling.repository;

import com.meterengine.scheduling.entity.ScheduledJobRun;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface ScheduledJobRunRepository extends JpaRepository<ScheduledJobRun, UUID> {

  @Modifying
  @Transactional
  @Query(
      value =
          """
          INSERT INTO scheduled_job_run (id, job_name, scheduled_at, status, started_at)
          VALUES (:id, :jobName, :scheduledAt, 'running', :startedAt)
          ON CONFLICT (job_name, scheduled_at) DO NOTHING
          """,
      nativeQuery = true)
  int insertIfAbsent(UUID id, String jobName, OffsetDateTime scheduledAt, OffsetDateTime startedAt);

  @Query(
      """
      SELECT max(run.finishedAt) FROM ScheduledJobRun run
      WHERE run.jobName = :jobName AND run.status = 'succeeded'
      """)
  Optional<OffsetDateTime> findLastSucceededFinishedAt(String jobName);
}
