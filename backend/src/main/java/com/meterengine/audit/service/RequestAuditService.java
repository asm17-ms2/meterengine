package com.meterengine.audit.service;

import com.meterengine.global.security.AuthenticatedPrincipal;
import com.meterengine.global.security.RequestAuditRecorder;
import com.meterengine.global.security.RequestAuditTarget;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RequestAuditService implements RequestAuditRecorder {
  private final AuditLogService auditLogService;

  public RequestAuditService(AuditLogService auditLogService) {
    this.auditLogService = auditLogService;
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
    AuditActor actor = principal == null ? AuditActor.anonymous() : AuditActor.from(principal);
    Map<String, Object> detail = new LinkedHashMap<>();
    detail.put("method", method);
    detail.put("route", route);
    detail.put("status", status);
    detail.put("duration_ms", durationMillis);
    detail.put("request_id", requestId.toString());
    if (principal != null && principal.environmentId() != null)
      detail.put("environment_id", principal.environmentId().toString());
    if (!target.idByVariable().isEmpty()) detail.put("path_ids", target.idByVariable());
    if (target.mode() != null) detail.put("mode", target.mode());
    auditLogService.record(
        actor,
        "api_request_completed",
        "http_request",
        target.id(),
        detail,
        principal == null ? null : principal.environmentId());
  }
}
