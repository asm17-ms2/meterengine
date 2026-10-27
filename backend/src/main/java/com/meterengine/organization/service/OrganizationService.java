package com.meterengine.organization.service;

import com.meterengine.audit.service.AuditActor;
import com.meterengine.audit.service.AuditLogService;
import com.meterengine.global.error.ConflictException;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.NotFoundException;
import com.meterengine.global.security.AuthenticatedPrincipal;
import com.meterengine.global.security.BusinessEnvironmentContext;
import com.meterengine.organization.dto.SupplierResponse;
import com.meterengine.organization.dto.UpdateSupplierRequest;
import com.meterengine.organization.entity.Organization;
import com.meterengine.organization.repository.OrganizationRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationService {

  private final OrganizationRepository organizationRepository;
  private final AuditLogService auditLogService;
  private final JdbcTemplate jdbcTemplate;

  OrganizationService(
      OrganizationRepository organizationRepository,
      AuditLogService auditLogService,
      JdbcTemplate jdbcTemplate) {
    this.organizationRepository = organizationRepository;
    this.auditLogService = auditLogService;
    this.jdbcTemplate = jdbcTemplate;
  }

  @Transactional(readOnly = true)
  public Organization get(UUID organizationId) {
    return organizationRepository
        .findById(organizationId)
        .orElseThrow(() -> new NotFoundException(ErrorCode.ORGANIZATION_NOT_FOUND));
  }

  // 도입사를 만든 순서로 돌려주고, status가 null이면 모든 상태를 돌려준다.
  @Transactional(readOnly = true)
  public List<Organization> list(String status) {
    if (status == null) {
      return organizationRepository.findByOrderByCreatedAtAscIdAsc();
    }
    return organizationRepository.findByStatusOrderByCreatedAtAscIdAsc(status);
  }

  // 허용된 전이만 하고 감사 행은 운영자가 대상 도입사에 한 일로 남긴다.
  @Transactional
  public Organization update(AuthenticatedPrincipal principal, UUID organizationId, String status) {
    Organization organization =
        organizationRepository
            .findForUpdateById(organizationId)
            .orElseThrow(() -> new NotFoundException(ErrorCode.ORGANIZATION_NOT_FOUND));
    if (!organization.canTransitionTo(status)) {
      throw new ConflictException(ErrorCode.ORGANIZATION_STATUS_TRANSITION_INVALID);
    }
    String previousStatus = organization.getStatus();
    organization.transitionTo(status);
    auditLogService.record(
        AuditActor.from(principal).withOrganizationId(organizationId),
        "organization_status_updated",
        "organization",
        organizationId.toString(),
        Map.of("from", previousStatus, "to", status));
    return organization;
  }

  @Transactional(readOnly = true)
  public SupplierResponse find(UUID organizationId) {
    if (BusinessEnvironmentContext.environmentId() == null)
      return SupplierResponse.from(get(organizationId));
    return jdbcTemplate.queryForObject(
        "SELECT business_registration_number, representative_name, business_address FROM organization_environment WHERE organization_id = ? AND id = ?",
        (rs, row) ->
            new SupplierResponse(
                rs.getString("business_registration_number"),
                rs.getString("representative_name"),
                rs.getString("business_address")),
        organizationId,
        BusinessEnvironmentContext.environmentId());
  }

  @Transactional
  public SupplierResponse update(UUID organizationId, UpdateSupplierRequest request) {
    if (BusinessEnvironmentContext.environmentId() != null) {
      jdbcTemplate.update(
          "UPDATE organization_environment SET business_registration_number = ?, representative_name = ?, business_address = ? WHERE organization_id = ? AND id = ?",
          request.businessRegistrationNumber(),
          request.representativeName(),
          request.businessAddress(),
          organizationId,
          BusinessEnvironmentContext.environmentId());
      auditLogService.record(
          organizationId, "supplier_updated", "organization", organizationId.toString(), Map.of());
      return find(organizationId);
    }
    Organization organization =
        organizationRepository
            .findForUpdateById(organizationId)
            .orElseThrow(() -> new NotFoundException(ErrorCode.ORGANIZATION_NOT_FOUND));
    organization.updateSupplier(
        request.businessRegistrationNumber(),
        request.representativeName(),
        request.businessAddress());
    auditLogService.record(
        organizationId, "supplier_updated", "organization", organizationId.toString(), Map.of());
    return SupplierResponse.from(organization);
  }
}
