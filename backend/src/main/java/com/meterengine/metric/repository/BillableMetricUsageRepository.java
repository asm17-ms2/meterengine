package com.meterengine.metric.repository;

import com.meterengine.event.dto.ReceivedAtRange;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class BillableMetricUsageRepository {

  private static final String IN_PERIOD_AND_RECEIVED_AT_RANGE =
      """
      organization_id = ?
        AND occurred_at >= ?
        AND occurred_at < ?
        AND received_at >= ?
        AND received_at < ?
      """;

  private final JdbcTemplate jdbcTemplate;

  BillableMetricUsageRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public Map<UUID, BigDecimal> sumQuantityByCustomerId(
      UUID organizationId,
      String eventType,
      String targetProperty,
      OffsetDateTime start,
      OffsetDateTime end,
      ReceivedAtRange receivedAtRange) {
    List<Map.Entry<UUID, BigDecimal>> rows =
        jdbcTemplate.query(
            """
            SELECT customer_id, SUM((properties ->> ?::text)::numeric) AS quantity
            FROM event
            WHERE %s
              AND type = ?
              AND jsonb_typeof(properties -> ?::text) = 'number'
            GROUP BY customer_id
            """
                .formatted(IN_PERIOD_AND_RECEIVED_AT_RANGE),
            (rs, rowNum) ->
                Map.entry(rs.getObject("customer_id", UUID.class), rs.getBigDecimal("quantity")),
            targetProperty,
            organizationId,
            start,
            end,
            receivedAtRange.startAt(),
            receivedAtRange.cutoffAt(),
            eventType,
            targetProperty);

    Map<UUID, BigDecimal> quantityByCustomerId = new HashMap<>();
    rows.forEach(row -> quantityByCustomerId.put(row.getKey(), row.getValue()));
    return quantityByCustomerId;
  }

  public Set<UUID> findCustomerIdsWithEvents(
      UUID organizationId,
      OffsetDateTime start,
      OffsetDateTime end,
      ReceivedAtRange receivedAtRange) {
    return Set.copyOf(
        jdbcTemplate.queryForList(
            "SELECT DISTINCT customer_id FROM event WHERE " + IN_PERIOD_AND_RECEIVED_AT_RANGE,
            UUID.class,
            organizationId,
            start,
            end,
            receivedAtRange.startAt(),
            receivedAtRange.cutoffAt()));
  }
}
