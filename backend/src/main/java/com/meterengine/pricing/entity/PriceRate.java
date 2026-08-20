package com.meterengine.pricing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import java.math.BigDecimal;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 조합별 단가 (MS2-158). 정책({@link PricePolicy})이 선언한 축의 조합 하나에 단가 하나가 붙는다.
 *
 * <p><b>dimension_values를 Map이 아니라 String으로 매핑한다.</b> 이 컬럼이 PK의 일부라서다. 식별자의 동등성이 jsonb가 정규화한 텍스트로
 * 정의돼야 영속성 컨텍스트가 같은 행을 같은 엔티티로 본다. Map으로 매핑하면 키 순서가 다른 같은 조합이 다른 식별자가 될 수 있다. 조합을 구조로 다루는 쪽(다차원 계산,
 * MS2-178)이 필요해지면 그때 파싱 계층을 얹는다.
 *
 * <p>쓰기는 아직 없다. 단가 등록/수정/삭제는 MS2-177이 만든다.
 */
@Entity
@IdClass(PriceRateId.class)
public class PriceRate {

  /** 무차원 미터와 미매칭 조합의 규약인 기본 단가 조합 (2026-08-19 팀 합의). */
  public static final String BASE_COMBINATION = "{}";

  @Id
  @Column(name = "organization_id")
  private UUID organizationId;

  @Id
  @Column(name = "metric_code")
  private String metricCode;

  @Id
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "dimension_values")
  private String dimensionValues;

  @Column(name = "unit_price", nullable = false)
  private BigDecimal unitPrice;

  /** Hibernate 전용. */
  protected PriceRate() {}

  public UUID getOrganizationId() {
    return organizationId;
  }

  public String getMetricCode() {
    return metricCode;
  }

  public String getDimensionValues() {
    return dimensionValues;
  }

  public BigDecimal getUnitPrice() {
    return unitPrice;
  }
}
