package com.meterengine.pricing.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * {@link PricePolicy}의 복합 PK (organization_id, metric_code).
 *
 * <p>record가 아니라 클래스인 이유: JPA 스펙이 IdClass에 public no-arg 생성자를 요구하는데 record에는 없다 ({@code
 * BillableMetricId}와 같은 사정).
 */
public class PricePolicyId implements Serializable {

  private UUID organizationId;
  private String metricCode;

  public PricePolicyId() {}

  public PricePolicyId(UUID organizationId, String metricCode) {
    this.organizationId = organizationId;
    this.metricCode = metricCode;
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public String getMetricCode() {
    return metricCode;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof PricePolicyId id
        && Objects.equals(organizationId, id.organizationId)
        && Objects.equals(metricCode, id.metricCode);
  }

  @Override
  public int hashCode() {
    return Objects.hash(organizationId, metricCode);
  }
}
