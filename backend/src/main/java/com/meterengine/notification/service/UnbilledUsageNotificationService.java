package com.meterengine.notification.service;

import com.meterengine.global.security.BusinessEnvironment;
import com.meterengine.global.security.BusinessEnvironmentContext;
import com.meterengine.global.security.OrganizationEnvironments;
import com.meterengine.organization.entity.Organization;
import com.meterengine.organization.repository.OrganizationRepository;
import com.meterengine.scheduling.dto.ScheduledJobResult;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class UnbilledUsageNotificationService {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(UnbilledUsageNotificationService.class);
  private final OrganizationRepository organizationRepository;
  private final UnbilledUsageReportService unbilledUsageReportService;
  private final OrganizationEnvironments organizationEnvironments;

  UnbilledUsageNotificationService(
      OrganizationRepository organizationRepository,
      UnbilledUsageReportService unbilledUsageReportService,
      OrganizationEnvironments organizationEnvironments) {
    this.organizationRepository = organizationRepository;
    this.unbilledUsageReportService = unbilledUsageReportService;
    this.organizationEnvironments = organizationEnvironments;
  }

  public ScheduledJobResult report() {
    int processed = 0;
    int skipped = 0;
    int failed = 0;
    for (Organization organization :
        organizationRepository.findByStatusOrderByCreatedAtAscIdAsc(Organization.ACTIVE)) {
      List<BusinessEnvironment> environments = new ArrayList<>();
      environments.add(new BusinessEnvironment(organization.getId(), null, null));
      try {
        environments.addAll(organizationEnvironments.list(organization.getId()));
      } catch (RuntimeException exception) {
        failed++;
        LOGGER.error(
            "unbilled usage environments failed for organization {}",
            organization.getId(),
            exception);
      }
      for (BusinessEnvironment environment : environments) {
        try {
          if (BusinessEnvironmentContext.run(
              environment, () -> unbilledUsageReportService.report(organization.getId())))
            processed++;
          else skipped++;
        } catch (RuntimeException exception) {
          failed++;
          LOGGER.error(
              "unbilled usage report failed for organization {} environment {}",
              organization.getId(),
              environment.mode(),
              exception);
        }
      }
    }
    return new ScheduledJobResult(processed, skipped, failed);
  }
}
