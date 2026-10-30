package com.meterengine.account.service;

import com.meterengine.account.dto.CreateUserAccountRequest;
import com.meterengine.account.repository.UserAccountRegistrationRepository;
import com.meterengine.account.repository.UserAccountRepository;
import com.meterengine.audit.service.AuditActor;
import com.meterengine.audit.service.AuditLogService;
import com.meterengine.global.error.ConflictException;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.security.AuthenticatedPrincipal;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperatorAccountService {
  private final UserAccountRegistrationRepository userAccountRegistrationRepository;
  private final UserAccountRepository userAccountRepository;
  private final PasswordEncoder passwordEncoder;
  private final AuditLogService auditLogService;

  OperatorAccountService(
      UserAccountRegistrationRepository userAccountRegistrationRepository,
      UserAccountRepository userAccountRepository,
      PasswordEncoder passwordEncoder,
      AuditLogService auditLogService) {
    this.userAccountRegistrationRepository = userAccountRegistrationRepository;
    this.userAccountRepository = userAccountRepository;
    this.passwordEncoder = passwordEncoder;
    this.auditLogService = auditLogService;
  }

  @Transactional
  public UserAccountMemberships create(
      AuthenticatedPrincipal principal, CreateUserAccountRequest request) {
    SignupService.validatePassword(request.password());
    UUID id = UUID.randomUUID();
    boolean created =
        userAccountRegistrationRepository.insertIfAbsent(
            id,
            request.email().toLowerCase(Locale.ROOT),
            passwordEncoder.encode(request.password()),
            request.name(),
            OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS));
    if (!created) throw new ConflictException(ErrorCode.EMAIL_ALREADY_EXISTS);
    var userAccount = userAccountRepository.findById(id).orElseThrow();
    auditLogService.record(
        AuditActor.from(principal).withOrganizationId(null),
        "user_account_created",
        "user_account",
        id.toString(),
        Map.of("role", userAccount.getRole()));
    return new UserAccountMemberships(userAccount, List.of());
  }
}
