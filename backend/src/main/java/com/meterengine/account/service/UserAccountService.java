package com.meterengine.account.service;

import com.meterengine.account.entity.UserAccount;
import com.meterengine.account.repository.UserAccountRepository;
import com.meterengine.audit.service.AuditActor;
import com.meterengine.audit.service.AuditLogService;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.NotFoundException;
import com.meterengine.global.security.AuthenticatedPrincipal;
import com.meterengine.organization.entity.Organization;
import com.meterengine.organization.entity.OrganizationMember;
import com.meterengine.organization.repository.OrganizationMemberRepository;
import com.meterengine.organization.repository.OrganizationRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAccountService {

  private static final String TARGET_TYPE = "user_account";

  private final UserAccountRepository userAccountRepository;
  private final OrganizationRepository organizationRepository;
  private final OrganizationMemberRepository organizationMemberRepository;
  private final AuditLogService auditLogService;

  UserAccountService(
      UserAccountRepository userAccountRepository,
      OrganizationRepository organizationRepository,
      OrganizationMemberRepository organizationMemberRepository,
      AuditLogService auditLogService) {
    this.userAccountRepository = userAccountRepository;
    this.organizationRepository = organizationRepository;
    this.organizationMemberRepository = organizationMemberRepository;
    this.auditLogService = auditLogService;
  }

  // 모든 사용자를 만든 순서로, 각자 소속된 도입사를 소속된 순서로 담아 돌려준다.
  @Transactional(readOnly = true)
  public List<UserAccountMemberships> list() {
    Map<UUID, Organization> organizationsById =
        organizationRepository.findAll().stream()
            .collect(Collectors.toMap(Organization::getId, Function.identity()));
    Map<UUID, List<Organization>> organizationsByUserAccountId =
        organizationMemberRepository.findByOrderByCreatedAtAscOrganizationIdAsc().stream()
            .collect(
                Collectors.groupingBy(
                    OrganizationMember::getUserAccountId,
                    Collectors.mapping(
                        member -> organizationsById.get(member.getOrganizationId()),
                        Collectors.toList())));
    return userAccountRepository.findByOrderByCreatedAtAscIdAsc().stream()
        .map(
            userAccount ->
                new UserAccountMemberships(
                    userAccount,
                    organizationsByUserAccountId.getOrDefault(userAccount.getId(), List.of())))
        .toList();
  }

  // 사용자의 역할을 바꾸고 감사 행은 도입사 없이 운영자가 한 일로 남긴다.
  @Transactional
  public UserAccountMemberships update(
      AuthenticatedPrincipal principal, UUID userAccountId, String role) {
    UserAccount userAccount =
        userAccountRepository
            .findById(userAccountId)
            .orElseThrow(() -> new NotFoundException(ErrorCode.USER_ACCOUNT_NOT_FOUND));
    String previousRole = userAccount.getRole();
    userAccount.changeRole(role);
    auditLogService.record(
        AuditActor.from(principal).withOrganizationId(null),
        "user_account_role_updated",
        TARGET_TYPE,
        userAccount.getId().toString(),
        Map.of("from", previousRole, "to", role));
    return new UserAccountMemberships(userAccount, organizationsOf(userAccount.getId()));
  }

  private List<Organization> organizationsOf(UUID userAccountId) {
    List<UUID> organizationIds =
        organizationMemberRepository
            .findByUserAccountIdOrderByCreatedAtAscOrganizationIdAsc(userAccountId)
            .stream()
            .map(OrganizationMember::getOrganizationId)
            .toList();
    Map<UUID, Organization> organizationsById =
        organizationRepository.findAllById(organizationIds).stream()
            .collect(Collectors.toMap(Organization::getId, Function.identity()));
    return organizationIds.stream().map(organizationsById::get).toList();
  }
}
