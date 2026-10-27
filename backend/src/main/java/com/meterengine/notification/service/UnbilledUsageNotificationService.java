package com.meterengine.notification.service;

import com.meterengine.organization.entity.Organization;
import com.meterengine.organization.repository.OrganizationRepository;
import com.meterengine.scheduling.dto.ScheduledJobResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class UnbilledUsageNotificationService {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(UnbilledUsageNotificationService.class);
  private final OrganizationRepository organizationRepository;
  private final UnbilledUsageReportService unbilledUsageReportService;

  UnbilledUsageNotificationService(
      OrganizationRepository organizationRepository,
      UnbilledUsageReportService unbilledUsageReportService) {
    this.organizationRepository = organizationRepository;
    this.unbilledUsageReportService = unbilledUsageReportService;
  }

  public ScheduledJobResult report() {
    int processed = 0;
    int skipped = 0;
    int failed = 0;
    for (Organization organization :
        organizationRepository.findByStatusOrderByCreatedAtAscIdAsc(Organization.ACTIVE)) {
      try {
        if (unbilledUsageReportService.report(organization.getId())) {
          processed++;
        } else {
          skipped++;
        }
      } catch (RuntimeException exception) {
        failed++;
        LOGGER.error("unbilled usage report failed for organization {}", organization.getId());
      }
    }
    return new ScheduledJobResult(processed, skipped, failed);
  }
}
