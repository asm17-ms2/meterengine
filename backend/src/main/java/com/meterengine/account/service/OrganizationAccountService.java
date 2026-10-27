package com.meterengine.account.service;

import com.meterengine.account.entity.Consent;
import com.meterengine.account.entity.UserAccount;
import com.meterengine.account.repository.ConsentRepository;
import com.meterengine.account.repository.UserAccountRepository;
import com.meterengine.account.repository.UserSessionRepository;
import com.meterengine.global.error.ConflictException;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.NotFoundException;
import com.meterengine.organization.api.MembershipConsent;
import com.meterengine.organization.api.OrganizationAccounts;
import com.meterengine.organization.api.OrganizationPerson;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationAccountService implements OrganizationAccounts {
  private final UserAccountRepository userAccountRepository;
  private final UserSessionRepository userSessionRepository;
  private final ConsentRepository consentRepository;
  private final PasswordEncoder passwordEncoder;

  public OrganizationAccountService(
      UserAccountRepository userAccountRepository,
      UserSessionRepository userSessionRepository,
      ConsentRepository consentRepository,
      PasswordEncoder passwordEncoder) {
    this.userAccountRepository = userAccountRepository;
    this.userSessionRepository = userSessionRepository;
    this.consentRepository = consentRepository;
    this.passwordEncoder = passwordEncoder;
  }

  @Override
  @Transactional(readOnly = true)
  public OrganizationPerson get(UUID userAccountId) {
    return person(
        userAccountRepository
            .findById(userAccountId)
            .orElseThrow(() -> new NotFoundException(ErrorCode.USER_ACCOUNT_NOT_FOUND)));
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<OrganizationPerson> find(String email) {
    return userAccountRepository
        .findByEmailIgnoreCase(email)
        .map(OrganizationAccountService::person);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean exists(UUID userAccountId) {
    return userAccountRepository.existsById(userAccountId);
  }

  @Override
  @Transactional
  public OrganizationPerson create(String email, String name, String password) {
    if (userAccountRepository.findByEmailIgnoreCase(email).isPresent())
      throw new ConflictException(ErrorCode.EMAIL_ALREADY_EXISTS);
    SignupService.validatePassword(password);
    UserAccount user = newUser(email, name, password);
    user.requireConsent();
    return person(save(user));
  }

  @Override
  @Transactional
  public OrganizationPerson register(
      String email, String name, String password, List<MembershipConsent> consents) {
    List<SignupConsent> accepted =
        consents.stream().map(c -> new SignupConsent(c.document(), c.documentVersion())).toList();
    SignupService.validate(password, accepted);
    UserAccount user = save(newUser(email, name, password));
    for (SignupConsent consent : accepted)
      consentRepository.save(
          new Consent(
              UUID.randomUUID(),
              user.getId(),
              consent.document(),
              consent.documentVersion(),
              now()));
    return person(user);
  }

  @Override
  @Transactional
  public void revoke(UUID organizationId, UUID userAccountId, OffsetDateTime revokedAt) {
    userSessionRepository.detachMembership(organizationId, userAccountId, revokedAt);
  }

  private UserAccount save(UserAccount user) {
    try {
      return userAccountRepository.saveAndFlush(user);
    } catch (DataIntegrityViolationException exception) {
      if (String.valueOf(exception.getMostSpecificCause().getMessage())
          .contains("user_account_email_unique"))
        throw new ConflictException(ErrorCode.EMAIL_ALREADY_EXISTS);
      throw exception;
    }
  }

  private UserAccount newUser(String email, String name, String password) {
    return new UserAccount(
        UUID.randomUUID(),
        email.toLowerCase(Locale.ROOT),
        passwordEncoder.encode(password),
        name,
        UserAccount.MEMBER,
        now());
  }

  private static OrganizationPerson person(UserAccount user) {
    return new OrganizationPerson(
        user.getId(), user.getEmail(), user.getName(), user.isConsentRequired());
  }

  private static OffsetDateTime now() {
    return OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS);
  }
}
