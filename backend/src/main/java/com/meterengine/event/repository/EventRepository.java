package com.meterengine.event.repository;

import com.meterengine.event.dto.Event;
import com.meterengine.event.dto.EventInsertion;
import com.meterengine.event.service.EventSearch;
import com.meterengine.global.security.BusinessEnvironmentContext;
import com.meterengine.global.security.RequestIdentity;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class EventRepository {

  // 월 조회(month)가 네 자리 연도까지만 받아 그 밖의 달은 열 수 없다.
  public static final String OPENABLE_MONTH_CONDITION =
      " AND occurred_at < TIMESTAMPTZ '10000-01-01 00:00:00+09'";

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
    RequestIdentity identity = RequestIdentity.current();
    return jdbcTemplate.update(
        """
        INSERT INTO event
          (organization_id, transaction_id, customer_id, type, properties, occurred_at, environment_id, request_id, actor_type, actor_id)
        VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?)
        ON CONFLICT (organization_id, environment_scope_id, transaction_id) DO NOTHING
        """,
        organizationId,
        transactionId,
        customerId,
        type,
        propertiesJson,
        occurredAt,
        BusinessEnvironmentContext.environmentId(),
        identity.requestId(),
        identity.actorType(),
        identity.actorId());
  }

  public List<String> insertAllIfAbsent(UUID organizationId, List<EventInsertion> events) {
    if (events.isEmpty()) {
      return List.of();
    }
    RequestIdentity identity = RequestIdentity.current();
    String placeholders =
        String.join(
            ",",
            java.util.Collections.nCopies(events.size(), "(?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?)"));
    List<Object> parameters = new ArrayList<>();
    for (EventInsertion event : events) {
      parameters.add(organizationId);
      parameters.add(event.transactionId());
      parameters.add(event.customerId());
      parameters.add(event.type());
      parameters.add(event.properties());
      parameters.add(event.occurredAt());
      parameters.add(BusinessEnvironmentContext.environmentId());
      parameters.add(identity.requestId());
      parameters.add(identity.actorType());
      parameters.add(identity.actorId());
    }
    return jdbcTemplate.query(
        "INSERT INTO event (organization_id, transaction_id, customer_id, type, properties, occurred_at, environment_id, request_id, actor_type, actor_id) VALUES "
            + placeholders
            + " ON CONFLICT (organization_id, environment_scope_id, transaction_id) DO NOTHING RETURNING transaction_id",
        (rs, rowNum) -> rs.getString("transaction_id"),
        parameters.toArray());
  }

  public List<String> findExistingTransactionIds(UUID organizationId, List<String> transactionIds) {
    if (transactionIds.isEmpty()) {
      return List.of();
    }
    List<Object> parameters = new ArrayList<>();
    parameters.add(organizationId);
    parameters.add(BusinessEnvironmentContext.scopeId());
    parameters.addAll(transactionIds);
    return jdbcTemplate.query(
        "SELECT transaction_id FROM event WHERE organization_id = ? AND environment_scope_id = ? AND transaction_id IN ("
            + String.join(",", java.util.Collections.nCopies(transactionIds.size(), "?"))
            + ")",
        (rs, rowNum) -> rs.getString("transaction_id"),
        parameters.toArray());
  }

  public List<Event> findPage(
      UUID organizationId,
      UUID customerId,
      String type,
      OffsetDateTime start,
      OffsetDateTime end,
      int page,
      int size) {
    return findPage(
        organizationId,
        customerId,
        type,
        new EventSearch(null, start, end, null, "occurred_at", "desc", null),
        page,
        size);
  }

  public List<Event> findPage(
      UUID organizationId, UUID customerId, String type, EventSearch search, int page, int size) {
    List<Object> parameters = new ArrayList<>();
    String where = buildSearchWhere(organizationId, customerId, type, search, parameters);
    parameters.add(size);
    parameters.add((long) page * size);

    return jdbcTemplate.query(
        """
        SELECT e.transaction_id, e.customer_id, c.name AS customer_name, e.type,
               e.properties::text AS properties, e.occurred_at, e.received_at, e.request_id, e.actor_type, e.actor_id
        FROM event e
        LEFT JOIN customer c
          ON c.organization_id = e.organization_id AND c.id = e.customer_id
        """
            + where
            + " ORDER BY "
            + orderColumn(search.sort())
            + " "
            + direction(search.order())
            + ", e.transaction_id "
            + direction(search.order())
            + " LIMIT ? OFFSET ?",
        (rs, rowNum) ->
            new Event(
                rs.getString("transaction_id"),
                rs.getObject("customer_id", UUID.class),
                rs.getString("customer_name"),
                rs.getString("type"),
                rs.getString("properties"),
                rs.getObject("occurred_at", OffsetDateTime.class),
                rs.getObject("received_at", OffsetDateTime.class),
                rs.getObject("request_id", UUID.class),
                rs.getString("actor_type"),
                rs.getObject("actor_id", UUID.class)),
        parameters.toArray());
  }

  public long count(
      UUID organizationId, UUID customerId, String type, OffsetDateTime start, OffsetDateTime end) {
    return count(
        organizationId,
        customerId,
        type,
        new EventSearch(null, start, end, null, "occurred_at", "desc", null));
  }

  public long count(UUID organizationId, UUID customerId, String type, EventSearch search) {
    List<Object> parameters = new ArrayList<>();
    String where = buildSearchWhere(organizationId, customerId, type, search, parameters);

    Long total =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM event e LEFT JOIN customer c ON c.organization_id=e.organization_id AND c.id=e.customer_id "
                + where,
            Long.class,
            parameters.toArray());
    return total == null ? 0 : total;
  }

  public List<String> findTypes(UUID organizationId, UUID customerId, EventSearch search) {
    List<Object> parameters = new ArrayList<>();
    String where = buildSearchWhere(organizationId, customerId, null, search, parameters);
    return jdbcTemplate.query(
        "SELECT DISTINCT e.type COLLATE korean AS type FROM event e LEFT JOIN customer c ON c.organization_id=e.organization_id AND c.id=e.customer_id "
            + where
            + " ORDER BY type",
        (rs, rowNum) -> rs.getString("type"),
        parameters.toArray());
  }

  public List<String> findMonths(UUID organizationId) {
    return jdbcTemplate.query(
        "SELECT DISTINCT to_char(occurred_at AT TIME ZONE 'Asia/Seoul', 'YYYY-MM') AS month FROM event WHERE organization_id = ? AND environment_scope_id = ?"
            + OPENABLE_MONTH_CONDITION
            + " ORDER BY month DESC",
        (rs, rowNum) -> rs.getString("month"),
        organizationId,
        BusinessEnvironmentContext.scopeId());
  }

  public java.util.Optional<Event> find(UUID organizationId, String transactionId) {
    return jdbcTemplate
        .query(
            """
        SELECT e.transaction_id, e.customer_id, c.name AS customer_name, e.type,
               e.properties::text AS properties, e.occurred_at, e.received_at, e.request_id, e.actor_type, e.actor_id
        FROM event e LEFT JOIN customer c ON c.organization_id=e.organization_id AND c.id=e.customer_id
        WHERE e.organization_id=? AND e.environment_scope_id = ? AND e.transaction_id=?
        """,
            (rs, rowNum) ->
                new Event(
                    rs.getString("transaction_id"),
                    rs.getObject("customer_id", UUID.class),
                    rs.getString("customer_name"),
                    rs.getString("type"),
                    rs.getString("properties"),
                    rs.getObject("occurred_at", OffsetDateTime.class),
                    rs.getObject("received_at", OffsetDateTime.class),
                    rs.getObject("request_id", UUID.class),
                    rs.getString("actor_type"),
                    rs.getObject("actor_id", UUID.class)),
            organizationId,
            BusinessEnvironmentContext.scopeId(),
            transactionId)
        .stream()
        .findFirst();
  }

  private String buildSearchWhere(
      UUID organizationId,
      UUID customerId,
      String type,
      EventSearch search,
      List<Object> parameters) {
    StringBuilder where =
        new StringBuilder(
            buildWhere(
                organizationId, customerId, type, search.fromAt(), search.toAt(), parameters));
    if (search.query() != null && !search.query().isBlank()) {
      where.append(
          " AND (position(lower(?) in lower(e.transaction_id)) > 0 OR position(lower(?) in lower(c.name)) > 0 OR position(lower(?) in lower(coalesce(c.external_id, ''))) > 0)");
      parameters.add(search.query());
      parameters.add(search.query());
      parameters.add(search.query());
    }
    if (search.properties() != null && !search.properties().isBlank()) {
      where.append(" AND e.properties @> ?::jsonb");
      parameters.add(search.properties());
    }
    return where.toString();
  }

  private String orderColumn(String field) {
    return switch (field) {
      case "received_at" -> "e.received_at";
      case "transaction_id" -> "e.transaction_id";
      case "customer_name" -> "c.name COLLATE korean";
      case "type" -> "e.type COLLATE korean";
      default -> "e.occurred_at";
    };
  }

  private String direction(String order) {
    return "asc".equals(order) ? "ASC" : "DESC";
  }

  public boolean existsWithType(UUID organizationId, String type) {
    return Boolean.TRUE.equals(
        jdbcTemplate.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM event WHERE organization_id = ? AND environment_scope_id = ? AND type = ?)",
            Boolean.class,
            organizationId,
            BusinessEnvironmentContext.scopeId(),
            type));
  }

  public boolean existsWithNumericProperty(UUID organizationId, String type, String property) {
    return Boolean.TRUE.equals(
        jdbcTemplate.queryForObject(
            """
            SELECT EXISTS(
              SELECT 1 FROM event
              WHERE organization_id = ?
                AND environment_scope_id = ?
                AND type = ?
                AND jsonb_typeof(properties -> ?::text) = 'number'
            )
            """,
            Boolean.class,
            organizationId,
            BusinessEnvironmentContext.scopeId(),
            type,
            property));
  }

  public boolean exists(UUID organizationId, String transactionId) {
    return Boolean.TRUE.equals(
        jdbcTemplate.queryForObject(
            """
            SELECT EXISTS(
              SELECT 1 FROM event
              WHERE organization_id = ? AND environment_scope_id = ? AND transaction_id = ?
            )
            """,
            Boolean.class,
            organizationId,
            BusinessEnvironmentContext.scopeId(),
            transactionId));
  }

  public boolean existsForCustomer(UUID organizationId, UUID customerId) {
    return Boolean.TRUE.equals(
        jdbcTemplate.queryForObject(
            """
            SELECT EXISTS(
              SELECT 1 FROM event
              WHERE organization_id = ? AND environment_scope_id = ? AND customer_id = ?
            )
            """,
            Boolean.class,
            organizationId,
            BusinessEnvironmentContext.scopeId(),
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
            "WHERE e.organization_id = ? AND e.environment_scope_id = ? AND e.occurred_at >= ? AND e.occurred_at < ?");
    parameters.add(organizationId);
    parameters.add(BusinessEnvironmentContext.scopeId());
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
