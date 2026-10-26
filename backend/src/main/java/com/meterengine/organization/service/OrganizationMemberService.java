package com.meterengine.organization.service;

import com.meterengine.account.repository.UserAccountRepository;
import com.meterengine.audit.service.AuditActor;
import com.meterengine.audit.service.AuditLogService;
import com.meterengine.global.error.ConflictException;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.NotFoundException;
import com.meterengine.global.security.AuthenticatedPrincipal;
import com.meterengine.organization.entity.OrganizationMember;
import com.meterengine.organization.entity.OrganizationMemberId;
import com.meterengine.organization.repository.OrganizationMemberRepository;
import com.meterengine.organization.repository.OrganizationRepository;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationMemberService {

  private final OrganizationRepository organizationRepository;
  private final OrganizationMemberRepository organizationMemberRepository;
  private final UserAccountRepository userAccountRepository;
  private final AuditLogService auditLogService;

  OrganizationMemberService(
      OrganizationRepository organizationRepository,
      OrganizationMemberRepository organizationMemberRepository,
      UserAccountRepository userAccountRepository,
      AuditLogService auditLogService) {
    this.organizationRepository = organizationRepository;
    this.organizationMemberRepository = organizationMemberRepository;
    this.userAccountRepository = userAccountRepository;
    this.auditLogService = auditLogService;
  }

  // 사용자를 도입사에 소속시키고 감사 행은 운영자가 대상 도입사에 한 일로 남긴다.
  @Transactional
  public OrganizationMember create(
      AuthenticatedPrincipal principal, UUID organizationId, UUID userAccountId) {
    if (!organizationRepository.existsById(organizationId)) {
      throw new NotFoundException(ErrorCode.ORGANIZATION_NOT_FOUND);
    }
    if (!userAccountRepository.existsById(userAccountId)) {
      throw new NotFoundException(ErrorCode.USER_ACCOUNT_NOT_FOUND);
    }
    if (organizationMemberRepository.existsById(
        new OrganizationMemberId(organizationId, userAccountId))) {
      throw new ConflictException(ErrorCode.ORGANIZATION_MEMBER_ALREADY_EXISTS);
    }
    OrganizationMember member =
        organizationMemberRepository.save(
            new OrganizationMember(
                organizationId,
                userAccountId,
                OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS)));
    auditLogService.record(
        AuditActor.from(principal).withOrganizationId(organizationId),
        "organization_member_created",
        "user_account",
        userAccountId.toString(),
        Map.of());
    return member;
  }
}
