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

  private final JdbcTemplate jdbc;

  EventRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public int insertIfAbsent(
      UUID organizationId,
      String transactionId,
      UUID customerId,
      String type,
      String propertiesJson,
      OffsetDateTime occurredAt) {
    return jdbc.update(
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
    List<Object> params = new ArrayList<>();
    String where = buildWhere(organizationId, customerId, type, start, end, params);
    params.add(size);
    params.add((long) page * size);

    return jdbc.query(
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
        params.toArray());
  }

  public long count(
      UUID organizationId, UUID customerId, String type, OffsetDateTime start, OffsetDateTime end) {
    List<Object> params = new ArrayList<>();
    String where = buildWhere(organizationId, customerId, type, start, end, params);

    Long total =
        jdbc.queryForObject("SELECT count(*) FROM event e " + where, Long.class, params.toArray());
    return total == null ? 0 : total;
  }

  public boolean existsForCustomer(UUID organizationId, UUID customerId) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
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
      List<Object> params) {
    StringBuilder where =
        new StringBuilder(
            "WHERE e.organization_id = ? AND e.occurred_at >= ? AND e.occurred_at < ?");
    params.add(organizationId);
    params.add(start);
    params.add(end);

    if (customerId != null) {
      where.append(" AND e.customer_id = ?");
      params.add(customerId);
    }
    if (type != null && !type.isBlank()) {
      where.append(" AND e.type = ?");
      params.add(type);
    }
    return where.append('\n').toString();
  }
}
