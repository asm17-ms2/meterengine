package com.meterengine.organization.service;

import com.meterengine.audit.service.AuditActor;
import com.meterengine.audit.service.AuditLogService;
import com.meterengine.global.error.ConflictException;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.NotFoundException;
import com.meterengine.global.security.AccessScopes;
import com.meterengine.global.security.AuthenticatedPrincipal;
import com.meterengine.organization.api.OrganizationAccounts;
import com.meterengine.organization.entity.OrganizationMember;
import com.meterengine.organization.entity.OrganizationMemberId;
import com.meterengine.organization.repository.OrganizationMemberRepository;
import com.meterengine.organization.repository.OrganizationRepository;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationMemberService {

  private final OrganizationRepository organizationRepository;
  private final OrganizationMemberRepository organizationMemberRepository;
  private final OrganizationAccounts organizationAccounts;
  private final AuditLogService auditLogService;

  OrganizationMemberService(
      OrganizationRepository organizationRepository,
      OrganizationMemberRepository organizationMemberRepository,
      OrganizationAccounts organizationAccounts,
      AuditLogService auditLogService) {
    this.organizationRepository = organizationRepository;
    this.organizationMemberRepository = organizationMemberRepository;
    this.organizationAccounts = organizationAccounts;
    this.auditLogService = auditLogService;
  }

  // 사용자를 도입사에 소속시키고 감사 행은 운영자가 대상 도입사에 한 일로 남긴다.
  @Transactional
  public OrganizationMember create(
      AuthenticatedPrincipal principal, UUID organizationId, UUID userAccountId) {
    return create(principal, organizationId, userAccountId, AccessScopes.READ);
  }

  @Transactional
  public OrganizationMember create(
      AuthenticatedPrincipal principal,
      UUID organizationId,
      UUID userAccountId,
      List<String> scopes) {
    List<String> normalized = AccessScopes.normalize(scopes);
    organizationRepository
        .findForUpdateById(organizationId)
        .orElseThrow(() -> new NotFoundException(ErrorCode.ORGANIZATION_NOT_FOUND));
    if (!organizationAccounts.exists(userAccountId)) {
      throw new NotFoundException(ErrorCode.USER_ACCOUNT_NOT_FOUND);
    }
    OrganizationMember member =
        organizationMemberRepository
            .findById(new OrganizationMemberId(organizationId, userAccountId))
            .orElse(null);
    if (member != null && member.getRevokedAt() == null)
      throw new ConflictException(ErrorCode.ORGANIZATION_MEMBER_ALREADY_EXISTS);
    if (member == null)
      member =
          organizationMemberRepository.save(
              new OrganizationMember(
                  organizationId,
                  userAccountId,
                  OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS),
                  normalized));
    else member.restore(normalized);
    member.changeScopes(normalized);
    if (organizationMemberRepository.countByOrganizationIdAndRoleAndRevokedAtIsNull(
            organizationId, "owner")
        == 0) {
      member.changeRole("owner");
    }
    auditLogService.record(
        AuditActor.from(principal).withOrganizationId(organizationId),
        "organization_member_created",
        "user_account",
        userAccountId.toString(),
        Map.of("scopes", member.getScopes(), "organization_role", member.getRole()));
    return member;
  }
}
