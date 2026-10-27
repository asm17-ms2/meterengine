package com.meterengine.audit.service;

import com.meterengine.audit.entity.AuditLog;
import com.meterengine.audit.repository.AuditLogRepository;
import com.meterengine.global.security.AuthenticatedPrincipal;
import com.meterengine.global.security.RequestAuditRecorder;
import com.meterengine.global.security.RequestAuditTarget;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
public class RequestAuditService implements RequestAuditRecorder {
  private final AuditLogRepository auditLogRepository;
  private final JsonMapper jsonMapper;

  public RequestAuditService(AuditLogRepository auditLogRepository, JsonMapper jsonMapper) {
    this.auditLogRepository = auditLogRepository;
    this.jsonMapper = jsonMapper;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(
      AuthenticatedPrincipal principal,
      String method,
      String route,
      int status,
      long durationMillis,
      UUID requestId,
      RequestAuditTarget target) {
    AuditActor actor =
        principal == null ? new AuditActor("anonymous", null, null) : AuditActor.from(principal);
    Map<String, Object> detail = new LinkedHashMap<>();
    detail.put("method", method);
    detail.put("route", route);
    detail.put("status", status);
    detail.put("duration_ms", durationMillis);
    detail.put("request_id", requestId.toString());
    if (!target.idByVariable().isEmpty()) detail.put("path_ids", target.idByVariable());
    if (target.mode() != null) detail.put("mode", target.mode());
    auditLogRepository.save(
        new AuditLog(
            UUID.randomUUID(),
            OffsetDateTime.now(),
            actor.organizationId(),
            actor.type(),
            actor.id(),
            "api_request_completed",
            "http_request",
            target.id(),
            jsonMapper.writeValueAsString(detail)));
  }
}
