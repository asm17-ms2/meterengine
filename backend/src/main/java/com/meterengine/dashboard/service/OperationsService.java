package com.meterengine.dashboard.service;

import com.meterengine.dashboard.dto.OperationsResponse;
import com.meterengine.dashboard.dto.OperationsResponse.OperationsJob;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperationsService {
  private final JdbcTemplate jdbcTemplate;
  private final Clock clock;

  OperationsService(JdbcTemplate jdbcTemplate, Clock clock) {
    this.jdbcTemplate = jdbcTemplate;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public OperationsResponse get() {
    List<OperationsJob> jobs =
        jdbcTemplate.query(
            """
            select expected.job_name,
              (select max(finished_at) from scheduled_job_run r
                 where r.job_name = expected.job_name and r.status = 'succeeded') last_success_at,
              latest.started_at last_started_at, latest.status latest_status
            from (values ('payment_attempt_confirmation'), ('payment_retry'), ('payment_reconciliation'),
                         ('webhook_delivery'), ('unbilled_usage_report')) expected(job_name)
            left join lateral (select started_at, status from scheduled_job_run r
              where r.job_name = expected.job_name order by started_at desc limit 1) latest on true
            order by expected.job_name
            """,
            (row, index) ->
                new OperationsJob(
                    row.getString("job_name"),
                    row.getObject("last_success_at", OffsetDateTime.class),
                    row.getObject("last_started_at", OffsetDateTime.class),
                    row.getString("latest_status")));
    return new OperationsResponse(
        OffsetDateTime.now(clock),
        count("select count(*) from organization where status = 'pending'"),
        count("select count(*) from organization where status = 'active'"),
        count("select count(*) from user_account"),
        count("select count(*) from payment_attempt where status = 'pending'"),
        count(
            "select count(*) from payment_attempt where status = 'failed' and requested_at >= now()"
                + " - interval '24 hours'"),
        count("select count(*) from webhook_delivery where status = 'failed'"),
        jobs);
  }

  private long count(String sql) {
    Long result = jdbcTemplate.queryForObject(sql, Long.class);
    return result == null ? 0 : result;
  }
}
