package com.meterengine.event.repository;

import com.meterengine.event.dto.EventIngestMessage;
import com.meterengine.event.dto.EventRejectionResponse;
import com.meterengine.event.dto.EventRejectionsResponse;
import com.meterengine.event.service.EventRejection;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.security.AuthenticatedPrincipal;
import com.meterengine.global.security.BusinessEnvironmentContext;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class EventRejectionRepository {
  private static final TypeReference<List<String>> FIELDS = new TypeReference<>() {};
  private final JdbcTemplate jdbcTemplate;
  private final JsonMapper jsonMapper;

  public EventRejectionRepository(JdbcTemplate jdbcTemplate, JsonMapper jsonMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.jsonMapper = jsonMapper;
  }

  public void insert(
      UUID organizationId,
      AuthenticatedPrincipal principal,
      UUID requestId,
      List<EventRejection> rejections) {
    if (rejections.isEmpty()) return;
    String actorType =
        principal == null
            ? "system"
            : switch (principal.kind()) {
              case API_KEY -> "api_key";
              case SESSION -> "user_account";
            };
    UUID actorId =
        principal == null
            ? null
            : switch (principal.kind()) {
              case API_KEY -> principal.apiKeyId();
              case SESSION -> principal.userAccountId();
            };
    List<Object> values = new ArrayList<>();
    for (EventRejection rejection : rejections) {
      values.add(organizationId);
      values.add(BusinessEnvironmentContext.environmentId());
      values.add(rejection.transactionId());
      values.add(rejection.customerId());
      values.add(rejection.type());
      values.add(rejection.occurredAt());
      values.add(rejection.errorCode().getCode());
      values.add(jsonMapper.writeValueAsString(rejection.errorFields()));
      values.add(requestId);
      values.add(actorType);
      values.add(actorId);
      values.add(rejection.batchIndex());
    }
    jdbcTemplate.update(
        "INSERT INTO event_rejection (organization_id,environment_id,transaction_id,customer_id,type,occurred_at,error_code,error_fields,request_id,actor_type,actor_id,batch_index) VALUES "
            + String.join(
                ",",
                java.util.Collections.nCopies(
                    rejections.size(), "(?,?,?,?,?,?,?,?::jsonb,?,?,?,?)")),
        values.toArray());
  }

  // 컨슈머는 같은 메시지를 다시 읽을 수 있어, 수집 서버가 받은 시각까지 같은 기록이 있으면 넣지 않는다.
  // 도입사가 다시 보낸 이벤트는 받은 시각이 달라 새 행이 된다. 넣었으면 true다.
  public boolean insertMessageIfAbsent(EventIngestMessage message, ErrorCode errorCode) {
    OffsetDateTime acceptedAt = OffsetDateTime.ofInstant(message.acceptedAt(), ZoneOffset.UTC);
    List<Object> parameters = new ArrayList<>();
    parameters.add(message.organizationId());
    parameters.add(message.environmentId());
    parameters.add(message.transactionId());
    parameters.add(message.customerId());
    parameters.add(message.type());
    parameters.add(message.occurredAt());
    parameters.add(errorCode.getCode());
    parameters.add(message.requestId());
    parameters.add(message.actorType());
    parameters.add(message.actorId());
    parameters.add(acceptedAt);
    parameters.add(message.organizationId());
    if (message.environmentId() != null) parameters.add(message.environmentId());
    parameters.add(message.transactionId());
    parameters.add(errorCode.getCode());
    parameters.add(acceptedAt);
    int inserted =
        jdbcTemplate.update(
            "INSERT INTO event_rejection (organization_id,environment_id,transaction_id,customer_id,type,occurred_at,error_code,request_id,actor_type,actor_id,recorded_at)"
                + " SELECT ?::uuid,?::uuid,?,?::uuid,?,?::timestamptz,?,?::uuid,?,?::uuid,?::timestamptz"
                + " WHERE NOT EXISTS (SELECT 1 FROM event_rejection WHERE organization_id=?::uuid AND "
                + (message.environmentId() == null
                    ? "environment_id IS NULL"
                    : "environment_id=?::uuid")
                + " AND transaction_id=? AND error_code=? AND recorded_at=?::timestamptz)",
            parameters.toArray());
    return inserted > 0;
  }

  public EventRejectionsResponse search(
      UUID organizationId,
      YearMonth month,
      OffsetDateTime start,
      OffsetDateTime end,
      UUID customerId,
      String errorCode,
      String query,
      int page,
      int size) {
    UUID environmentId = BusinessEnvironmentContext.environmentId();
    StringBuilder where =
        new StringBuilder(
            environmentId == null
                ? " WHERE organization_id=? AND environment_id IS NULL AND recorded_at>=? AND recorded_at<?"
                : " WHERE organization_id=? AND environment_id=?::uuid AND recorded_at>=? AND recorded_at<?");
    List<Object> parameters = new ArrayList<>();
    parameters.add(organizationId);
    if (environmentId != null) parameters.add(environmentId);
    parameters.add(start);
    parameters.add(end);
    if (customerId != null) {
      where.append(" AND customer_id=?");
      parameters.add(customerId);
    }
    if (errorCode != null) {
      where.append(" AND error_code=?");
      parameters.add(errorCode);
    }
    if (query != null && !query.isBlank()) {
      where.append(
          " AND (position(lower(?) in lower(coalesce(transaction_id,''))) > 0 OR position(lower(?) in lower(coalesce(type,''))) > 0)");
      parameters.add(query);
      parameters.add(query);
    }
    Long total =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM event_rejection" + where, Long.class, parameters.toArray());
    parameters.add(size);
    parameters.add(Math.multiplyExact((long) page, size));
    List<EventRejectionResponse> rows =
        jdbcTemplate.query(
            "SELECT * FROM event_rejection"
                + where
                + " ORDER BY recorded_at DESC, id DESC LIMIT ? OFFSET ?",
            (rs, index) ->
                new EventRejectionResponse(
                    rs.getObject("id", UUID.class),
                    rs.getString("transaction_id"),
                    rs.getObject("customer_id", UUID.class),
                    rs.getString("type"),
                    rs.getObject("occurred_at", OffsetDateTime.class),
                    rs.getString("error_code"),
                    jsonMapper.readValue(rs.getString("error_fields"), FIELDS),
                    rs.getObject("request_id", UUID.class),
                    (Integer) rs.getObject("batch_index"),
                    rs.getObject("recorded_at", OffsetDateTime.class)),
            parameters.toArray());
    OffsetDateTime started =
        jdbcTemplate.queryForObject(
            "SELECT started_at FROM event_rejection_recording_start", OffsetDateTime.class);
    return new EventRejectionsResponse(
        month.toString(), started, page, size, total == null ? 0 : total, rows);
  }
}
