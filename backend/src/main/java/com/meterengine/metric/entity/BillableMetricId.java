package com.meterengine.metric.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * {@link BillableMetric}의 복합 PK (organization_id, code).
 *
 * <p>record가 아니라 클래스인 이유: JPA 스펙이 IdClass에 public no-arg 생성자를 요구하는데 record에는 없다.
 *
 * <p>미터가 도입사별로 code를 따로 갖는 구조라 PK가 복합이다. code만으로는 도입사가 다른 두 미터를 구별하지 못한다 (V1 마이그레이션 참조).
 */
public class BillableMetricId implements Serializable {

  private UUID organizationId;
  private String code;

  public BillableMetricId() {}

  public BillableMetricId(UUID organizationId, String code) {
    this.organizationId = organizationId;
    this.code = code;
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public String getCode() {
    return code;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof BillableMetricId id
        && Objects.equals(organizationId, id.organizationId)
        && Objects.equals(code, id.code);
  }

  @Override
  public int hashCode() {
    return Objects.hash(organizationId, code);
  }
}
