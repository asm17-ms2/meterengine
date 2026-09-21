package com.meterengine.metric.repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class BillableMetricUsageRepository {

  private final JdbcTemplate jdbc;

  BillableMetricUsageRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Map<UUID, BigDecimal> sumQuantityByCustomerId(
      UUID organizationId,
      String eventType,
      String targetProperty,
      OffsetDateTime start,
      OffsetDateTime end) {
    List<Map.Entry<UUID, BigDecimal>> rows =
        jdbc.query(
            """
            SELECT customer_id, SUM((properties ->> ?::text)::numeric) AS quantity
            FROM event
            WHERE organization_id = ?
              AND type = ?
              AND occurred_at >= ?
              AND occurred_at < ?
              AND jsonb_typeof(properties -> ?::text) = 'number'
            GROUP BY customer_id
            """,
            (rs, rowNum) ->
                Map.entry(rs.getObject("customer_id", UUID.class), rs.getBigDecimal("quantity")),
            targetProperty,
            organizationId,
            eventType,
            start,
            end,
            targetProperty);

    Map<UUID, BigDecimal> quantityByCustomerId = new HashMap<>();
    rows.forEach(row -> quantityByCustomerId.put(row.getKey(), row.getValue()));
    return quantityByCustomerId;
  }
}
