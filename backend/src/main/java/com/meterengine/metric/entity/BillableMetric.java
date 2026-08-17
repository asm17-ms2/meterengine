package com.meterengine.metric.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * 과금 지표 (MS2-129에서 집계가 쓰는 범위).
 *
 * <p>이벤트의 event_type이 이 미터의 eventType과 맞으면 그 이벤트가 이 미터의 집계 대상이 된다. FK 없는 논리 매칭이다 (V1 마이그레이션 주석 참조).
 *
 * <p>aggregation을 enum이 아니라 String으로 두는 이유: enum이면 DB에 SUM 아닌 값이 있을 때 매핑 시점에 터진다. 그러면 그 미터와 무관한
 * 조회까지 같이 실패한다. 지원 여부 판정은 집계 서비스가 미터 단위로 한다.
 *
 * <p>unitPrice는 이번 슬라이스의 집계가 쓰지 않는다. 금액 계산은 MS2-124의 몫이고, 그때 미터를 다시 조회하지 않도록 여기 실어 둔다. NUMERIC이라
 * BigDecimal이다. 토큰당 1원 미만 단가를 double로 받으면 청구 근거가 조용히 어긋난다.
 */
@Entity
@IdClass(BillableMetricId.class)
public class BillableMetric {

  /** 이번 슬라이스가 구현한 유일한 집계 방식이다 (MS2-129 팀 결정). */
  public static final String SUM = "SUM";

  @Id
  @Column(name = "organization_id")
  private UUID organizationId;

  @Id private String code;

  @Column(nullable = false)
  private String name;

  @Column(name = "event_type", nullable = false)
  private String eventType;

  @Column(nullable = false)
  private String aggregation;

  /** SUM 집계가 properties에서 읽을 키 이름. COUNT 미터에는 없어서 nullable이다. */
  @Column(name = "target_property")
  private String targetProperty;

  @Column(name = "unit_price", nullable = false)
  private BigDecimal unitPrice;

  /** Hibernate 전용. */
  protected BillableMetric() {}

  public BillableMetric(
      UUID organizationId,
      String code,
      String name,
      String eventType,
      String aggregation,
      String targetProperty,
      BigDecimal unitPrice) {
    this.organizationId = organizationId;
    this.code = code;
    this.name = name;
    this.eventType = eventType;
    this.aggregation = aggregation;
    this.targetProperty = targetProperty;
    this.unitPrice = unitPrice;
  }

  public boolean isSum() {
    return SUM.equals(aggregation);
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public String getCode() {
    return code;
  }

  public String getName() {
    return name;
  }

  public String getEventType() {
    return eventType;
  }

  public String getAggregation() {
    return aggregation;
  }

  public String getTargetProperty() {
    return targetProperty;
  }

  public BigDecimal getUnitPrice() {
    return unitPrice;
  }
}
