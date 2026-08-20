package com.meterengine.pricing.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

/**
 * 가격 정책과 단가의 쓰기 모델 (MS2-157).
 *
 * <p>JPA를 쓰지 않는 이유는 {@link PriceRateRepository}와 같다. price_policy의 TEXT[]와 price_rate의 JSONB를 엔티티로
 * 매핑하는 부담이 지금 용도(INSERT 몇 줄)보다 크다.
 *
 * <p>정책과 단가를 한 클래스가 쓴다. 단가는 정책에 종속된 행(FK 과녁이 price_policy)이라 쓰기가 항상 정책 단위로 묶이기 때문이다. 단가만 따로 고치는
 * API가 생기면(MS2-177) {@link #insertRate}를 그쪽이 재사용한다.
 */
@Repository
public class PricePolicyRepository {

  private final JdbcTemplate jdbc;
  private final JsonMapper jsonMapper;

  PricePolicyRepository(JdbcTemplate jdbc, JsonMapper jsonMapper) {
    this.jdbc = jdbc;
    this.jsonMapper = jsonMapper;
  }

  public boolean exists(UUID organizationId, String metricCode) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            """
            SELECT EXISTS(
              SELECT 1 FROM price_policy WHERE organization_id = ? AND metric_code = ?)
            """,
            Boolean.class,
            organizationId,
            metricCode));
  }

  public void insertPolicy(
      UUID organizationId, String metricCode, List<String> dimensionProperties) {
    jdbc.update(
        """
        INSERT INTO price_policy (organization_id, metric_code, dimension_properties)
        VALUES (?, ?, ?)
        """,
        ps -> {
          ps.setObject(1, organizationId);
          ps.setString(2, metricCode);
          ps.setArray(3, ps.getConnection().createArrayOf("text", dimensionProperties.toArray()));
        });
  }

  /**
   * 단가 한 행을 넣는다.
   *
   * <p>조합을 JSON 문자열로 직렬화해 {@code ::jsonb}로 캐스팅한다. 키 순서 정규화는 Postgres jsonb의 몫이라, 표기만 다른 같은 조합은 PK가
   * 막는다 (SchemaConstraintTest에서 확인한 성질).
   */
  public void insertRate(
      UUID organizationId,
      String metricCode,
      Map<String, String> dimensionValues,
      BigDecimal unitPrice) {
    jdbc.update(
        """
        INSERT INTO price_rate (organization_id, metric_code, dimension_values, unit_price)
        VALUES (?, ?, ?::jsonb, ?)
        """,
        organizationId,
        metricCode,
        jsonMapper.writeValueAsString(dimensionValues),
        unitPrice);
  }
}
