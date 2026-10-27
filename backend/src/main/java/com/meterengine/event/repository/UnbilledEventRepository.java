package com.meterengine.event.repository;

import com.meterengine.event.dto.UnbilledEventsResponse.FutureTimestamp;
import com.meterengine.event.dto.UnbilledEventsResponse.MetricCount;
import com.meterengine.event.dto.UnbilledEventsResponse.MetricCounts;
import com.meterengine.event.dto.UnbilledEventsResponse.TypeCount;
import com.meterengine.event.dto.UnbilledEventsResponse.UnmatchedType;
import com.meterengine.global.security.BusinessEnvironmentContext;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UnbilledEventRepository {
  private final JdbcTemplate jdbcTemplate;

  UnbilledEventRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public UnmatchedType findUnmatchedTypes(
      UUID organizationId, OffsetDateTime startAt, OffsetDateTime cutoffAt) {
    List<TypeCount> counts =
        jdbcTemplate.query(
            """
            SELECT e.type, count(*) AS event_count FROM event e
            WHERE e.organization_id = ? AND e.environment_id IS NOT DISTINCT FROM ?::uuid AND e.occurred_at >= ? AND e.occurred_at < ?
              AND NOT EXISTS (SELECT 1 FROM billable_metric m WHERE m.organization_id = e.organization_id AND m.environment_scope_id = e.environment_scope_id AND m.event_type = e.type AND ((m.archived_at IS NULL AND m.active_from <= to_char(e.occurred_at AT TIME ZONE 'Asia/Seoul', 'YYYY-MM')) OR EXISTS (SELECT 1 FROM billable_metric_period p WHERE p.organization_id = m.organization_id AND p.environment_scope_id = m.environment_scope_id AND p.billable_metric_code = m.code AND p.active_from <= to_char(e.occurred_at AT TIME ZONE 'Asia/Seoul', 'YYYY-MM') AND p.active_until > to_char(e.occurred_at AT TIME ZONE 'Asia/Seoul', 'YYYY-MM'))))
            GROUP BY e.type ORDER BY event_count DESC, e.type
            """,
            (rs, rowNum) -> new TypeCount(rs.getString("type"), rs.getLong("event_count")),
            organizationId,
            BusinessEnvironmentContext.environmentId(),
            startAt,
            cutoffAt);
    return new UnmatchedType(
        counts.stream().mapToLong(TypeCount::eventCount).sum(), counts.stream().limit(20).toList());
  }

  public MetricCounts findNonNumericValues(
      UUID organizationId, OffsetDateTime startAt, OffsetDateTime cutoffAt) {
    return metricCounts(
        organizationId,
        startAt,
        cutoffAt,
        "m.aggregation <> 'count' AND CASE WHEN m.aggregation = 'count_distinct' THEN"
            + " COALESCE(jsonb_typeof(e.properties -> m.target_property), 'null') NOT IN ('number',"
            + " 'string', 'boolean') ELSE jsonb_typeof(e.properties -> m.target_property) IS"
            + " DISTINCT FROM 'number' END");
  }

  public MetricCounts findNegativeQuantities(
      UUID organizationId, OffsetDateTime startAt, OffsetDateTime cutoffAt) {
    return metricCounts(
        organizationId,
        startAt,
        cutoffAt,
        "m.aggregation NOT IN ('count', 'count_distinct') AND CASE WHEN jsonb_typeof(e.properties"
            + " -> m.target_property) = 'number' THEN (e.properties ->> m.target_property)::numeric"
            + " < 0 ELSE false END");
  }

  public FutureTimestamp findFutureTimestamps(
      UUID organizationId, OffsetDateTime startAt, OffsetDateTime endAt, OffsetDateTime cutoffAt) {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) AS event_count, min(occurred_at) AS earliest_occurred_at, max(occurred_at)"
            + " AS latest_occurred_at FROM event WHERE organization_id = ? AND environment_id IS"
            + " NOT DISTINCT FROM ?::uuid AND occurred_at >= ? AND occurred_at < ? AND occurred_at"
            + " >= ?",
        (rs, rowNum) ->
            new FutureTimestamp(
                rs.getLong("event_count"),
                rs.getObject("earliest_occurred_at", OffsetDateTime.class),
                rs.getObject("latest_occurred_at", OffsetDateTime.class)),
        organizationId,
        BusinessEnvironmentContext.environmentId(),
        startAt,
        endAt,
        cutoffAt);
  }

  private MetricCounts metricCounts(
      UUID organizationId, OffsetDateTime startAt, OffsetDateTime cutoffAt, String condition) {
    List<MetricCount> counts =
        jdbcTemplate.query(
            "SELECT m.code, count(*) AS event_count FROM event e JOIN billable_metric m ON"
                + " m.organization_id = e.organization_id AND m.environment_scope_id ="
                + " e.environment_scope_id AND m.event_type = e.type AND ((m.archived_at IS NULL"
                + " AND m.active_from <= to_char(e.occurred_at AT TIME ZONE 'Asia/Seoul',"
                + " 'YYYY-MM')) OR EXISTS (SELECT 1 FROM billable_metric_period p WHERE"
                + " p.organization_id = m.organization_id AND p.environment_scope_id ="
                + " m.environment_scope_id AND p.billable_metric_code = m.code AND p.active_from <="
                + " to_char(e.occurred_at AT TIME ZONE 'Asia/Seoul', 'YYYY-MM') AND p.active_until"
                + " > to_char(e.occurred_at AT TIME ZONE 'Asia/Seoul', 'YYYY-MM'))) WHERE"
                + " e.organization_id = ? AND e.environment_id IS NOT DISTINCT FROM ?::uuid AND"
                + " e.occurred_at >= ? AND e.occurred_at < ? AND ("
                + condition
                + ") GROUP BY m.code ORDER BY m.code",
            (rs, rowNum) -> new MetricCount(rs.getString("code"), rs.getLong("event_count")),
            organizationId,
            BusinessEnvironmentContext.environmentId(),
            startAt,
            cutoffAt);
    return new MetricCounts(counts.stream().mapToLong(MetricCount::eventCount).sum(), counts);
  }
}
