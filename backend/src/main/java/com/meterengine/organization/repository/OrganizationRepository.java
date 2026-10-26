package com.meterengine.organization.repository;

import com.meterengine.organization.entity.Organization;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {

  List<Organization> findByOrderByCreatedAtAscIdAsc();

  List<Organization> findByStatusOrderByCreatedAtAscIdAsc(String status);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT o FROM Organization o WHERE o.id = :id")
  Optional<Organization> findForUpdateById(@Param("id") UUID id);
}
