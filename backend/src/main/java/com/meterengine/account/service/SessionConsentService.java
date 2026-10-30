package com.meterengine.account.service;

import com.meterengine.account.dto.AcceptSessionConsentsRequest;
import com.meterengine.account.entity.Consent;
import com.meterengine.account.repository.ConsentRepository;
import com.meterengine.account.repository.UserAccountRepository;
import com.meterengine.account.repository.UserSessionRepository;
import com.meterengine.audit.service.AuditActor;
import com.meterengine.audit.service.AuditLogService;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.ErrorResponse.FieldError;
import com.meterengine.global.error.InvalidRequestException;
import com.meterengine.global.error.UnauthorizedException;
import com.meterengine.global.security.AuthenticatedPrincipal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionConsentService {
  private final UserAccountRepository userAccountRepository;
  private final ConsentRepository consentRepository;
  private final AuditLogService auditLogService;
  private final PasswordEncoder passwordEncoder;
  private final UserSessionRepository userSessionRepository;

  public SessionConsentService(
      UserAccountRepository userAccountRepository,
      ConsentRepository consentRepository,
      AuditLogService auditLogService,
      PasswordEncoder passwordEncoder,
      UserSessionRepository userSessionRepository) {
    this.userAccountRepository = userAccountRepository;
    this.consentRepository = consentRepository;
    this.auditLogService = auditLogService;
    this.passwordEncoder = passwordEncoder;
    this.userSessionRepository = userSessionRepository;
  }

  @Transactional
  public void accept(AuthenticatedPrincipal principal, AcceptSessionConsentsRequest request) {
    var user =
        userAccountRepository
            .findForUpdateById(principal.userAccountId())
            .orElseThrow(() -> new UnauthorizedException(ErrorCode.INVALID_CREDENTIALS));
    if (!user.isConsentRequired()) return;
    var accepted =
        request.consents().stream()
            .map(c -> new SignupConsent(c.document(), c.documentVersion()))
            .toList();
    // 동의가 남은 계정은 관리자나 운영자가 비밀번호를 정한 계정이라 첫 동의에서 본인 비밀번호로 바꾼다.
    String newPassword = request.newPassword();
    if (newPassword == null || newPassword.isBlank())
      throw new InvalidRequestException(
          ErrorCode.VALIDATION_ERROR,
          List.of(new FieldError("new_password", "처음 받은 비밀번호 대신 쓸 새 비밀번호를 입력하세요")));
    if (passwordEncoder.matches(newPassword, user.getPasswordHash()))
      throw new InvalidRequestException(
          ErrorCode.VALIDATION_ERROR,
          List.of(new FieldError("new_password", "처음 받은 비밀번호와 다른 비밀번호를 입력하세요")));
    SignupService.validate(newPassword, accepted);
    for (var consent : accepted)
      consentRepository.save(
          new Consent(
              java.util.UUID.randomUUID(),
              user.getId(),
              consent.document(),
              consent.documentVersion(),
              OffsetDateTime.now()));
    user.changePasswordHash(passwordEncoder.encode(newPassword));
    user.acceptConsent();
    auditLogService.record(
        "user_account_password_updated", "user_account", user.getId().toString(), Map.of());
    auditLogService.record(
        "user_account_consents_accepted", "user_account", user.getId().toString(), Map.of());
    OffsetDateTime now = OffsetDateTime.now();
    for (var session : userSessionRepository.findByUserAccountIdAndRevokedAtIsNull(user.getId())) {
      if (session.getId().equals(principal.userSessionId())) continue;
      session.revoke(now);
      auditLogService.record(
          AuditActor.from(principal),
          SessionService.DELETED_ACTION,
          SessionService.TARGET_TYPE,
          session.getId().toString(),
          Map.of());
    }
  }
}
