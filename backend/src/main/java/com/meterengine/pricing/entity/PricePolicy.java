package com.meterengine.pricing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

/**
 * 가격 정책 (MS2-157). 미터 하나에 "가격을 가르는 이벤트 속성 키"의 선언이 하나 붙는다.
 *
 * <p>조합별 단가(price_rate)는 여기 없다. 단가는 유효 기간 같은 자체 수명을 가질 예정이라 정책과 등록 시점이 다르고, 관리 API도 MS2-177이 따로 만든다
 * (PR 43 리뷰 결정).
 *
 * <p><b>{@link Persistable}을 구현해 항상 새 행으로 취급한다.</b> save()가 기존 키를 만나면 UPDATE로 조용히 덮어쓰는데, 덮어쓰기는 청구가
 * 읽는 정책을 등록 요청으로 바꾸므로 지원하지 않는다. 항상 persist라서 중복은 제약 위반으로 터지고 서비스가 409로 바꾼다.
 */
@Entity
@IdClass(PricePolicyId.class)
public class PricePolicy implements Persistable<PricePolicyId> {

  @Id
  @Column(name = "organization_id")
  private UUID organizationId;

  @Id
  @Column(name = "billable_metric_code")
  private String billableMetricCode;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "dimension_properties", nullable = false)
  private List<String> dimensionProperties;

  /** Hibernate 전용. */
  protected PricePolicy() {}

  public PricePolicy(
      UUID organizationId, String billableMetricCode, List<String> dimensionProperties) {
    this.organizationId = organizationId;
    this.billableMetricCode = billableMetricCode;
    this.dimensionProperties = List.copyOf(dimensionProperties);
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public String getBillableMetricCode() {
    return billableMetricCode;
  }

  public List<String> getDimensionProperties() {
    return dimensionProperties;
  }

  @Override
  public PricePolicyId getId() {
    return new PricePolicyId(organizationId, billableMetricCode);
  }

  @Override
  public boolean isNew() {
    return true;
  }
}
