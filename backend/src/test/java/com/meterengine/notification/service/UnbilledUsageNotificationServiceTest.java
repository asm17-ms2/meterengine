package com.meterengine.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meterengine.global.security.BusinessEnvironment;
import com.meterengine.global.security.BusinessEnvironmentContext;
import com.meterengine.global.security.OrganizationEnvironments;
import com.meterengine.organization.entity.Organization;
import com.meterengine.organization.repository.OrganizationRepository;
import com.meterengine.scheduling.dto.ScheduledJobResult;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
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
    ScheduledJobResult result =
        new UnbilledUsageNotificationService(
                repository, reporter, mock(OrganizationEnvironments.class))
            .report();
    assertThat(result).isEqualTo(new ScheduledJobResult(1, 1, 1));
    verify(reporter).report(processedId);
    verify(reporter).report(skippedId);
  }

  @Test
  void 한_환경의_실패후에도_나머지_환경을_처리하고_호출환경을_복원한다() {
    OrganizationRepository repository = mock(OrganizationRepository.class);
    UnbilledUsageReportService reporter = mock(UnbilledUsageReportService.class);
    OrganizationEnvironments environments = mock(OrganizationEnvironments.class);
    UUID organizationId = UUID.randomUUID();
    BusinessEnvironment test = new BusinessEnvironment(organizationId, UUID.randomUUID(), "test");
    BusinessEnvironment live = new BusinessEnvironment(organizationId, UUID.randomUUID(), "live");
    BusinessEnvironment incoming =
        new BusinessEnvironment(UUID.randomUUID(), UUID.randomUUID(), "live");
    when(repository.findByStatusOrderByCreatedAtAscIdAsc(Organization.ACTIVE))
        .thenReturn(List.of(new Organization(organizationId, "도입사", Organization.ACTIVE)));
    when(environments.list(organizationId)).thenReturn(List.of(test, live));
    var observed = new ArrayList<BusinessEnvironment>();
    when(reporter.report(organizationId))
        .thenAnswer(
            invocation -> {
              BusinessEnvironment current = BusinessEnvironmentContext.current();
              observed.add(current);
              if (current.equals(test)) throw new IllegalStateException("test failure");
              return current.equals(live);
            });
    BusinessEnvironmentContext.run(
        incoming,
        () -> {
          assertThat(
                  new UnbilledUsageNotificationService(repository, reporter, environments).report())
              .isEqualTo(new ScheduledJobResult(1, 1, 1));
          assertThat(BusinessEnvironmentContext.current()).isEqualTo(incoming);
        });
    assertThat(observed)
        .containsExactly(new BusinessEnvironment(organizationId, null, null), test, live);
    assertThat(BusinessEnvironmentContext.current()).isNull();
  }

  @Test
  void 통보와_환경_조회가_실패하면_예외_종류와_메시지를_로그에_남긴다(CapturedOutput output) {
    OrganizationRepository repository = mock(OrganizationRepository.class);
    UnbilledUsageReportService reporter = mock(UnbilledUsageReportService.class);
    OrganizationEnvironments environments = mock(OrganizationEnvironments.class);
    UUID organizationId = UUID.randomUUID();
    when(repository.findByStatusOrderByCreatedAtAscIdAsc(Organization.ACTIVE))
        .thenReturn(List.of(new Organization(organizationId, "도입사", Organization.ACTIVE)));
    when(environments.list(organizationId))
        .thenThrow(new IllegalStateException("environments down"));
    when(reporter.report(organizationId)).thenThrow(new IllegalArgumentException("report down"));
    new UnbilledUsageNotificationService(repository, reporter, environments).report();
    assertThat(output.getOut())
        .contains("java.lang.IllegalStateException: environments down")
        .contains("java.lang.IllegalArgumentException: report down");
  }
}
