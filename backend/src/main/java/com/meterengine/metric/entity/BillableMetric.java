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

@Entity
@IdClass(BillableMetricId.class)
public class BillableMetric implements Persistable<BillableMetricId> {

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

  @Column(name = "target_property")
  private String targetProperty;

  @Transient private boolean isNew = true;

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
