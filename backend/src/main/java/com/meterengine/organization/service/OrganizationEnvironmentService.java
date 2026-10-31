package com.meterengine.organization.service;

import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.NotFoundException;
import com.meterengine.global.metrics.DatabaseOperationTimer;
import com.meterengine.global.security.BusinessEnvironment;
import com.meterengine.global.security.OrganizationEnvironments;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationEnvironmentService implements OrganizationEnvironments {
  private static final String ORGANIZATION_ENVIRONMENT_TABLE = "organization_environment";

  private final JdbcTemplate jdbcTemplate;
  private final DatabaseOperationTimer databaseOperationTimer;

  public OrganizationEnvironmentService(
      JdbcTemplate jdbcTemplate, DatabaseOperationTimer databaseOperationTimer) {
    this.jdbcTemplate = jdbcTemplate;
    this.databaseOperationTimer = databaseOperationTimer;
  }

  @Override
  @Transactional(readOnly = true)
  public BusinessEnvironment resolve(UUID organizationId, String mode) {
    return databaseOperationTimer
        .timeSelect(ORGANIZATION_ENVIRONMENT_TABLE, () -> list(organizationId))
        .stream()
        .filter(environment -> environment.mode().equals(mode))
        .findFirst()
        .orElseThrow(() -> new NotFoundException(ErrorCode.ORGANIZATION_NOT_FOUND));
  }

  @Transactional(readOnly = true)
  public List<BusinessEnvironment> list(UUID organizationId) {
    return jdbcTemplate.query(
        "SELECT organization_id, id, mode FROM organization_environment WHERE organization_id = ? ORDER BY CASE mode WHEN 'test' THEN 0 ELSE 1 END",
        (rs, row) ->
            new BusinessEnvironment(
                rs.getObject("organization_id", UUID.class),
                rs.getObject("id", UUID.class),
                rs.getString("mode")),
        organizationId);
  }
}
