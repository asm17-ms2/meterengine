package com.meterengine.pricing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import java.math.BigDecimal;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@IdClass(PriceRateId.class)
public class PriceRate {

  // 기본 단가가 붙는 조합이다.
  public static final String BASE_COMBINATION = "{}";

  @Id
  @Column(name = "organization_id")
  private UUID organizationId;

  @Id
  @Column(name = "billable_metric_code")
  private String billableMetricCode;

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

  public String getBillableMetricCode() {
    return billableMetricCode;
  }

  public String getDimensionValues() {
    return dimensionValues;
  }

  public BigDecimal getUnitPrice() {
    return unitPrice;
  }
}
