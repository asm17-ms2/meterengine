package com.meterengine.audit.service;

import com.meterengine.audit.dto.AuditLogResponse;
import com.meterengine.audit.dto.ListAuditLogsResponse;
import com.meterengine.audit.entity.AuditLog;
import com.meterengine.audit.repository.AuditLogRepository;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.InvalidRequestException;
import jakarta.persistence.criteria.Predicate;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
public class AuditLogQueryService {
  private final AuditLogRepository auditLogRepository;
  private final JsonMapper jsonMapper;

  public AuditLogQueryService(AuditLogRepository auditLogRepository, JsonMapper jsonMapper) {
    this.auditLogRepository = auditLogRepository;
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
    Specification<AuditLog> specification =
        (root, query, cb) -> {
          List<Predicate> filters = new ArrayList<>();
          if (organizationId != null) {
            filters.add(cb.equal(root.get("organizationId"), organizationId));
          }
          if (action != null) filters.add(cb.equal(root.get("action"), action));
          if (actorType != null) filters.add(cb.equal(root.get("actorType"), actorType));
          if (start != null) filters.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), start));
          if (end != null) filters.add(cb.lessThan(root.get("occurredAt"), end));
          if (q != null && !q.isBlank()) {
            String term =
                "%"
                    + q.toLowerCase(Locale.ROOT)
                        .replace("\\", "\\\\")
                        .replace("%", "\\%")
                        .replace("_", "\\_")
                    + "%";
            filters.add(
                cb.or(
                    cb.like(cb.lower(root.get("action")), term, '\\'),
                    cb.like(cb.lower(root.get("targetType")), term, '\\'),
                    cb.like(cb.lower(root.get("targetId")), term, '\\'),
                    cb.like(
                        cb.lower(
                            cb.function(
                                "jsonb_extract_path_text",
                                String.class,
                                root.get("detail"),
                                cb.literal("request_id"))),
                        term,
                        '\\'),
                    cb.like(
                        cb.lower(
                            cb.function(
                                "jsonb_extract_path_text",
                                String.class,
                                root.get("detail"),
                                cb.literal("route"))),
                        term,
                        '\\')));
          }
          return cb.and(filters.toArray(Predicate[]::new));
        };
    Sort.Direction direction =
        "occurred_at_asc".equals(sort) ? Sort.Direction.ASC : Sort.Direction.DESC;
    Page<AuditLog> result =
        auditLogRepository.findAll(
            specification, PageRequest.of(page, size, Sort.by(direction, "occurredAt", "id")));
    return new ListAuditLogsResponse(
        result.getContent().stream().map(log -> AuditLogResponse.from(log, jsonMapper)).toList(),
        result.getTotalElements(),
        page,
        size);
  }

  private static OffsetDateTime boundary(String text, boolean end) {
    if (text == null) return null;
    try {
      if (text.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
        LocalDate date = LocalDate.parse(text);
        return (end ? date.plusDays(1) : date).atStartOfDay().atOffset(ZoneOffset.ofHours(9));
      }
      return OffsetDateTime.parse(text);
    } catch (DateTimeException exception) {
      throw new InvalidRequestException(ErrorCode.VALIDATION_ERROR);
    }
  }
}
