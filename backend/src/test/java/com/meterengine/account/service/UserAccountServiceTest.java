package com.meterengine.account.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.meterengine.account.entity.UserAccount;
import com.meterengine.account.repository.UserAccountRepository;
import com.meterengine.audit.service.AuditLogService;
import com.meterengine.global.error.ConflictException;
import com.meterengine.organization.repository.OrganizationMemberRepository;
import com.meterengine.organization.repository.OrganizationRepository;
import java.time.OffsetDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;

class UserAccountServiceTest {
  @Test
  void 마지막_운영자는_member로_내릴_수_없다() {
    UserAccountRepository users = mock(UserAccountRepository.class);
    UUID id = UUID.randomUUID();
    UserAccount operator =
        new UserAccount(
            id, "operator@example.com", "hash", "운영자", "operator", OffsetDateTime.now());
    when(users.findOperatorsForUpdate()).thenReturn(List.of(operator));
    when(users.findForUpdateById(id)).thenReturn(Optional.of(operator));
    UserAccountService service =
        new UserAccountService(
            users,
            mock(OrganizationRepository.class),
            mock(OrganizationMemberRepository.class),
            mock(AuditLogService.class));
    assertThatThrownBy(() -> service.update(null, id, "member"))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("마지막 운영자");
  }
}
