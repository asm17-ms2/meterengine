package com.meterengine.account.service;

import com.meterengine.account.dto.AcceptSessionConsentsRequest;
import com.meterengine.account.entity.Consent;
import com.meterengine.account.repository.ConsentRepository;
import com.meterengine.account.repository.UserAccountRepository;
import com.meterengine.audit.service.AuditLogService;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.UnauthorizedException;
import com.meterengine.global.security.AuthenticatedPrincipal;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionConsentService {
  private final UserAccountRepository userAccountRepository;
  private final ConsentRepository consentRepository;
  private final AuditLogService auditLogService;

  public SessionConsentService(
      UserAccountRepository userAccountRepository,
      ConsentRepository consentRepository,
      AuditLogService auditLogService) {
    this.userAccountRepository = userAccountRepository;
    this.consentRepository = consentRepository;
    this.auditLogService = auditLogService;
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
    SignupService.validate("", accepted);
    for (var consent : accepted)
      consentRepository.save(
          new Consent(
              java.util.UUID.randomUUID(),
              user.getId(),
              consent.document(),
              consent.documentVersion(),
              OffsetDateTime.now()));
    user.acceptConsent();
    auditLogService.record(
        "user_account_consents_accepted", "user_account", user.getId().toString(), Map.of());
  }
}
