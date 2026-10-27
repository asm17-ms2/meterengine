package com.meterengine.organization.repository;

import com.meterengine.organization.entity.OrganizationInvitation;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrganizationInvitationRepository
    extends JpaRepository<OrganizationInvitation, UUID> {
  List<OrganizationInvitation> findByOrganizationIdOrderByCreatedAtDescIdDesc(UUID organizationId);

  Optional<OrganizationInvitation> findByTokenHash(byte[] tokenHash);

  @Query("SELECT i.organizationId FROM OrganizationInvitation i WHERE i.tokenHash=:hash")
  Optional<UUID> findOrganizationIdByTokenHash(@Param("hash") byte[] tokenHash);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT i FROM OrganizationInvitation i WHERE i.tokenHash=:hash")
  Optional<OrganizationInvitation> findForUpdateByTokenHash(@Param("hash") byte[] tokenHash);

  @Query(
      "SELECT count(i)>0 FROM OrganizationInvitation i, Organization o WHERE i.organizationId=o.id"
          + " AND o.status='active' AND lower(i.email)=lower(:email) AND i.acceptedAt IS NULL AND"
          + " i.revokedAt IS NULL AND i.expiresAt>:now")
  boolean existsByEmail(@Param("email") String email, @Param("now") java.time.OffsetDateTime now);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT i FROM OrganizationInvitation i WHERE i.id=:id")
  Optional<OrganizationInvitation> findForUpdateById(@Param("id") UUID id);
}
