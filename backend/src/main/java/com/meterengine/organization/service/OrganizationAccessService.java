package com.meterengine.organization.service;

import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.ForbiddenException;
import com.meterengine.global.error.NotFoundException;
import com.meterengine.global.metrics.DatabaseOperationTimer;
import com.meterengine.global.security.ActiveOrganizations;
import com.meterengine.global.security.BusinessEnvironmentContext;
import com.meterengine.global.security.OrganizationAccessPolicy;
import com.meterengine.organization.entity.Organization;
import com.meterengine.organization.repository.OrganizationRepository;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationAccessService implements OrganizationAccessPolicy, ActiveOrganizations {
  private static final String ORGANIZATION_TABLE = "organization";
  private static final String TEST_MODE = "test";

  private final OrganizationRepository organizationRepository;
  private final DatabaseOperationTimer databaseOperationTimer;
  private final JdbcTemplate jdbcTemplate;

  public OrganizationAccessService(
      OrganizationRepository organizationRepository,
      DatabaseOperationTimer databaseOperationTimer,
      JdbcTemplate jdbcTemplate) {
    this.organizationRepository = organizationRepository;
    this.databaseOperationTimer = databaseOperationTimer;
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  @Transactional(readOnly = true)
  public void check(UUID organizationId) {
    var organization =
        databaseOperationTimer
            .timeSelect(ORGANIZATION_TABLE, () -> organizationRepository.findById(organizationId))
            .orElseThrow(() -> new NotFoundException(ErrorCode.ORGANIZATION_NOT_FOUND));
    if (!Organization.ACTIVE.equals(organization.getStatus()))
      throw new ForbiddenException(ErrorCode.ORGANIZATION_NOT_ACTIVE);
    // 환경 헤더가 없는 요청은 기존 데이터 범위라 실제 키를 발급할 수 있으므로 테스트 환경만 통과시킨다.
    if (!organization.isLiveEnvironmentOpen()
        && !TEST_MODE.equals(BusinessEnvironmentContext.mode()))
      throw new ForbiddenException(ErrorCode.LIVE_ENVIRONMENT_LOCKED);
  }

  // JPA를 거치지 않아 영속성 컨텍스트에 남은 옛 상태를 보지 않는다.
  @Override
  public boolean contains(UUID organizationId) {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM organization WHERE id = ? AND status = ?",
            Integer.class,
            organizationId,
            Organization.ACTIVE);
    return count != null && count > 0;
  }

  @Override
  public Set<UUID> list() {
    return Set.copyOf(
        jdbcTemplate.queryForList(
            "SELECT id FROM organization WHERE status = ?", UUID.class, Organization.ACTIVE));
  }
}
