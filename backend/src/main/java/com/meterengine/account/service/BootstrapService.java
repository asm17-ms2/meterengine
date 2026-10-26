package com.meterengine.account.service;

import com.meterengine.account.config.BootstrapProperties;
import com.meterengine.account.entity.UserAccount;
import com.meterengine.account.entity.UserSession;
import com.meterengine.account.repository.UserAccountRepository;
import com.meterengine.account.repository.UserSessionRepository;
import com.meterengine.apikey.entity.ApiKey;
import com.meterengine.apikey.repository.ApiKeyRepository;
import com.meterengine.apikey.service.ApiKeyService;
import com.meterengine.audit.service.AuditActor;
import com.meterengine.audit.service.AuditLogService;
import com.meterengine.global.security.SecureTokens;
import com.meterengine.organization.entity.Organization;
import com.meterengine.organization.repository.OrganizationRepository;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BootstrapService implements ApplicationRunner {

  private static final Logger logger = LoggerFactory.getLogger(BootstrapService.class);

  private static final String OPERATOR_NAME = "운영자";

  private static final String CONSOLE_KEY_LABEL = "프론트 콘솔";

  private static final List<String> CONSOLE_KEY_SCOPES = List.of("admin");

  private static final String USER_ACCOUNT_TARGET_TYPE = "user_account";

  private final BootstrapProperties bootstrapProperties;
  private final UserAccountRepository userAccountRepository;
  private final UserSessionRepository userSessionRepository;
  private final ApiKeyRepository apiKeyRepository;
  private final OrganizationRepository organizationRepository;
  private final PasswordEncoder passwordEncoder;
  private final AuditLogService auditLogService;

  public BootstrapService(
      BootstrapProperties bootstrapProperties,
      UserAccountRepository userAccountRepository,
      UserSessionRepository userSessionRepository,
      ApiKeyRepository apiKeyRepository,
      OrganizationRepository organizationRepository,
      PasswordEncoder passwordEncoder,
      AuditLogService auditLogService) {
    this.bootstrapProperties = bootstrapProperties;
    this.userAccountRepository = userAccountRepository;
    this.userSessionRepository = userSessionRepository;
    this.apiKeyRepository = apiKeyRepository;
    this.organizationRepository = organizationRepository;
    this.passwordEncoder = passwordEncoder;
    this.auditLogService = auditLogService;
  }

  // 환경변수의 운영자와 프론트 콘솔 키를 넣고, 값이 어긋나면 아무것도 쓰지 않고 기동을 멈춘다.
  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    String email = textOf(bootstrapProperties.operatorEmail());
    String password = textOf(bootstrapProperties.operatorPassword());
    String rawApiKey = textOf(bootstrapProperties.frontendApiKey());
    checkOperator(email, password);
    UUID organizationId = rawApiKey == null ? null : consoleOrganizationId(rawApiKey);
    if (email != null) {
      bootstrapOperator(email, password);
    }
    if (rawApiKey != null) {
      bootstrapConsoleApiKey(rawApiKey, organizationId);
    }
  }

  private static void checkOperator(String email, String password) {
    if ((email == null) != (password == null)) {
      throw new IllegalStateException(
          "OPERATOR_EMAIL and OPERATOR_PASSWORD must be set together or both left empty");
    }
    if (password != null
        && password.getBytes(StandardCharsets.UTF_8).length > UserAccount.PASSWORD_MAX_BYTES) {
      throw new IllegalStateException("OPERATOR_PASSWORD must be at most 72 bytes in UTF-8");
    }
  }

  private UUID consoleOrganizationId(String rawApiKey) {
    if (!SecureTokens.isApiKey(rawApiKey)) {
      throw new IllegalStateException(
          "FRONTEND_API_KEY must be me_live_ or me_test_ followed by 32 base62 characters");
    }
    String rawOrganizationId = textOf(bootstrapProperties.frontendOrganizationId());
    if (rawOrganizationId == null) {
      throw new IllegalStateException(
          "FRONTEND_ORGANIZATION_ID must be set when FRONTEND_API_KEY is set");
    }
    UUID organizationId;
    try {
      organizationId = UUID.fromString(rawOrganizationId);
    } catch (IllegalArgumentException exception) {
      throw new IllegalStateException("FRONTEND_ORGANIZATION_ID must be a UUID");
    }
    Organization organization =
        organizationRepository
            .findById(organizationId)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "FRONTEND_ORGANIZATION_ID must name an existing organization"));
    boolean ownedByOtherOrganization =
        apiKeyRepository
            .findByKeyHash(SecureTokens.hash(rawApiKey))
            .filter(apiKey -> !apiKey.getOrganizationId().equals(organizationId))
            .isPresent();
    if (ownedByOtherOrganization) {
      throw new IllegalStateException(
          "FRONTEND_API_KEY belongs to an organization other than FRONTEND_ORGANIZATION_ID");
    }
    if (!Organization.ACTIVE.equals(organization.getStatus())) {
      logger.warn("Bootstrap console organization {} is not active", organizationId);
    }
    return organizationId;
  }

  private void bootstrapOperator(String email, String password) {
    userAccountRepository
        .findByEmailIgnoreCase(email)
        .ifPresentOrElse(
            userAccount -> updatePasswordIfChanged(userAccount, password),
            () -> createOperator(email, password));
  }

  private void createOperator(String email, String password) {
    UserAccount operator =
        userAccountRepository.save(
            new UserAccount(
                UUID.randomUUID(),
                email.toLowerCase(Locale.ROOT),
                passwordEncoder.encode(password),
                OPERATOR_NAME,
                UserAccount.OPERATOR,
                OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS)));
    auditLogService.record(
        AuditActor.system(),
        "user_account_created",
        USER_ACCOUNT_TARGET_TYPE,
        operator.getId().toString(),
        Map.of("role", UserAccount.OPERATOR));
    logger.info("Bootstrap created operator user account {}", operator.getEmail());
  }

  private void updatePasswordIfChanged(UserAccount userAccount, String password) {
    if (!UserAccount.OPERATOR.equals(userAccount.getRole())) {
      logger.warn(
          "Bootstrap operator email matches user account {} that is not an operator, role left unchanged",
          userAccount.getEmail());
    }
    if (passwordEncoder.matches(password, userAccount.getPasswordHash())) {
      return;
    }
    userAccount.changePasswordHash(passwordEncoder.encode(password));
    auditLogService.record(
        AuditActor.system(),
        "user_account_password_updated",
        USER_ACCOUNT_TARGET_TYPE,
        userAccount.getId().toString(),
        Map.of());
    revokeSessions(userAccount);
    logger.info("Bootstrap updated the password of user account {}", userAccount.getEmail());
  }

  private void revokeSessions(UserAccount userAccount) {
    OffsetDateTime now = OffsetDateTime.now();
    List<UserSession> sessions =
        userSessionRepository.findByUserAccountIdAndRevokedAtIsNull(userAccount.getId());
    for (UserSession session : sessions) {
      session.revoke(now);
      auditLogService.record(
          AuditActor.system(),
          SessionService.DELETED_ACTION,
          SessionService.TARGET_TYPE,
          session.getId().toString(),
          Map.of());
    }
    if (!sessions.isEmpty()) {
      logger.info(
          "Bootstrap revoked {} sessions of user account {}",
          sessions.size(),
          userAccount.getEmail());
    }
  }

  private void bootstrapConsoleApiKey(String rawApiKey, UUID organizationId) {
    byte[] keyHash = SecureTokens.hash(rawApiKey);
    OffsetDateTime now = OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS);
    ApiKey existing = apiKeyRepository.findByKeyHash(keyHash).orElse(null);
    if (existing != null) {
      if (!existing.isUsableAt(now)) {
        logger.warn(
            "Bootstrap left frontend console API key ending in {} as is because it is revoked or expired",
            existing.getLastFour());
      }
      return;
    }
    ApiKey apiKey =
        apiKeyRepository.save(
            new ApiKey(
                UUID.randomUUID(),
                organizationId,
                SecureTokens.apiKeyMode(rawApiKey),
                CONSOLE_KEY_LABEL,
                keyHash,
                SecureTokens.lastFour(rawApiKey),
                CONSOLE_KEY_SCOPES,
                null,
                now,
                null));
    auditLogService.record(
        organizationId,
        ApiKeyService.CREATED_ACTION,
        ApiKeyService.TARGET_TYPE,
        apiKey.getId().toString(),
        ApiKeyService.createdAuditDetail(apiKey, null));
    logger.info(
        "Bootstrap created frontend console API key ending in {} for organization {}",
        apiKey.getLastFour(),
        organizationId);
  }

  private static String textOf(String value) {
    return value == null || value.isBlank() ? null : value;
  }
}
