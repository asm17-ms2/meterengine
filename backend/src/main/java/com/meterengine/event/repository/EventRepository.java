package com.meterengine.event.repository;

import com.meterengine.event.dto.Event;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class EventRepository {

  private final JdbcTemplate jdbcTemplate;

  EventRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public int insertIfAbsent(
      UUID organizationId,
      String transactionId,
      UUID customerId,
      String type,
      String propertiesJson,
      OffsetDateTime occurredAt) {
    return jdbcTemplate.update(
        """
        INSERT INTO event
          (organization_id, transaction_id, customer_id, type, properties, occurred_at)
        VALUES (?, ?, ?, ?, ?::jsonb, ?)
        ON CONFLICT (organization_id, transaction_id) DO NOTHING
        """,
        organizationId,
        transactionId,
        customerId,
        type,
        propertiesJson,
        occurredAt);
  }

  public List<Event> findPage(
      UUID organizationId,
      UUID customerId,
      String type,
      OffsetDateTime start,
      OffsetDateTime end,
      int page,
      int size) {
    List<Object> parameters = new ArrayList<>();
    String where = buildWhere(organizationId, customerId, type, start, end, parameters);
    parameters.add(size);
    parameters.add((long) page * size);

    return jdbcTemplate.query(
        """
        SELECT e.transaction_id, e.customer_id, c.name AS customer_name, e.type,
               e.properties::text AS properties, e.occurred_at, e.received_at
        FROM event e
        LEFT JOIN customer c
          ON c.organization_id = e.organization_id AND c.id = e.customer_id
        """
            + where
            + """
        ORDER BY e.occurred_at DESC, e.transaction_id DESC
        LIMIT ? OFFSET ?
        """,
        (rs, rowNum) ->
            new Event(
                rs.getString("transaction_id"),
                rs.getObject("customer_id", UUID.class),
                rs.getString("customer_name"),
                rs.getString("type"),
                rs.getString("properties"),
                rs.getObject("occurred_at", OffsetDateTime.class),
                rs.getObject("received_at", OffsetDateTime.class)),
        parameters.toArray());
  }

  public long count(
      UUID organizationId, UUID customerId, String type, OffsetDateTime start, OffsetDateTime end) {
    List<Object> parameters = new ArrayList<>();
    String where = buildWhere(organizationId, customerId, type, start, end, parameters);

    Long total =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM event e " + where, Long.class, parameters.toArray());
    return total == null ? 0 : total;
  }

  public boolean existsForCustomer(UUID organizationId, UUID customerId) {
    return Boolean.TRUE.equals(
        jdbcTemplate.queryForObject(
            """
            SELECT EXISTS(
              SELECT 1 FROM event
              WHERE organization_id = ? AND customer_id = ?
            )
            """,
            Boolean.class,
            organizationId,
            customerId));
  }

  private String buildWhere(
      UUID organizationId,
      UUID customerId,
      String type,
      OffsetDateTime start,
      OffsetDateTime end,
      List<Object> parameters) {
    StringBuilder where =
        new StringBuilder(
            "WHERE e.organization_id = ? AND e.occurred_at >= ? AND e.occurred_at < ?");
    parameters.add(organizationId);
    parameters.add(start);
    parameters.add(end);

    if (customerId != null) {
      where.append(" AND e.customer_id = ?");
      parameters.add(customerId);
    }
    if (type != null && !type.isBlank()) {
      where.append(" AND e.type = ?");
      parameters.add(type);
    }
    return where.append('\n').toString();
  }
}
