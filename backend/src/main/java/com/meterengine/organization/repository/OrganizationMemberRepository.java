package com.meterengine.organization.repository;

import com.meterengine.organization.entity.OrganizationMember;
import com.meterengine.organization.entity.OrganizationMemberId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationMemberRepository
    extends JpaRepository<OrganizationMember, OrganizationMemberId> {

  List<OrganizationMember> findByUserAccountIdOrderByCreatedAtAscOrganizationIdAsc(
      UUID userAccountId);

  List<OrganizationMember> findByOrderByCreatedAtAscOrganizationIdAsc();
}
