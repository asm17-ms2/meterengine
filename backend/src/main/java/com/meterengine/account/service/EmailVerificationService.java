package com.meterengine.account.service;

import com.meterengine.account.config.EmailVerificationProperties;
import com.meterengine.account.entity.EmailVerification;
import com.meterengine.account.entity.UserAccount;
import com.meterengine.account.repository.EmailVerificationRepository;
import com.meterengine.account.repository.UserAccountRepository;
import com.meterengine.audit.service.AuditActor;
import com.meterengine.audit.service.AuditLogService;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.NotFoundException;
import com.meterengine.global.security.SecureTokens;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailVerificationService {

  static final String TOKEN_PREFIX = "me_verify_";

  private static final Pattern TOKEN_PATTERN = Pattern.compile("^me_verify_[A-Za-z0-9]{32}$");

  private final EmailVerificationRepository emailVerificationRepository;
  private final UserAccountRepository userAccountRepository;
  private final EmailVerificationProperties emailVerificationProperties;
  private final ApplicationEventPublisher applicationEventPublisher;
  private final AuditLogService auditLogService;

  EmailVerificationService(
      EmailVerificationRepository emailVerificationRepository,
      UserAccountRepository userAccountRepository,
      EmailVerificationProperties emailVerificationProperties,
      ApplicationEventPublisher applicationEventPublisher,
      AuditLogService auditLogService) {
    this.emailVerificationRepository = emailVerificationRepository;
    this.userAccountRepository = userAccountRepository;
    this.emailVerificationProperties = emailVerificationProperties;
    this.applicationEventPublisher = applicationEventPublisher;
    this.auditLogService = auditLogService;
  }

  public boolean isRequired() {
    return emailVerificationProperties.enabled();
  }

  // 부른 쪽 트랜잭션에 토큰 행을 남기고, 메일은 그 트랜잭션이 커밋된 뒤 EmailVerificationMailer가 보낸다.
  @Transactional(propagation = Propagation.MANDATORY)
  void issue(UserAccount userAccount) {
    String token = SecureTokens.generate(TOKEN_PREFIX);
    OffsetDateTime now = now();
    OffsetDateTime expiresAt = now.plus(emailVerificationProperties.ttl());
    emailVerificationRepository.save(
        new EmailVerification(
            UUID.randomUUID(), userAccount.getId(), SecureTokens.hash(token), now, expiresAt));
    applicationEventPublisher.publishEvent(
        new EmailVerificationIssued(userAccount.getEmail(), token, expiresAt));
  }

  // 인증 메일을 다시 보낸다. 계정이 없거나 이미 인증했으면 아무것도 하지 않아 응답으로 계정 상태가 드러나지 않는다.
  @Transactional
  public void create(String email) {
    if (!isRequired()) return;
    userAccountRepository
        .findByEmailIgnoreCase(email.strip())
        .filter(userAccount -> !userAccount.isEmailVerified())
        .ifPresent(this::issue);
  }

  @Transactional
  public void confirm(String token) {
    if (!TOKEN_PATTERN.matcher(token).matches()) {
      throw new NotFoundException(ErrorCode.EMAIL_VERIFICATION_NOT_FOUND);
    }
    OffsetDateTime now = now();
    EmailVerification emailVerification =
        emailVerificationRepository
            .findForUpdateByTokenHash(SecureTokens.hash(token))
            .filter(candidate -> candidate.isUsableAt(now))
            .orElseThrow(() -> new NotFoundException(ErrorCode.EMAIL_VERIFICATION_NOT_FOUND));
    emailVerification.use(now);
    UserAccount userAccount =
        userAccountRepository
            .findForUpdateById(emailVerification.getUserAccountId())
            .orElseThrow(() -> new NotFoundException(ErrorCode.EMAIL_VERIFICATION_NOT_FOUND));
    if (userAccount.isEmailVerified()) return;
    userAccount.verifyEmail(now);
    auditLogService.record(
        AuditActor.userAccount(userAccount.getId(), null),
        "user_account_email_verified",
        "user_account",
        userAccount.getId().toString(),
        Map.of());
  }

  private static OffsetDateTime now() {
    return OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS);
  }
}
