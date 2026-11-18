package com.meterengine.dashboard.repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DailyEventCountRepository {
  private final JdbcTemplate jdbcTemplate;

  public DailyEventCountRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /** 이벤트가 없는 날은 결과에 없다. */
  public Map<LocalDate, Long> countByEventDate(
      UUID organizationId, UUID environmentScopeId, OffsetDateTime from, OffsetDateTime until) {
    Map<LocalDate, Long> eventCountByDate = new HashMap<>();
    jdbcTemplate.query(
        """
        SELECT (occurred_at AT TIME ZONE 'Asia/Seoul')::date AS event_date, count(*) AS event_count
        FROM event
        WHERE organization_id = ? AND environment_scope_id = ?
          AND occurred_at >= ? AND occurred_at < ?
        GROUP BY event_date
        """,
        rs -> {
          eventCountByDate.put(
              rs.getObject("event_date", LocalDate.class), rs.getLong("event_count"));
        },
        organizationId,
        environmentScopeId,
        from,
        until);
    return eventCountByDate;
  }
}
