package com.meterengine.metric.repository;

import com.meterengine.metric.entity.BillableMetric;
import com.meterengine.metric.entity.BillableMetricId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillableMetricRepository extends JpaRepository<BillableMetric, BillableMetricId> {

  List<BillableMetric> findByOrganizationIdOrderByCodeAsc(UUID organizationId);
}
