package com.meterengine.account.service;

import com.meterengine.account.entity.Consent;
import com.meterengine.account.entity.UserAccount;
import com.meterengine.account.repository.ConsentRepository;
import com.meterengine.account.repository.UserAccountRepository;
import com.meterengine.audit.service.AuditActor;
import com.meterengine.audit.service.AuditLogService;
import com.meterengine.global.error.ConflictException;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.ErrorResponse.FieldError;
import com.meterengine.global.error.InvalidRequestException;
import com.meterengine.organization.entity.Organization;
import com.meterengine.organization.entity.OrganizationMember;
import com.meterengine.organization.repository.OrganizationMemberRepository;
import com.meterengine.organization.repository.OrganizationRepository;
import java.nio.charset.StandardCharsets;
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
public class SignupService {

  private static final List<String> REQUIRED_DOCUMENTS =
      List.of(Consent.TERMS_OF_SERVICE, Consent.PRIVACY_POLICY);

  private static final String CONSENTS_MESSAGE = "terms_of_service와 privacy_policy에 한 번씩 동의해야 합니다";

  private static final String PASSWORD_BYTES_MESSAGE =
      "UTF-8로 " + UserAccount.PASSWORD_MAX_BYTES + "바이트 이하여야 합니다";

  private final OrganizationRepository organizationRepository;
  private final UserAccountRepository userAccountRepository;
  private final OrganizationMemberRepository organizationMemberRepository;
  private final ConsentRepository consentRepository;
  private final PasswordEncoder passwordEncoder;
  private final AuditLogService auditLogService;

  SignupService(
      OrganizationRepository organizationRepository,
      UserAccountRepository userAccountRepository,
      OrganizationMemberRepository organizationMemberRepository,
      ConsentRepository consentRepository,
      PasswordEncoder passwordEncoder,
      AuditLogService auditLogService) {
    this.organizationRepository = organizationRepository;
    this.userAccountRepository = userAccountRepository;
    this.organizationMemberRepository = organizationMemberRepository;
    this.consentRepository = consentRepository;
    this.passwordEncoder = passwordEncoder;
    this.auditLogService = auditLogService;
  }

  // pending 도입사와 그 도입사에 소속된 member, 동의 행을 한 트랜잭션에 만든다.
  @Transactional
  public Signup create(
      String organizationName,
      String name,
      String email,
      String password,
      List<SignupConsent> consents) {
    validate(password, consents);
    if (userAccountRepository.findByEmailIgnoreCase(email).isPresent()) {
      throw new ConflictException(ErrorCode.EMAIL_ALREADY_EXISTS);
    }
    OffsetDateTime now = OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS);
    Organization organization =
        organizationRepository.save(
            new Organization(UUID.randomUUID(), organizationName, Organization.PENDING));
    UserAccount userAccount =
        userAccountRepository.save(
            new UserAccount(
                UUID.randomUUID(),
                email.toLowerCase(Locale.ROOT),
                passwordEncoder.encode(password),
                name,
                UserAccount.MEMBER,
                now));
    organizationMemberRepository.save(
        new OrganizationMember(organization.getId(), userAccount.getId(), now));
    consentRepository.saveAll(
        consents.stream()
            .map(
                consent ->
                    new Consent(
                        UUID.randomUUID(),
                        userAccount.getId(),
                        consent.document(),
                        consent.documentVersion(),
                        now))
            .toList());
    auditLogService.record(
        AuditActor.userAccount(userAccount.getId(), organization.getId()),
        "signup_created",
        "organization",
        organization.getId().toString(),
        Map.of());
    return new Signup(organization, userAccount);
  }

  private static void validate(String password, List<SignupConsent> consents) {
    if (password.getBytes(StandardCharsets.UTF_8).length > UserAccount.PASSWORD_MAX_BYTES) {
      throw new InvalidRequestException(
          ErrorCode.VALIDATION_ERROR, List.of(new FieldError("password", PASSWORD_BYTES_MESSAGE)));
    }
    List<String> documents = consents.stream().map(SignupConsent::document).sorted().toList();
    if (!documents.equals(REQUIRED_DOCUMENTS.stream().sorted().toList())) {
      throw new InvalidRequestException(
          ErrorCode.VALIDATION_ERROR, List.of(new FieldError("consents", CONSENTS_MESSAGE)));
    }
  }
}
