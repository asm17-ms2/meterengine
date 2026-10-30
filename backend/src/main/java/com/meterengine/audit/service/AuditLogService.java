package com.meterengine.audit.service;

import com.meterengine.audit.entity.AuditLog;
import com.meterengine.audit.repository.AuditLogRepository;
import com.meterengine.global.security.AuthenticatedPrincipal;
import com.meterengine.global.security.BusinessEnvironment;
import com.meterengine.global.security.BusinessEnvironmentContext;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

@Service
public class AuditLogService {

  // 계정, 소속, 초대와 세션은 환경과 무관한 도입사 공통 작업이라 감사에 환경을 붙이지 않는다.
  static final Set<String> ORGANIZATION_WIDE_ACTIONS =
      Set.of(
          "organization_status_updated",
          "organization_member_created",
          "organization_member_role_updated",
          "organization_member_scopes_updated",
          "organization_member_archived",
          "organization_invitation_created",
          "organization_invitation_revoked",
          "organization_invitation_accepted",
          "session_created",
          "session_deleted",
          "session_organization_updated",
          "signup_created",
          "user_account_created",
          "user_account_role_updated",
          "user_account_password_updated",
          "user_account_consents_accepted");

  private final AuditLogRepository auditLogRepository;
  private final JsonMapper jsonMapper;

  AuditLogService(AuditLogRepository auditLogRepository, JsonMapper jsonMapper) {
    this.auditLogRepository = auditLogRepository;
    this.jsonMapper = jsonMapper;
  }

  // 요청 주체를 행위자로 남기고, 주체가 없으면 시스템이 한 일로 남긴다.
  public void record(
      String action, String targetType, String targetId, Map<String, Object> detail) {
    record(currentActor(AuditActor.system()), action, targetType, targetId, detail);
  }

  // 감사 행의 도입사는 넘긴 도입사로 두고, 요청 주체가 있으면 주체를, 없으면 시스템을 행위자로 남긴다.
  public void record(
      UUID organizationId,
      String action,
      String targetType,
      String targetId,
      Map<String, Object> detail) {
    AuditActor actor =
        currentActor(AuditActor.system(organizationId)).withOrganizationId(organizationId);
    record(actor, action, targetType, targetId, detail);
  }

  public void record(
      AuditActor actor,
      String action,
      String targetType,
      String targetId,
      Map<String, Object> detail) {
    BusinessEnvironment environment = BusinessEnvironmentContext.current();
    boolean isScoped =
        environment != null
            && !ORGANIZATION_WIDE_ACTIONS.contains(action)
            && environment.organizationId().equals(actor.organizationId());
    record(
        actor, action, targetType, targetId, detail, isScoped ? environment.environmentId() : null);
  }

  void record(
      AuditActor actor,
      String action,
      String targetType,
      String targetId,
      Map<String, Object> detail,
      UUID environmentId) {
    auditLogRepository.save(
        new AuditLog(
            UUID.randomUUID(),
            OffsetDateTime.now(),
            actor.organizationId(),
            actor.type(),
            actor.id(),
            action,
            targetType,
            targetId,
            jsonMapper.writeValueAsString(detail == null ? Map.of() : detail),
            environmentId));
  }

  private static AuditActor currentActor(AuditActor fallback) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null
        && authentication.getPrincipal() instanceof AuthenticatedPrincipal principal) {
      return AuditActor.from(principal);
    }
    return fallback;
  }
}
