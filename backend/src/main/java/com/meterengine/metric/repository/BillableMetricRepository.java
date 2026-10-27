package com.meterengine.metric.repository;

import com.meterengine.metric.entity.BillableMetric;
import com.meterengine.metric.entity.BillableMetricId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BillableMetricRepository extends JpaRepository<BillableMetric, BillableMetricId> {

  @Query(
      value =
          "SELECT * FROM billable_metric WHERE organization_id = :organizationId AND environment_id"
              + " IS NULL ORDER BY code",
      nativeQuery = true)
  List<BillableMetric> findByOrganizationIdOrderByCodeAsc(UUID organizationId);

  @Query(
      value =
          "SELECT EXISTS(SELECT 1 FROM billable_metric WHERE organization_id = :organizationId AND"
              + " environment_id IS NULL AND event_type = :eventType AND target_property ="
              + " :targetProperty)",
      nativeQuery = true)
  boolean existsByOrganizationIdAndEventTypeAndTargetProperty(
      UUID organizationId, String eventType, String targetProperty);

  @Query(
      value =
          "SELECT * FROM billable_metric WHERE organization_id = :organizationId AND environment_id"
              + " IS NOT DISTINCT FROM CAST(:environmentId AS UUID) ORDER BY code",
      nativeQuery = true)
  List<BillableMetric> listScoped(UUID organizationId, UUID environmentId);
}
