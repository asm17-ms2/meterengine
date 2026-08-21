package com.meterengine.pricing.repository;

import com.meterengine.pricing.entity.PriceRate;
import com.meterengine.pricing.entity.PriceRateId;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 미터별 단가 조회 (MS2-158). 금액 계산이 단가를 얻는 유일한 통로다.
 *
 * <p>MS2-158의 JdbcTemplate에서 JPA로 전환했다 (PR 43 리뷰 결정, pricing 리포지토리를 JPA로 통일). 쓰기는 단가 API(MS2-177)가
 * 이 인터페이스에 얹는다.
 */
public interface PriceRateRepository extends JpaRepository<PriceRate, PriceRateId> {

  List<PriceRate> findByOrganizationIdAndDimensionValues(UUID organizationId, String combination);

  /**
   * 도입사의 미터별 기본 단가를 낸다.
   *
   * <p>기본 단가는 dimension_values가 {@link PriceRate#BASE_COMBINATION}인 행이다. 차원별 단가 행을 읽는 조회는 다차원
   * 스토리(MS2-178)에서 추가한다.
   *
   * @return 미터 code -> 단가. 기본 단가 행이 없는 미터는 키 자체가 없다
   */
  default Map<String, BigDecimal> findBaseUnitPrices(UUID organizationId) {
    return findByOrganizationIdAndDimensionValues(organizationId, PriceRate.BASE_COMBINATION)
        .stream()
        .collect(Collectors.toMap(PriceRate::getMetricCode, PriceRate::getUnitPrice));
  }
}
