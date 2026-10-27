package com.meterengine.organization.service;

import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.ForbiddenException;
import com.meterengine.global.error.NotFoundException;
import com.meterengine.global.security.OrganizationAccessPolicy;
import com.meterengine.organization.entity.Organization;
import com.meterengine.organization.repository.OrganizationRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationAccessService implements OrganizationAccessPolicy {
  private final OrganizationRepository organizationRepository;

  public OrganizationAccessService(OrganizationRepository organizationRepository) {
    this.organizationRepository = organizationRepository;
  }

  @Override
  @Transactional(readOnly = true)
  public void check(UUID organizationId) {
    var organization =
        organizationRepository
            .findById(organizationId)
            .orElseThrow(() -> new NotFoundException(ErrorCode.ORGANIZATION_NOT_FOUND));
    if (!Organization.ACTIVE.equals(organization.getStatus()))
      throw new ForbiddenException(ErrorCode.ORGANIZATION_NOT_ACTIVE);
  }
}
