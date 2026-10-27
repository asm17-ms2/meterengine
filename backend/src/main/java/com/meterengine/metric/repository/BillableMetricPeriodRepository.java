package com.meterengine.metric.repository;

import com.meterengine.metric.entity.BillableMetricPeriod;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BillableMetricPeriodRepository extends JpaRepository<BillableMetricPeriod, UUID> {
  @Query(
      value =
          "SELECT * FROM billable_metric_period WHERE organization_id = :organizationId AND"
              + " environment_id IS NULL",
      nativeQuery = true)
  List<BillableMetricPeriod> findByOrganizationId(UUID organizationId);

  @Query(
      value =
          "SELECT * FROM billable_metric_period WHERE organization_id = :organizationId AND"
              + " environment_id IS NOT DISTINCT FROM CAST(:environmentId AS UUID)",
      nativeQuery = true)
  List<BillableMetricPeriod> listScoped(UUID organizationId, UUID environmentId);
}
