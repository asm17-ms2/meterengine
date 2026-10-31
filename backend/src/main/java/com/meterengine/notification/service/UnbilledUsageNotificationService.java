package com.meterengine.notification.service;

import com.meterengine.global.security.OrganizationEnvironments;
import com.meterengine.organization.repository.OrganizationRepository;
import com.meterengine.scheduling.dto.ScheduledJobResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class UnbilledUsageNotificationService {
  @org.springframework.beans.factory.annotation.Autowired
  private com.meterengine.global.database.CutoverPolicy cutover;

  @org.springframework.beans.factory.annotation.Autowired
  private org.springframework.jdbc.core.JdbcTemplate jdbc;

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
    if (cutover.readOnly()) return new ScheduledJobResult(0, 0, 0);
    int processed = 0;
    int skipped = 0;
    int failed = 0;
    if (com.meterengine.global.database.DatabaseScopes.current()
        == com.meterengine.global.database.DatabaseScope.SHARED)
      throw new IllegalStateException("notification report requires a business scope");
    for (var organization :
        jdbc.queryForList(
            "SELECT id FROM shared.organization_reference WHERE status='active'",
            java.util.UUID.class)) {
      try {
        if (unbilledUsageReportService.report(organization)) processed++;
        else skipped++;
      } catch (RuntimeException exception) {
        failed++;
        LOGGER.error(
            "unbilled usage report failed for organization {} environment {}",
            organization,
            com.meterengine.global.database.DatabaseScopes.current(),
            exception);
      }
    }
    return new ScheduledJobResult(processed, skipped, failed);
  }
}
