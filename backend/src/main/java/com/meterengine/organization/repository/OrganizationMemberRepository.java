package com.meterengine.organization.repository;

import com.meterengine.organization.entity.OrganizationMember;
import com.meterengine.organization.entity.OrganizationMemberId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrganizationMemberRepository
    extends JpaRepository<OrganizationMember, OrganizationMemberId> {

  @Query(
      "SELECT m FROM OrganizationMember m WHERE m.userAccountId = :userAccountId AND m.revokedAt IS"
          + " NULL ORDER BY m.createdAt, m.organizationId")
  List<OrganizationMember> findByUserAccountIdOrderByCreatedAtAscOrganizationIdAsc(
      @Param("userAccountId") UUID userAccountId);

  @Query(
      "SELECT m FROM OrganizationMember m WHERE m.revokedAt IS NULL ORDER BY m.createdAt,"
          + " m.organizationId")
  List<OrganizationMember> findByOrderByCreatedAtAscOrganizationIdAsc();

  List<OrganizationMember> findByOrganizationIdOrderByCreatedAtAscUserAccountIdAsc(
      UUID organizationId);

  long countByOrganizationIdAndRoleAndRevokedAtIsNull(UUID organizationId, String role);
}
