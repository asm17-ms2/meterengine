package com.meterengine.metric.repository;

import com.meterengine.metric.entity.BillableMetricPeriod;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillableMetricPeriodRepository extends JpaRepository<BillableMetricPeriod, UUID> {
  List<BillableMetricPeriod> findByOrganizationId(UUID organizationId);
}
