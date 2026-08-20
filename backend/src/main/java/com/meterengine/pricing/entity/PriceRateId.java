package com.meterengine.pricing.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * {@link PriceRate}의 복합 PK (organization_id, metric_code, dimension_values).
 *
 * <p>record가 아니라 클래스인 이유: JPA 스펙이 IdClass에 public no-arg 생성자를 요구하는데 record에는 없다 ({@code
 * BillableMetricId}와 같은 사정).
 */
public class PriceRateId implements Serializable {

  private UUID organizationId;
  private String metricCode;
  private String dimensionValues;

  public PriceRateId() {}

  public PriceRateId(UUID organizationId, String metricCode, String dimensionValues) {
    this.organizationId = organizationId;
    this.metricCode = metricCode;
    this.dimensionValues = dimensionValues;
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public String getMetricCode() {
    return metricCode;
  }

  public String getDimensionValues() {
    return dimensionValues;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof PriceRateId id
        && Objects.equals(organizationId, id.organizationId)
        && Objects.equals(metricCode, id.metricCode)
        && Objects.equals(dimensionValues, id.dimensionValues);
  }

  @Override
  public int hashCode() {
    return Objects.hash(organizationId, metricCode, dimensionValues);
  }
}
