package com.meterengine.pricing.repository;

import com.meterengine.pricing.entity.PriceRate;
import com.meterengine.pricing.entity.PriceRateId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PriceRateRepository extends JpaRepository<PriceRate, PriceRateId> {

  boolean existsByOrganizationIdAndBillableMetricCode(
      UUID organizationId, String billableMetricCode);

  List<PriceRate> findByOrganizationIdOrderByBillableMetricCodeAscDimensionValuesAsc(
      UUID organizationId);
}
