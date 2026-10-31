package com.meterengine.organization.service;

import com.meterengine.global.database.DatabaseScope;
import com.meterengine.global.database.DatabaseScopes;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.ForbiddenException;
import com.meterengine.global.error.NotFoundException;
import com.meterengine.global.metrics.DatabaseOperationTimer;
import com.meterengine.global.security.OrganizationAccessPolicy;
import com.meterengine.organization.entity.Organization;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationAccessService implements OrganizationAccessPolicy {
  private final JdbcTemplate jdbcTemplate;
  private final DatabaseOperationTimer databaseOperationTimer;

  public OrganizationAccessService(
      JdbcTemplate jdbcTemplate, DatabaseOperationTimer databaseOperationTimer) {
    this.jdbcTemplate = jdbcTemplate;
    this.databaseOperationTimer = databaseOperationTimer;
  }

  @Override
  @Transactional(readOnly = true)
  public void check(UUID organizationId) {
    String table =
        DatabaseScopes.current() == DatabaseScope.SHARED
            ? "shared.organization"
            : "shared.organization_reference";
    var statuses =
        databaseOperationTimer.timeSelect(
            "organization",
            () ->
                jdbcTemplate.queryForList(
                    "SELECT status FROM " + table + " WHERE id = ?", String.class, organizationId));
    if (statuses.isEmpty()) throw new NotFoundException(ErrorCode.ORGANIZATION_NOT_FOUND);
    if (!Organization.ACTIVE.equals(statuses.getFirst()))
      throw new ForbiddenException(ErrorCode.ORGANIZATION_NOT_ACTIVE);
  }
}
