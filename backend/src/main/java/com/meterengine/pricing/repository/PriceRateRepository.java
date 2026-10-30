package com.meterengine.pricing.repository;

import com.meterengine.pricing.entity.PriceRate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PriceRateRepository extends JpaRepository<PriceRate, UUID> {

  @Query(
      value =
          "SELECT EXISTS(SELECT 1 FROM price_rate WHERE organization_id = :organizationId AND"
              + " environment_id IS NULL AND billable_metric_code = :billableMetricCode)",
      nativeQuery = true)
  boolean existsByOrganizationIdAndBillableMetricCode(
      UUID organizationId, String billableMetricCode);

  @Query(
      value =
          "SELECT * FROM price_rate WHERE organization_id = :organizationId AND environment_id IS"
              + " NULL ORDER BY billable_metric_code, dimension_values",
      nativeQuery = true)
  List<PriceRate> findByOrganizationIdOrderByBillableMetricCodeAscDimensionValuesAsc(
      UUID organizationId);

  @Query(
      value =
          "SELECT * FROM price_rate WHERE organization_id = :organizationId AND environment_id IS"
              + " NOT DISTINCT FROM CAST(:environmentId AS UUID) ORDER BY billable_metric_code,"
              + " dimension_values",
      nativeQuery = true)
  List<PriceRate> listScoped(UUID organizationId, UUID environmentId);

  @Query(
      value =
          "SELECT * FROM price_rate WHERE organization_id = :organizationId AND environment_id IS"
              + " NOT DISTINCT FROM CAST(:environmentId AS UUID) AND id = :id",
      nativeQuery = true)
  Optional<PriceRate> findScoped(UUID organizationId, UUID environmentId, UUID id);
}
