package com.meterengine.pricing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import java.math.BigDecimal;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

@Entity
@IdClass(PriceRateId.class)
public class PriceRate implements Persistable<PriceRateId> {

  // 기본 단가가 붙는 조합이다.
  public static final String BASE_DIMENSION_VALUES = "{}";

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

  protected PriceRate() {}

  public PriceRate(
      UUID organizationId,
      String billableMetricCode,
      String dimensionValues,
      BigDecimal unitPrice) {
    this.organizationId = organizationId;
    this.billableMetricCode = billableMetricCode;
    this.dimensionValues = dimensionValues;
    this.unitPrice = unitPrice;
  }

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

  @Override
  public PriceRateId getId() {
    return new PriceRateId(organizationId, billableMetricCode, dimensionValues);
  }

  @Override
  public boolean isNew() {
    return true;
  }
}
