package com.meterengine.organization.service;

import com.meterengine.audit.service.AuditActor;
import com.meterengine.audit.service.AuditLogService;
import com.meterengine.global.error.ConflictException;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.ForbiddenException;
import com.meterengine.global.error.InvalidRequestException;
import com.meterengine.global.error.NotFoundException;
import com.meterengine.global.security.AccessScopes;
import com.meterengine.global.security.AuthenticatedPrincipal;
import com.meterengine.global.security.PrincipalKind;
import com.meterengine.global.security.SecureTokens;
import com.meterengine.global.validation.ArchiveStatus;
import com.meterengine.organization.api.MembershipConsent;
import com.meterengine.organization.api.OrganizationAccounts;
import com.meterengine.organization.api.OrganizationPerson;
import com.meterengine.organization.dto.AcceptOrganizationInvitationRequest;
import com.meterengine.organization.dto.CreateManagedMemberRequest;
import com.meterengine.organization.dto.ManagedMemberResponse;
import com.meterengine.organization.dto.OrganizationInvitationAcceptanceResponse;
import com.meterengine.organization.dto.OrganizationInvitationPreviewResponse;
import com.meterengine.organization.dto.OrganizationInvitationResponse;
import com.meterengine.organization.dto.OrganizationInvitationSecretResponse;
import com.meterengine.organization.entity.Organization;
import com.meterengine.organization.entity.OrganizationInvitation;
import com.meterengine.organization.entity.OrganizationMember;
import com.meterengine.organization.entity.OrganizationMemberId;
import com.meterengine.organization.repository.OrganizationInvitationRepository;
import com.meterengine.organization.repository.OrganizationMemberRepository;
import com.meterengine.organization.repository.OrganizationRepository;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationPeopleService {
  private final OrganizationRepository organizationRepository;
  private final OrganizationMemberRepository organizationMemberRepository;
  private final OrganizationInvitationRepository organizationInvitationRepository;
  private final AuditLogService auditLogService;
  private final OrganizationAccounts organizationAccounts;

  public OrganizationPeopleService(
      OrganizationRepository organizationRepository,
      OrganizationMemberRepository organizationMemberRepository,
      OrganizationInvitationRepository organizationInvitationRepository,
      OrganizationAccounts organizationAccounts,
      AuditLogService auditLogService) {
    this.organizationRepository = organizationRepository;
    this.organizationMemberRepository = organizationMemberRepository;
    this.organizationInvitationRepository = organizationInvitationRepository;
    this.organizationAccounts = organizationAccounts;
    this.auditLogService = auditLogService;
  }

  @Transactional(readOnly = true)
  public List<ManagedMemberResponse> list(AuthenticatedPrincipal principal, UUID organizationId) {
    return list(principal, organizationId, "all");
  }

  @Transactional(readOnly = true)
  public List<ManagedMemberResponse> list(
      AuthenticatedPrincipal principal, UUID organizationId, String status) {
    requireMember(principal, organizationId, false);
    ArchiveStatus filter = ArchiveStatus.parse(status);
    return organizationMemberRepository
        .findByOrganizationIdOrderByCreatedAtAscUserAccountIdAsc(organizationId)
        .stream()
        .filter(member -> filter.includes(member.getRevokedAt()))
        .map(m -> ManagedMemberResponse.from(m, organizationAccounts.get(m.getUserAccountId())))
        .toList();
  }

  @Transactional
  public ManagedMemberResponse create(
      AuthenticatedPrincipal principal, UUID organizationId, CreateManagedMemberRequest request) {
    lockOwner(principal, organizationId);
    OrganizationPerson user =
        organizationAccounts.create(request.email(), request.name(), request.password());
    OrganizationMember member =
        organizationMemberRepository.save(
            new OrganizationMember(organizationId, user.id(), now(), request.scopes()));
    auditLogService.record(
        AuditActor.from(principal).withOrganizationId(organizationId),
        "organization_member_created",
        "user_account",
        user.id().toString(),
        Map.of("scopes", member.getScopes()));
    return ManagedMemberResponse.from(member, user);
  }

  @Transactional
  public ManagedMemberResponse update(
      AuthenticatedPrincipal principal, UUID organizationId, UUID userId, String role) {
    lockOwner(principal, organizationId);
    OrganizationMember member = member(organizationId, userId);
    protectOwner(member, role);
    String previous = member.getRole();
    List<String> previousScopes = member.getScopes();
    member.changeRole(role);
    auditLogService.record(
        AuditActor.from(principal).withOrganizationId(organizationId),
        "organization_member_role_updated",
        "user_account",
        userId.toString(),
        Map.of(
            "from",
            previous,
            "to",
            role,
            "from_scopes",
            previousScopes,
            "to_scopes",
            member.getScopes()));
    return ManagedMemberResponse.from(member, organizationAccounts.get(userId));
  }

  @Transactional
  public ManagedMemberResponse update(
      AuthenticatedPrincipal principal, UUID organizationId, UUID userId, List<String> scopes) {
    lockOwner(principal, organizationId);
    OrganizationMember member = member(organizationId, userId);
    List<String> normalized = AccessScopes.normalize(scopes);
    if ("owner".equals(member.getRole()) && !normalized.contains("admin"))
      throw new ConflictException(ErrorCode.OWNER_SCOPES_REQUIRED);
    List<String> previous = member.getScopes();
    member.changeScopes(normalized);
    auditLogService.record(
        AuditActor.from(principal).withOrganizationId(organizationId),
        "organization_member_scopes_updated",
        "user_account",
        userId.toString(),
        Map.of("from", previous, "to", member.getScopes()));
    return ManagedMemberResponse.from(member, organizationAccounts.get(userId));
  }

  @Transactional
  public void remove(AuthenticatedPrincipal principal, UUID organizationId, UUID userId) {
    lockOwner(principal, organizationId);
    OrganizationMember member = member(organizationId, userId);
    protectOwner(member, null);
    OffsetDateTime now = now();
    organizationAccounts.revoke(organizationId, userId, now);
    member.revoke(now);
    organizationInvitationRepository.revokePending(
        organizationId, organizationAccounts.get(userId).email(), now);
    organizationMemberRepository.flush();
    record(principal, organizationId, "organization_member_archived", "user_account", userId);
  }

  @Transactional(readOnly = true)
  public List<OrganizationInvitationResponse> invitations(
      AuthenticatedPrincipal principal, UUID organizationId) {
    requireMember(principal, organizationId, true);
    return organizationInvitationRepository
        .findByOrganizationIdOrderByCreatedAtDescIdDesc(organizationId)
        .stream()
        .map(OrganizationInvitationResponse::from)
        .toList();
  }

  @Transactional
  public OrganizationInvitationSecretResponse invite(
      AuthenticatedPrincipal principal, UUID organizationId, String email) {
    return invite(principal, organizationId, email, AccessScopes.READ);
  }

  @Transactional
  public OrganizationInvitationSecretResponse invite(
      AuthenticatedPrincipal principal, UUID organizationId, String email, List<String> scopes) {
    lockOwner(principal, organizationId);
    organizationAccounts
        .find(email)
        .ifPresent(
            user -> {
              if (organizationMemberRepository
                  .findById(new OrganizationMemberId(organizationId, user.id()))
                  .filter(m -> m.getRevokedAt() == null)
                  .isPresent())
                throw new ConflictException(ErrorCode.ORGANIZATION_MEMBER_ALREADY_EXISTS);
            });
    String token = SecureTokens.generate("me_invite_");
    OffsetDateTime now = now();
    OrganizationInvitation invitation =
        organizationInvitationRepository.save(
            new OrganizationInvitation(
                UUID.randomUUID(),
                organizationId,
                email.toLowerCase(Locale.ROOT),
                SecureTokens.hash(token),
                now,
                now.plusHours(48),
                scopes));
    auditLogService.record(
        AuditActor.from(principal).withOrganizationId(organizationId),
        "organization_invitation_created",
        "organization_invitation",
        invitation.getId().toString(),
        Map.of("scopes", invitation.getScopes()));
    return new OrganizationInvitationSecretResponse(
        OrganizationInvitationResponse.from(invitation), token);
  }

  @Transactional
  public void revoke(AuthenticatedPrincipal principal, UUID organizationId, UUID id) {
    lockOwner(principal, organizationId);
    OrganizationInvitation invitation =
        organizationInvitationRepository
            .findForUpdateById(id)
            .filter(i -> i.getOrganizationId().equals(organizationId))
            .orElseThrow(() -> new NotFoundException(ErrorCode.INVITATION_NOT_FOUND));
    usable(invitation);
    invitation.revoke(now());
    record(
        principal,
        organizationId,
        "organization_invitation_revoked",
        "organization_invitation",
        id);
  }

  @Transactional(readOnly = true)
  public OrganizationInvitationPreviewResponse preview(String token) {
    OrganizationInvitation invitation = byToken(token);
    usable(invitation);
    Organization organization = activeOrganization(invitation.getOrganizationId(), false);
    return new OrganizationInvitationPreviewResponse(
        organization.getName(),
        invitation.getEmail(),
        invitation.getExpiresAt(),
        organizationAccounts.find(invitation.getEmail()).isPresent(),
        invitation.getScopes());
  }

  @Transactional
  public OrganizationInvitationAcceptanceResponse accept(
      String token, AuthenticatedPrincipal principal, AcceptOrganizationInvitationRequest request) {
    byte[] tokenHash = SecureTokens.hash(token);
    UUID organizationId =
        organizationInvitationRepository
            .findOrganizationIdByTokenHash(tokenHash)
            .orElseThrow(() -> new NotFoundException(ErrorCode.INVITATION_NOT_FOUND));
    activeOrganization(organizationId, true);
    OrganizationInvitation invitation =
        organizationInvitationRepository
            .findForUpdateByTokenHash(tokenHash)
            .orElseThrow(() -> new NotFoundException(ErrorCode.INVITATION_NOT_FOUND));
    usable(invitation);
    OrganizationPerson user = organizationAccounts.find(invitation.getEmail()).orElse(null);
    if (user != null) {
      if (principal == null
          || principal.kind() != PrincipalKind.SESSION
          || !user.id().equals(principal.userAccountId()))
        throw new ForbiddenException(ErrorCode.PERMISSION_DENIED);
      if (user.consentRequired()) throw new ForbiddenException(ErrorCode.CONSENT_REQUIRED);
    } else {
      if (request == null
          || request.name() == null
          || request.name().isBlank()
          || request.password() == null
          || request.password().length() < 8
          || request.consents() == null)
        throw new InvalidRequestException(ErrorCode.VALIDATION_ERROR);
      List<MembershipConsent> accepted =
          request.consents().stream()
              .map(c -> new MembershipConsent(c.document(), c.documentVersion()))
              .toList();
      user =
          organizationAccounts.register(
              invitation.getEmail(), request.name(), request.password(), accepted);
    }
    OrganizationMember existing =
        organizationMemberRepository
            .findById(new OrganizationMemberId(invitation.getOrganizationId(), user.id()))
            .orElse(null);
    if (existing != null && existing.getRevokedAt() == null)
      throw new ConflictException(ErrorCode.ORGANIZATION_MEMBER_ALREADY_EXISTS);
    if (existing != null) existing.restore(invitation.getScopes());
    else {
      OrganizationMember joined =
          new OrganizationMember(invitation.getOrganizationId(), user.id(), now());
      joined.changeScopes(invitation.getScopes());
      organizationMemberRepository.save(joined);
    }
    invitation.accept(now());
    auditLogService.record(
        AuditActor.userAccount(user.id(), invitation.getOrganizationId()),
        "organization_invitation_accepted",
        "organization_invitation",
        invitation.getId().toString(),
        Map.of("scopes", invitation.getScopes()));
    return new OrganizationInvitationAcceptanceResponse(user.id(), invitation.getOrganizationId());
  }

  private OrganizationInvitation byToken(String token) {
    return organizationInvitationRepository
        .findByTokenHash(SecureTokens.hash(token))
        .orElseThrow(() -> new NotFoundException(ErrorCode.INVITATION_NOT_FOUND));
  }

  private void usable(OrganizationInvitation invitation) {
    if (!invitation.isUsable(now())) throw new ConflictException(ErrorCode.INVITATION_UNAVAILABLE);
  }

  private void lockOwner(AuthenticatedPrincipal principal, UUID organizationId) {
    activeOrganization(organizationId, true);
    requireMember(principal, organizationId, true);
  }

  private Organization activeOrganization(UUID organizationId, boolean lock) {
    Organization organization =
        (lock
                ? organizationRepository.findForUpdateById(organizationId)
                : organizationRepository.findById(organizationId))
            .orElseThrow(() -> new NotFoundException(ErrorCode.ORGANIZATION_NOT_FOUND));
    if (!Organization.ACTIVE.equals(organization.getStatus()))
      throw new ForbiddenException(ErrorCode.ORGANIZATION_NOT_ACTIVE);
    return organization;
  }

  private void requireMember(AuthenticatedPrincipal principal, UUID organizationId, boolean owner) {
    if (principal.kind() != PrincipalKind.SESSION
        || !organizationId.equals(principal.organizationId()))
      throw new ForbiddenException(ErrorCode.PERMISSION_DENIED);
    OrganizationMember membership =
        organizationMemberRepository
            .findById(new OrganizationMemberId(organizationId, principal.userAccountId()))
            .filter(m -> m.getRevokedAt() == null)
            .orElseThrow(() -> new ForbiddenException(ErrorCode.PERMISSION_DENIED));
    if (owner && !"owner".equals(membership.getRole()))
      throw new ForbiddenException(ErrorCode.PERMISSION_DENIED);
    if (!owner && !AccessScopes.grants(membership.getScopes(), "read"))
      throw new ForbiddenException(ErrorCode.PERMISSION_DENIED);
  }

  private OrganizationMember member(UUID organizationId, UUID userId) {
    return organizationMemberRepository
        .findById(new OrganizationMemberId(organizationId, userId))
        .filter(m -> m.getRevokedAt() == null)
        .orElseThrow(() -> new NotFoundException(ErrorCode.USER_ACCOUNT_NOT_FOUND));
  }

  private void protectOwner(OrganizationMember member, String next) {
    if ("owner".equals(member.getRole())
        && !"owner".equals(next)
        && organizationMemberRepository.countByOrganizationIdAndRoleAndRevokedAtIsNull(
                member.getOrganizationId(), "owner")
            <= 1) throw new ConflictException(ErrorCode.LAST_OWNER_REQUIRED);
  }

  private void record(
      AuthenticatedPrincipal principal,
      UUID organizationId,
      String action,
      String type,
      UUID target) {
    auditLogService.record(
        AuditActor.from(principal).withOrganizationId(organizationId),
        action,
        type,
        target.toString(),
        Map.of());
  }

  private static OffsetDateTime now() {
    return OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS);
  }
}
