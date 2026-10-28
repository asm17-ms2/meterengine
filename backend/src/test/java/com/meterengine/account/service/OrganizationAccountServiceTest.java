package com.meterengine.account.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meterengine.account.entity.Consent;
import com.meterengine.account.entity.UserAccount;
import com.meterengine.account.repository.ConsentRepository;
import com.meterengine.account.repository.UserAccountRepository;
import com.meterengine.account.repository.UserSessionRepository;
import com.meterengine.global.error.BusinessException;
import com.meterengine.global.error.ConflictException;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.organization.api.MembershipConsent;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class OrganizationAccountServiceTest {

  private static final String EMAIL = "member@example.com";

  private static final String PASSWORD = "member-password";

  private static final List<MembershipConsent> CONSENTS =
      List.of(
          new MembershipConsent(Consent.TERMS_OF_SERVICE, "2026-10-26-preview"),
          new MembershipConsent(Consent.PRIVACY_POLICY, "2026-10-26-preview"));

  @Mock private UserAccountRepository userAccountRepository;
  @Mock private UserSessionRepository userSessionRepository;
  @Mock private ConsentRepository consentRepository;
  @Mock private PasswordEncoder passwordEncoder;

  private OrganizationAccountService organizationAccountService;

  @BeforeEach
  void setUp() {
    organizationAccountService =
        new OrganizationAccountService(
            userAccountRepository, userSessionRepository, consentRepository, passwordEncoder);
  }

  @Test
  void 이미_있는_이메일로_가입하면_저장하지_않고_409_예외다() {
    when(userAccountRepository.findByEmailIgnoreCase(EMAIL))
        .thenReturn(
            Optional.of(
                new UserAccount(
                    UUID.randomUUID(),
                    EMAIL,
                    "hash",
                    "기존 사용자",
                    UserAccount.MEMBER,
                    OffsetDateTime.now())));

    assertThatThrownBy(
            () -> organizationAccountService.register(EMAIL, "새 사용자", PASSWORD, CONSENTS))
        .isInstanceOf(ConflictException.class)
        .extracting(exception -> ((BusinessException) exception).getErrorCode())
        .isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS);

    verify(userAccountRepository, never()).saveAndFlush(any());
    verify(consentRepository, never()).save(any());
  }

  @Test
  void 가입_저장에서_난_DataIntegrityViolationException은_바꾸지_않고_그대로_올려_보낸다() {
    DataIntegrityViolationException violation =
        new DataIntegrityViolationException("user_account_email_unique");
    when(userAccountRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.empty());
    when(passwordEncoder.encode(anyString())).thenReturn("hash");
    when(userAccountRepository.saveAndFlush(any())).thenThrow(violation);

    assertThatThrownBy(
            () -> organizationAccountService.register(EMAIL, "새 사용자", PASSWORD, CONSENTS))
        .isSameAs(violation);
  }

  @Test
  void 계정_생성_저장에서_난_DataIntegrityViolationException은_바꾸지_않고_그대로_올려_보낸다() {
    DataIntegrityViolationException violation =
        new DataIntegrityViolationException("user_account_email_unique");
    when(userAccountRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.empty());
    when(passwordEncoder.encode(anyString())).thenReturn("hash");
    when(userAccountRepository.saveAndFlush(any())).thenThrow(violation);

    assertThatThrownBy(() -> organizationAccountService.create(EMAIL, "새 사용자", PASSWORD))
        .isSameAs(violation);
  }
}
