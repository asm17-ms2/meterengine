package com.meterengine.account.service;

import com.meterengine.account.config.SessionProperties;
import com.meterengine.account.entity.UserAccount;
import com.meterengine.account.entity.UserSession;
import com.meterengine.account.repository.UserAccountRepository;
import com.meterengine.account.repository.UserSessionRepository;
import com.meterengine.audit.service.AuditActor;
import com.meterengine.audit.service.AuditLogService;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.ForbiddenException;
import com.meterengine.global.error.UnauthorizedException;
import com.meterengine.global.security.AuthenticatedCredential;
import com.meterengine.global.security.AuthenticatedPrincipal;
import com.meterengine.global.security.PrincipalKind;
import com.meterengine.global.security.SecureTokens;
import com.meterengine.organization.entity.Organization;
import com.meterengine.organization.entity.OrganizationMember;
import com.meterengine.organization.entity.OrganizationMemberId;
import com.meterengine.organization.repository.OrganizationInvitationRepository;
import com.meterengine.organization.repository.OrganizationMemberRepository;
import com.meterengine.organization.repository.OrganizationRepository;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class SessionService {

  public static final String TARGET_TYPE = "user_session";

  public static final String DELETED_ACTION = "session_deleted";

  private static final String ACTIVE_STATUS = "active";

  private static final String OPERATOR_ROLE = "operator";

  private final UserAccountRepository userAccountRepository;
  private final UserSessionRepository userSessionRepository;
  private final OrganizationRepository organizationRepository;
  private final OrganizationMemberRepository organizationMemberRepository;
  private final PasswordEncoder passwordEncoder;
  private final SessionProperties sessionProperties;
  private final AuditLogService auditLogService;
  private final TransactionTemplate transactionTemplate;
  private final String unknownUserPasswordHash;
  private final OrganizationInvitationRepository organizationInvitationRepository;

  SessionService(
      UserAccountRepository userAccountRepository,
      UserSessionRepository userSessionRepository,
      OrganizationRepository organizationRepository,
      OrganizationMemberRepository organizationMemberRepository,
      PasswordEncoder passwordEncoder,
      SessionProperties sessionProperties,
      AuditLogService auditLogService,
      TransactionTemplate transactionTemplate) {
    this(
        userAccountRepository,
        userSessionRepository,
        organizationRepository,
        organizationMemberRepository,
        passwordEncoder,
        sessionProperties,
        auditLogService,
        transactionTemplate,
        null);
  }

  @Autowired
  SessionService(
      UserAccountRepository userAccountRepository,
      UserSessionRepository userSessionRepository,
      OrganizationRepository organizationRepository,
      OrganizationMemberRepository organizationMemberRepository,
      PasswordEncoder passwordEncoder,
      SessionProperties sessionProperties,
      AuditLogService auditLogService,
      TransactionTemplate transactionTemplate,
      OrganizationInvitationRepository organizationInvitationRepository) {
    this.userAccountRepository = userAccountRepository;
    this.userSessionRepository = userSessionRepository;
    this.organizationRepository = organizationRepository;
    this.organizationMemberRepository = organizationMemberRepository;
    this.passwordEncoder = passwordEncoder;
    this.sessionProperties = sessionProperties;
    this.auditLogService = auditLogService;
    this.transactionTemplate = transactionTemplate;
    this.unknownUserPasswordHash = passwordEncoder.encode(SecureTokens.generate(""));
    this.organizationInvitationRepository = organizationInvitationRepository;
  }

  // bcrypt 비교가 DB 커넥션을 잡지 않게 트랜잭션 밖에서 확인하고, 가장 먼저 소속된 활성 도입사로 세션을 만든다.
  public IssuedSession create(String email, String password) {
    UserAccount userAccount = verifiedUserAccount(email, password);
    List<Organization> organizations = organizationsOf(userAccount.getId());
    Organization organization =
        organizations.stream()
            .filter(candidate -> ACTIVE_STATUS.equals(candidate.getStatus()))
            .findFirst()
            .orElse(null);
    if (organization == null
        && !OPERATOR_ROLE.equals(userAccount.getRole())
        && (organizationInvitationRepository == null
            || !organizationInvitationRepository.existsByEmail(
                userAccount.getEmail(), OffsetDateTime.now()))) {
      throw new ForbiddenException(ErrorCode.ORGANIZATION_NOT_ACTIVE);
    }
    UUID organizationId = organization == null ? null : organization.getId();
    String token = SecureTokens.generate(SecureTokens.SESSION_TOKEN_PREFIX);
    OffsetDateTime now = OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS);
    UserSession session =
        transactionTemplate.execute(
            status -> {
              // 확인 뒤 첫 동의 등으로 비밀번호가 바뀌었으면 옛 비밀번호로 세션을 만들지 않는다.
              String passwordHash =
                  userAccountRepository
                      .findForUpdateById(userAccount.getId())
                      .map(UserAccount::getPasswordHash)
                      .orElse(null);
              if (!userAccount.getPasswordHash().equals(passwordHash))
                throw new UnauthorizedException(ErrorCode.INVALID_CREDENTIALS);
              UserSession saved =
                  userSessionRepository.save(
                      new UserSession(
                          UUID.randomUUID(),
                          userAccount.getId(),
                          SecureTokens.hash(token),
                          organizationId,
                          now,
                          now.plus(sessionProperties.ttl())));
              auditLogService.record(
                  AuditActor.userAccount(userAccount.getId(), organizationId),
                  "session_created",
                  TARGET_TYPE,
                  saved.getId().toString(),
                  Map.of());
              return saved;
            });
    return new IssuedSession(
        token,
        new CurrentSession(
            session,
            userAccount,
            organization,
            organizations,
            organizationRole(session),
            scopesOf(session)));
  }

  @Transactional(readOnly = true)
  public CurrentSession get(UUID userSessionId) {
    return currentSessionOf(sessionOf(userSessionId));
  }

  @Transactional
  public void delete(UUID userSessionId) {
    UserSession session = lockedSessionOf(userSessionId);
    session.revoke(OffsetDateTime.now());
    auditLogService.record(DELETED_ACTION, TARGET_TYPE, session.getId().toString(), Map.of());
  }

  // 세션의 도입사를 사용자가 소속된 도입사로 바꾸며, 활성이 아닌 도입사로도 바꿀 수 있다.
  @Transactional
  public CurrentSession update(UUID userSessionId, UUID organizationId) {
    UserSession session = lockedSessionOf(userSessionId);
    if (!session.isUsableAt(OffsetDateTime.now())) {
      throw new UnauthorizedException(ErrorCode.INVALID_CREDENTIALS);
    }
    if (organizationMemberRepository
        .findById(new OrganizationMemberId(organizationId, session.getUserAccountId()))
        .filter(member -> member.getRevokedAt() == null)
        .isEmpty()) {
      throw new ForbiddenException(ErrorCode.PERMISSION_DENIED);
    }
    session.switchOrganization(organizationId);
    auditLogService.record(
        "session_organization_updated",
        TARGET_TYPE,
        session.getId().toString(),
        Map.of("organization_id", organizationId.toString()));
    return currentSessionOf(session);
  }

  @Transactional(readOnly = true)
  public Optional<AuthenticatedCredential> authenticate(String rawToken) {
    if (!SecureTokens.isSessionToken(rawToken)) {
      return Optional.empty();
    }
    OffsetDateTime now = OffsetDateTime.now();
    return userSessionRepository
        .findByTokenHash(SecureTokens.hash(rawToken))
        .filter(session -> session.isUsableAt(now))
        .flatMap(
            session ->
                userAccountRepository
                    .findById(session.getUserAccountId())
                    .map(userAccount -> credentialOf(session, userAccount)));
  }

  private UserAccount verifiedUserAccount(String email, String password) {
    Optional<UserAccount> userAccount = userAccountRepository.findByEmailIgnoreCase(email);
    String passwordHash =
        userAccount.map(UserAccount::getPasswordHash).orElse(unknownUserPasswordHash);
    boolean matches = passwordEncoder.matches(password, passwordHash);
    if (userAccount.isEmpty() || !matches) {
      throw new UnauthorizedException(ErrorCode.INVALID_CREDENTIALS);
    }
    return userAccount.get();
  }

  private UserSession sessionOf(UUID userSessionId) {
    return userSessionRepository
        .findById(userSessionId)
        .orElseThrow(() -> new UnauthorizedException(ErrorCode.INVALID_CREDENTIALS));
  }

  private UserSession lockedSessionOf(UUID userSessionId) {
    return userSessionRepository
        .findForUpdateById(userSessionId)
        .orElseThrow(() -> new UnauthorizedException(ErrorCode.INVALID_CREDENTIALS));
  }

  private CurrentSession currentSessionOf(UserSession session) {
    UserAccount userAccount =
        userAccountRepository
            .findById(session.getUserAccountId())
            .orElseThrow(() -> new UnauthorizedException(ErrorCode.INVALID_CREDENTIALS));
    List<Organization> organizations = organizationsOf(userAccount.getId());
    Organization organization =
        organizations.stream()
            .filter(candidate -> candidate.getId().equals(session.getOrganizationId()))
            .findFirst()
            .orElse(null);
    return new CurrentSession(
        session,
        userAccount,
        organization,
        organizations,
        organizationRole(session),
        scopesOf(session));
  }

  // 소속된 도입사를 소속된 순서대로 돌려준다.
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

  private String organizationRole(UserSession session) {
    if (session.getOrganizationId() == null) return null;
    return organizationMemberRepository
        .findById(new OrganizationMemberId(session.getOrganizationId(), session.getUserAccountId()))
        .filter(member -> member.getRevokedAt() == null)
        .map(OrganizationMember::getRole)
        .orElse(null);
  }

  private List<String> scopesOf(UserSession session) {
    if (session.getOrganizationId() == null) return List.of();
    return organizationMemberRepository
        .findById(new OrganizationMemberId(session.getOrganizationId(), session.getUserAccountId()))
        .filter(member -> member.getRevokedAt() == null)
        .map(OrganizationMember::getScopes)
        .orElse(List.of());
  }

  @Transactional(readOnly = true)
  public boolean requiresConsent(UUID userId) {
    return userAccountRepository.findById(userId).map(UserAccount::isConsentRequired).orElse(false);
  }

  private AuthenticatedCredential credentialOf(UserSession session, UserAccount userAccount) {
    AuthenticatedPrincipal principal =
        new AuthenticatedPrincipal(
            PrincipalKind.SESSION,
            session.getOrganizationId(),
            null,
            userAccount.getId(),
            session.getId(),
            null,
            userAccount.getRole(),
            Set.copyOf(scopesOf(session)));
    return new AuthenticatedCredential(principal, null);
  }
}
