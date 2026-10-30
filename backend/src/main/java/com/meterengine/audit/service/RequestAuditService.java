package com.meterengine.audit.service;

import com.meterengine.global.security.AuthenticatedPrincipal;
import com.meterengine.global.security.RequestAuditRecorder;
import com.meterengine.global.security.RequestAuditTarget;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RequestAuditService implements RequestAuditRecorder {
  private static final Set<Integer> UNATTRIBUTED_SKIPPED_STATUSES = Set.of(401, 429);
  private static final Set<String> SIGN_IN_REQUESTS =
      Set.of("POST /v1/sessions", "POST /v1/signups");
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
    if (isUnattributedRejection(principal, method, route, status)) return;
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

  // 주체 없는 401, 429는 누구나 끝없이 만들 수 있어 지울 수 없는 감사 표 대신 요청 지표와 요청 로그로 센다.
  private static boolean isUnattributedRejection(
      AuthenticatedPrincipal principal, String method, String route, int status) {
    return principal == null
        && UNATTRIBUTED_SKIPPED_STATUSES.contains(status)
        && !SIGN_IN_REQUESTS.contains(method + " " + route);
  }
}
