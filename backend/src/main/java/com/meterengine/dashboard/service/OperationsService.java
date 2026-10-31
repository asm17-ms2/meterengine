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
            SELECT scope,job_name,last_success_at,last_started_at,latest_status
            FROM shared.operations_job_status ORDER BY scope,job_name
            """,
            (row, index) ->
                new OperationsJob(
                    row.getString("scope"),
                    row.getString("job_name"),
                    row.getObject("last_success_at", OffsetDateTime.class),
                    row.getObject("last_started_at", OffsetDateTime.class),
                    row.getString("latest_status")));
    return new OperationsResponse(
        OffsetDateTime.now(clock),
        count("select count(*) from organization where status = 'pending'"),
        count("select count(*) from organization where status = 'active'"),
        count("select count(*) from user_account"),
        count(
            "SELECT pending_payment_count FROM shared.operations_business_counts WHERE scope='live'"),
        count(
            "SELECT failed_payment_24h_count FROM shared.operations_business_counts WHERE scope='live'"),
        count(
            "SELECT failed_webhook_count FROM shared.operations_business_counts WHERE scope='live'"),
        jobs);
  }

  private long count(String sql) {
    Long result = jdbcTemplate.queryForObject(sql, Long.class);
    return result == null ? 0 : result;
  }
}
