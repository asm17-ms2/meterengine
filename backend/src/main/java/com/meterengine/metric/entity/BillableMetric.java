package com.meterengine.metric.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/**
 * 과금 지표 (MS2-129에서 집계가 쓰는 범위).
 *
 * <p>이벤트의 event_type이 이 미터의 eventType과 맞으면 그 이벤트가 이 미터의 집계 대상이 된다. FK 없는 논리 매칭이다 (V1 마이그레이션 주석 참조).
 *
 * <p>aggregation을 enum이 아니라 String으로 두는 이유: enum이면 DB에 SUM 아닌 값이 있을 때 매핑 시점에 터진다. 그러면 그 미터와 무관한
 * 조회까지 같이 실패한다. 지원 여부 판정은 집계 서비스가 미터 단위로 한다.
 *
 * <p>단가는 여기 없다. MS2-158에서 price_policy/price_rate로 분리됐고, 금액 계산이 필요한 쪽이 단가를 따로 조회한다.
 */
@Entity
@IdClass(BillableMetricId.class)
public class BillableMetric implements Persistable<BillableMetricId> {

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

  @Transient private boolean isNew = true;

  /** Hibernate 전용. */
  protected BillableMetric() {}

  public BillableMetric(
      UUID organizationId,
      String code,
      String name,
      String eventType,
      String aggregation,
      String targetProperty) {
    this.organizationId = organizationId;
    this.code = code;
    this.name = name;
    this.eventType = eventType;
    this.aggregation = aggregation;
    this.targetProperty = targetProperty;
  }

  @PostLoad
  @PostPersist
  private void markNotNew() {
    this.isNew = false;
  }

  @Override
  public BillableMetricId getId() {
    return new BillableMetricId(organizationId, code);
  }

  @Override
  public boolean isNew() {
    return isNew;
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
}
