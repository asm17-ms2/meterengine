package com.meterengine.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.meterengine.account.config.SessionProperties;
import com.meterengine.account.entity.UserAccount;
import com.meterengine.account.entity.UserSession;
import com.meterengine.account.repository.UserAccountRepository;
import com.meterengine.account.repository.UserSessionRepository;
import com.meterengine.audit.service.AuditActor;
import com.meterengine.audit.service.AuditLogService;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.UnauthorizedException;
import com.meterengine.organization.repository.OrganizationMemberRepository;
import com.meterengine.organization.repository.OrganizationRepository;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

  private static final String EMAIL = "operator@example.com";

  private static final String PASSWORD = "operator-password";

  private static final String PASSWORD_HASH = "{bcrypt}stored-hash";

  private static final String UNKNOWN_USER_PASSWORD_HASH = "{bcrypt}unknown-user-hash";

  @Mock private UserAccountRepository userAccountRepository;
  @Mock private UserSessionRepository userSessionRepository;
  @Mock private OrganizationRepository organizationRepository;
  @Mock private OrganizationMemberRepository organizationMemberRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private AuditLogService auditLogService;
  @Mock private TransactionTemplate transactionTemplate;

  private SessionService sessionService;

  @BeforeEach
  void setUp() {
    when(passwordEncoder.encode(anyString())).thenReturn(UNKNOWN_USER_PASSWORD_HASH);
    sessionService =
        new SessionService(
            userAccountRepository,
            userSessionRepository,
            organizationRepository,
            organizationMemberRepository,
            passwordEncoder,
            new SessionProperties(Duration.ofHours(12)),
            auditLogService,
            transactionTemplate);
  }

  @Test
  void 로그인은_비밀번호를_트랜잭션_밖에서_확인하고_트랜잭션에서는_잠근_해시를_다시_본_뒤_세션과_감사를_남긴다() {
    UserAccount operator = userAccount(UserAccount.OPERATOR);
    when(userAccountRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(operator));
    when(userAccountRepository.findForUpdateById(operator.getId()))
        .thenReturn(Optional.of(operator));
    when(passwordEncoder.matches(PASSWORD, PASSWORD_HASH)).thenReturn(true);
    when(userSessionRepository.save(any(UserSession.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(transactionTemplate.execute(any()))
        .thenAnswer(
            invocation -> invocation.<TransactionCallback<?>>getArgument(0).doInTransaction(null));

    IssuedSession issued = sessionService.create(EMAIL, PASSWORD);

    assertThat(issued.token()).startsWith("me_session_");
    InOrder order =
        inOrder(
            userAccountRepository,
            passwordEncoder,
            transactionTemplate,
            userSessionRepository,
            auditLogService);
    order.verify(userAccountRepository).findByEmailIgnoreCase(EMAIL);
    order.verify(passwordEncoder).matches(PASSWORD, PASSWORD_HASH);
    order.verify(transactionTemplate).execute(any());
    order.verify(userAccountRepository).findForUpdateById(operator.getId());
    order.verify(userSessionRepository).save(any(UserSession.class));
    order
        .verify(auditLogService)
        .record(
            eq(AuditActor.userAccount(operator.getId(), null)),
            eq("session_created"),
            eq("user_session"),
            anyString(),
            eq(Map.of()));
  }

  @Test
  void 확인한_뒤_비밀번호가_바뀌었으면_세션을_저장하지_않고_401이다() {
    UserAccount operator = userAccount(UserAccount.OPERATOR);
    when(userAccountRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(operator));
    when(userAccountRepository.findForUpdateById(operator.getId()))
        .thenReturn(
            Optional.of(
                new UserAccount(
                    operator.getId(),
                    EMAIL,
                    "{bcrypt}changed-hash",
                    "사용자",
                    UserAccount.OPERATOR,
                    OffsetDateTime.now())));
    when(passwordEncoder.matches(PASSWORD, PASSWORD_HASH)).thenReturn(true);
    when(transactionTemplate.execute(any()))
        .thenAnswer(
            invocation -> invocation.<TransactionCallback<?>>getArgument(0).doInTransaction(null));

    assertThatThrownBy(() -> sessionService.create(EMAIL, PASSWORD))
        .isInstanceOfSatisfying(
            UnauthorizedException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS));
    verify(userSessionRepository, never()).save(any(UserSession.class));
    verifyNoInteractions(auditLogService);
  }

  @Test
  void 없는_이메일도_더미_해시와_비교하고_트랜잭션을_열지_않고_401이다() {
    when(userAccountRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> sessionService.create(EMAIL, PASSWORD))
        .isInstanceOfSatisfying(
            UnauthorizedException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS));
    verify(passwordEncoder).matches(PASSWORD, UNKNOWN_USER_PASSWORD_HASH);
    verifyNoInteractions(transactionTemplate);
  }

  @Test
  void 틀린_비밀번호는_트랜잭션을_열지_않고_401이다() {
    when(userAccountRepository.findByEmailIgnoreCase(EMAIL))
        .thenReturn(Optional.of(userAccount(UserAccount.MEMBER)));
    when(passwordEncoder.matches(PASSWORD, PASSWORD_HASH)).thenReturn(false);

    assertThatThrownBy(() -> sessionService.create(EMAIL, PASSWORD))
        .isInstanceOf(UnauthorizedException.class);
    verifyNoInteractions(transactionTemplate);
  }

  @Test
  void 잠근_뒤_세션이_폐기됐으면_도입사를_바꾸지_않고_401_invalid_credentials다() {
    UUID organizationId = UUID.randomUUID();
    UserSession session = session(organizationId);
    session.revoke(OffsetDateTime.now());
    when(userSessionRepository.findForUpdateById(session.getId())).thenReturn(Optional.of(session));

    assertThatThrownBy(() -> sessionService.update(session.getId(), UUID.randomUUID()))
        .isInstanceOfSatisfying(
            UnauthorizedException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS));
    assertThat(session.getOrganizationId()).isEqualTo(organizationId);
    verify(userSessionRepository, never()).findById(any());
    verifyNoInteractions(organizationMemberRepository, auditLogService);
  }

  @Test
  void 로그아웃은_잠근_세션을_폐기하고_감사_행을_남긴다() {
    UserSession session = session(UUID.randomUUID());
    when(userSessionRepository.findForUpdateById(session.getId())).thenReturn(Optional.of(session));

    sessionService.delete(session.getId());

    assertThat(session.getRevokedAt()).isNotNull();
    verify(userSessionRepository, never()).findById(any());
    verify(auditLogService)
        .record("session_deleted", "user_session", session.getId().toString(), Map.of());
  }

  private static UserAccount userAccount(String role) {
    return new UserAccount(
        UUID.randomUUID(), EMAIL, PASSWORD_HASH, "사용자", role, OffsetDateTime.now());
  }

  private static UserSession session(UUID organizationId) {
    OffsetDateTime now = OffsetDateTime.now();
    return new UserSession(
        UUID.randomUUID(), UUID.randomUUID(), new byte[32], organizationId, now, now.plusHours(1));
  }
}
