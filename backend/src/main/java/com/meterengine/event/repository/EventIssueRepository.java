package com.meterengine.event.repository;

import com.meterengine.event.dto.EventIssueResponse;
import com.meterengine.event.dto.EventIssuesResponse.Summary;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class EventIssueRepository {
  private final JdbcTemplate jdbcTemplate;

  public EventIssueRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public record IssuePage(List<EventIssueResponse> issues, Summary summary) {}

  public IssuePage search(
      UUID organizationId,
      YearMonth month,
      OffsetDateTime start,
      OffsetDateTime cutoff,
      UUID customerId,
      String reason,
      String query,
      int page,
      int size,
      String unpricedJson) {
    List<Object> parameters = new ArrayList<>();
    parameters.add(organizationId);
    parameters.add(start);
    parameters.add(cutoff);
    StringBuilder filters = new StringBuilder();
    if (customerId != null) {
      filters.append(" AND e.customer_id = ?");
      parameters.add(customerId);
    }
    if (query != null && !query.isBlank()) {
      filters.append(
          " AND (position(lower(?) in lower(e.transaction_id)) > 0 OR position(lower(?) in lower(c.name)) > 0 OR position(lower(?) in lower(coalesce(c.external_id, ''))) > 0)");
      parameters.add(query);
      parameters.add(query);
      parameters.add(query);
    }
    parameters.add(organizationId);
    parameters.add(month.toString());
    parameters.add(month.toString());
    parameters.add(month.toString());
    parameters.add(unpricedJson);
    String eligible =
        "(m.aggregation = 'count' OR (m.aggregation = 'count_distinct' AND coalesce(jsonb_typeof(e.properties -> m.target_property), 'null') IN ('number', 'string', 'boolean')) OR (m.aggregation NOT IN ('count', 'count_distinct') AND jsonb_typeof(e.properties -> m.target_property) = 'number'))";
    String cte =
        """
        WITH selected_events AS (
          SELECT e.*, c.name AS customer_name FROM event e
          JOIN customer c ON c.organization_id = e.organization_id AND c.id = e.customer_id
          WHERE e.organization_id = ? AND e.environment_id IS NULL
            AND e.occurred_at >= ? AND e.occurred_at < ?
        """
            + filters
            + """
        ), active_metrics AS (
          SELECT m.* FROM billable_metric m WHERE m.organization_id = ? AND (
            (m.archived_at IS NULL AND m.active_from <= ?)
            OR EXISTS (SELECT 1 FROM billable_metric_period p
              WHERE p.organization_id = m.organization_id AND p.billable_metric_code = m.code
                AND p.active_from <= ? AND p.active_until > ?))
        ), unpriced_groups AS (
          SELECT * FROM jsonb_to_recordset(?::jsonb)
            AS g(customer_id UUID, billable_metric_code TEXT, dimension_values JSONB)
        ), issues AS (
          SELECT e.transaction_id, e.customer_id, e.customer_name, e.type, e.occurred_at, e.received_at,
            'unknown_event_type'::text AS reason, NULL::text AS billable_metric_code,
            NULL::text AS target_property, false AS negative_quantity
          FROM selected_events e WHERE NOT EXISTS (SELECT 1 FROM active_metrics m WHERE m.event_type = e.type)
          UNION ALL
          SELECT e.transaction_id, e.customer_id, e.customer_name, e.type, e.occurred_at, e.received_at,
            'invalid_target_property'::text, m.code, m.target_property, false
          FROM selected_events e JOIN active_metrics m ON m.event_type = e.type
          WHERE NOT coalesce(
        """
            + eligible
            + """
          , false)
          UNION ALL
          SELECT e.transaction_id, e.customer_id, e.customer_name, e.type, e.occurred_at, e.received_at,
            'unpriced_usage'::text, m.code, m.target_property,
            CASE WHEN m.aggregation NOT IN ('count', 'count_distinct')
              AND jsonb_typeof(e.properties -> m.target_property) = 'number'
              THEN (e.properties ->> m.target_property)::numeric < 0 ELSE false END
          FROM selected_events e JOIN active_metrics m ON m.event_type = e.type
          JOIN unpriced_groups g ON g.customer_id = e.customer_id AND g.billable_metric_code = m.code
            AND g.dimension_values = coalesce((SELECT jsonb_object_agg(dimension_key, e.properties -> dimension_key)
              FROM unnest(m.dimension_properties) dimensions(dimension_key)), '{}'::jsonb)
          WHERE
        """
            + eligible
            + """
        )
        """;
    String reasonWhere = reason == null ? "" : " WHERE reason = ?";
    if (reason != null) parameters.add(reason);
    Map<String, Long> counts =
        jdbcTemplate.query(
            cte
                + " SELECT reason, count(*) AS count FROM issues"
                + reasonWhere
                + " GROUP BY reason",
            rs -> {
              Map<String, Long> result = new java.util.HashMap<>();
              while (rs.next()) result.put(rs.getString("reason"), rs.getLong("count"));
              return result;
            },
            parameters.toArray());
    Summary summary =
        new Summary(
            counts.getOrDefault("unknown_event_type", 0L),
            counts.getOrDefault("invalid_target_property", 0L),
            counts.getOrDefault("unpriced_usage", 0L));
    parameters.add(size);
    parameters.add(Math.multiplyExact((long) page, size));
    List<EventIssueResponse> rows =
        jdbcTemplate.query(
            cte
                + " SELECT * FROM issues"
                + reasonWhere
                + " ORDER BY occurred_at DESC, transaction_id COLLATE \"C\", billable_metric_code COLLATE \"C\" NULLS FIRST, reason LIMIT ? OFFSET ?",
            EventIssueRepository::row,
            parameters.toArray());
    return new IssuePage(rows, summary);
  }

  private static EventIssueResponse row(ResultSet rs, int index) throws SQLException {
    String reason = rs.getString("reason");
    String hint =
        switch (reason) {
          case "unknown_event_type" -> "이벤트 유형에 맞는 미터를 정의하거나 전송 유형을 확인하세요";
          case "invalid_target_property" -> "미터의 수량 속성과 전송 값의 형식을 확인하세요";
          default -> "고객, 미터와 차원에 적용되는 단가를 등록하세요";
        };
    return new EventIssueResponse(
        rs.getString("transaction_id"),
        rs.getObject("customer_id", UUID.class),
        rs.getString("customer_name"),
        rs.getString("type"),
        rs.getObject("occurred_at", OffsetDateTime.class),
        rs.getObject("received_at", OffsetDateTime.class),
        reason,
        rs.getString("billable_metric_code"),
        rs.getString("target_property"),
        hint,
        rs.getBoolean("negative_quantity"));
  }
}
