package com.meterengine.pricing.repository;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 미터별 단가 조회 (MS2-158). 금액 계산이 단가를 얻는 유일한 통로다.
 *
 * <p>JPA를 쓰지 않는다. 이번 슬라이스의 용도가 읽기 하나뿐인데 엔티티로 매핑하면 price_rate의 JSONB와 price_policy의 TEXT[] 타입 변환이
 * 통째로 딸려 온다. 쓰기 모델은 등록 API(MS2-157)에서 필요해질 때 만든다. JdbcTemplate 선례는 MetricUsageRepository.
 */
@Repository
public class PriceRateRepository {

  private final JdbcTemplate jdbc;

  PriceRateRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * 도입사의 미터별 기본 단가를 낸다.
   *
   * <p>기본 단가는 dimension_values가 빈 객체('{}')인 행이다 (2026-08-19 팀 합의: 무차원 미터와 미매칭 조합의 규약). 현재 슬라이스는 모든
   * 정책이 무차원이라 이 행이 곧 미터의 유일한 단가다. 차원별 단가 행을 읽는 조회는 다차원 스토리에서 추가한다.
   *
   * @return 미터 code -> 단가. 기본 단가 행이 없는 미터는 키 자체가 없다
   */
  public Map<String, BigDecimal> findBaseUnitPrices(UUID organizationId) {
    List<Map.Entry<String, BigDecimal>> rows =
        jdbc.query(
            """
            SELECT metric_code, unit_price
            FROM price_rate
            WHERE organization_id = ?
              AND dimension_values = '{}'::jsonb
            """,
            (rs, rowNum) -> Map.entry(rs.getString("metric_code"), rs.getBigDecimal("unit_price")),
            organizationId);

    Map<String, BigDecimal> prices = new HashMap<>();
    rows.forEach(row -> prices.put(row.getKey(), row.getValue()));
    return prices;
  }
}
