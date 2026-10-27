package com.meterengine.pricing.repository;

import com.meterengine.pricing.entity.PricePlan;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PricePlanRepository extends JpaRepository<PricePlan, UUID> {
  @Query(
      value =
          "SELECT * FROM price_plan WHERE organization_id = :organizationId AND environment_id IS"
              + " NULL ORDER BY effective_from DESC",
      nativeQuery = true)
  List<PricePlan> findByOrganizationIdOrderByEffectiveFromDesc(UUID organizationId);

  @Query(
      value =
          "SELECT * FROM price_plan WHERE organization_id = :organizationId AND environment_id IS"
              + " NOT DISTINCT FROM CAST(:environmentId AS UUID) ORDER BY effective_from DESC",
      nativeQuery = true)
  List<PricePlan> listScoped(UUID organizationId, UUID environmentId);

  @Query(
      value =
          "SELECT * FROM price_plan WHERE organization_id = :organizationId AND environment_id IS"
              + " NOT DISTINCT FROM CAST(:environmentId AS UUID) AND id = :id",
      nativeQuery = true)
  Optional<PricePlan> findScoped(UUID organizationId, UUID environmentId, UUID id);
}
