package com.meterengine.pricing.repository;

import com.meterengine.pricing.entity.PriceRate;
import com.meterengine.pricing.entity.PriceRateId;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PriceRateRepository extends JpaRepository<PriceRate, PriceRateId> {

  List<PriceRate> findByOrganizationIdAndDimensionValues(UUID organizationId, String combination);

  // 도입사의 미터별 기본 단가를 낸다.
  default Map<String, BigDecimal> findBaseUnitPrices(UUID organizationId) {
    return findByOrganizationIdAndDimensionValues(organizationId, PriceRate.BASE_COMBINATION)
        .stream()
        .collect(Collectors.toMap(PriceRate::getBillableMetricCode, PriceRate::getUnitPrice));
  }
}
