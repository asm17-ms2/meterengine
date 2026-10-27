package com.meterengine.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meterengine.organization.entity.Organization;
import com.meterengine.organization.repository.OrganizationRepository;
import com.meterengine.scheduling.dto.ScheduledJobResult;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UnbilledUsageNotificationServiceTest {
  @Test
  void 한_도입사의_실패가_다른_도입사의_통보를_막지_않는다() {
    OrganizationRepository repository = mock(OrganizationRepository.class);
    UnbilledUsageReportService reporter = mock(UnbilledUsageReportService.class);
    UUID failedId = UUID.randomUUID();
    UUID processedId = UUID.randomUUID();
    UUID skippedId = UUID.randomUUID();
    when(repository.findByStatusOrderByCreatedAtAscIdAsc(Organization.ACTIVE))
        .thenReturn(
            List.of(
                new Organization(failedId, "실패 도입사", Organization.ACTIVE),
                new Organization(processedId, "통보 도입사", Organization.ACTIVE),
                new Organization(skippedId, "건너뛸 도입사", Organization.ACTIVE)));
    when(reporter.report(failedId)).thenThrow(new IllegalStateException("unavailable"));
    when(reporter.report(processedId)).thenReturn(true);
    when(reporter.report(skippedId)).thenReturn(false);
    ScheduledJobResult result = new UnbilledUsageNotificationService(repository, reporter).report();
    assertThat(result).isEqualTo(new ScheduledJobResult(1, 1, 1));
    verify(reporter).report(processedId);
    verify(reporter).report(skippedId);
  }
}
