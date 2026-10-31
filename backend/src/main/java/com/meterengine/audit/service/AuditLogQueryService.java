package com.meterengine.audit.service;

import com.meterengine.audit.dto.AuditLogResponse;
import com.meterengine.audit.dto.ListAuditLogsResponse;
import com.meterengine.audit.entity.AuditLog;
import com.meterengine.global.database.DatabaseScope;
import com.meterengine.global.database.DatabaseScopes;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.InvalidRequestException;
import com.meterengine.global.security.BusinessEnvironmentContext;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
public class AuditLogQueryService {
  private static final int MIN_YEAR = 1;
  private static final int MAX_YEAR = 9999;
  private final JdbcTemplate jdbcTemplate;
  private final JsonMapper jsonMapper;

  public AuditLogQueryService(JdbcTemplate jdbcTemplate, JsonMapper jsonMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.jsonMapper = jsonMapper;
  }

  @Transactional(readOnly = true)
  public ListAuditLogsResponse list(
      UUID organizationId,
      int page,
      int size,
      String action,
      String actorType,
      String q,
      String from,
      String to,
      String sort) {
    OffsetDateTime start = boundary(from, false);
    OffsetDateTime end = boundary(to, true);
    if (start != null && end != null && !start.isBefore(end))
      throw new InvalidRequestException(ErrorCode.VALIDATION_ERROR);
    var filters = new ArrayList<String>();
    var arguments = new ArrayList<Object>();
    if (organizationId != null) {
      filters.add("organization_id = ?");
      arguments.add(organizationId);
      String wideActions =
          String.join(
              ",",
              java.util.Collections.nCopies(AuditLogService.ORGANIZATION_WIDE_ACTIONS.size(), "?"));
      filters.add("(environment_id = ? OR action IN (" + wideActions + "))");
      arguments.add(BusinessEnvironmentContext.environmentId());
      arguments.addAll(AuditLogService.ORGANIZATION_WIDE_ACTIONS);
    }
    if (action != null) {
      filters.add("action = ?");
      arguments.add(action);
    }
    if (actorType != null) {
      filters.add("actor_type = ?");
      arguments.add(actorType);
    }
    if (start != null) {
      filters.add("occurred_at >= ?");
      arguments.add(start);
    }
    if (end != null) {
      filters.add("occurred_at < ?");
      arguments.add(end);
    }
    if (q != null && !q.isBlank()) {
      String term =
          "%"
              + q.toLowerCase(Locale.ROOT)
                  .replace("\\", "\\\\")
                  .replace("%", "\\%")
                  .replace("_", "\\_")
              + "%";
      var fields =
          new ArrayList<>(
              List.of(
                  "action",
                  "target_type",
                  "target_id",
                  "detail->>'request_id'",
                  "detail->>'route'"));
      if (organizationId == null) fields.add("CAST(organization_id AS text)");
      filters.add(
          "("
              + String.join(
                  " OR ",
                  fields.stream()
                      .map(field -> "lower(" + field + ") LIKE ? ESCAPE E'\\\\'")
                      .toList())
              + ")");
      for (var field : fields) arguments.add(term);
    }
    String table =
        DatabaseScopes.current() == DatabaseScope.SHARED
            ? "shared.audit_log"
            : "shared.audit_for_scope";
    String where = filters.isEmpty() ? "" : " WHERE " + String.join(" AND ", filters);
    long total =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM " + table + where, Long.class, arguments.toArray());
    String direction = "occurred_at_asc".equals(sort) ? " ASC" : " DESC";
    var pageArguments = new ArrayList<>(arguments);
    pageArguments.add(size);
    pageArguments.add((long) page * size);
    var logs =
        jdbcTemplate.query(
            "SELECT * FROM "
                + table
                + where
                + " ORDER BY occurred_at"
                + direction
                + ", id"
                + direction
                + " LIMIT ? OFFSET ?",
            (rs, row) ->
                AuditLogResponse.from(
                    new AuditLog(
                        rs.getObject("id", UUID.class),
                        rs.getObject("occurred_at", OffsetDateTime.class),
                        rs.getObject("organization_id", UUID.class),
                        rs.getString("actor_type"),
                        rs.getObject("actor_id", UUID.class),
                        rs.getString("action"),
                        rs.getString("target_type"),
                        rs.getString("target_id"),
                        rs.getString("detail"),
                        rs.getObject("environment_id", UUID.class)),
                    jsonMapper),
            pageArguments.toArray());
    return new ListAuditLogsResponse(logs, total, page, size);
  }

  private static OffsetDateTime boundary(String text, boolean end) {
    if (text == null) return null;
    OffsetDateTime parsed;
    try {
      if (text.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
        LocalDate date = LocalDate.parse(text);
        parsed = (end ? date.plusDays(1) : date).atStartOfDay().atOffset(ZoneOffset.ofHours(9));
      } else {
        parsed = OffsetDateTime.parse(text);
      }
    } catch (DateTimeException exception) {
      throw new InvalidRequestException(ErrorCode.VALIDATION_ERROR);
    }
    if (parsed.getYear() < MIN_YEAR || parsed.getYear() > MAX_YEAR)
      throw new InvalidRequestException(ErrorCode.VALIDATION_ERROR);
    return parsed;
  }
}
