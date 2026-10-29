package com.meterengine.metric.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

@Entity
@IdClass(BillableMetricId.class)
public class BillableMetric implements Persistable<BillableMetricId> {

  public static final String SUM = "sum";

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

  @Column(name = "unit_size", nullable = false)
  private BigDecimal unitSize = BigDecimal.ONE;

  @Column(name = "rounding_mode", nullable = false)
  private String roundingMode = "none";

  @Column(name = "rounding_precision", nullable = false)
  private int roundingPrecision;

  @Column(name = "percentile")
  private BigDecimal percentile;

  @Column(name = "target_property")
  private String targetProperty;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "dimension_properties", nullable = false)
  private List<String> dimensionProperties = List.of();

  @Transient private boolean isNew = true;

  protected BillableMetric() {}

  public BillableMetric(
      UUID organizationId,
      String code,
      String name,
      String eventType,
      String aggregation,
      String targetProperty,
      List<String> dimensionProperties) {
    this.organizationId = organizationId;
    this.code = code;
    this.name = name;
    this.eventType = eventType;
    this.aggregation = aggregation;
    this.targetProperty = targetProperty;
    this.dimensionProperties = List.copyOf(dimensionProperties);
  }

  public BillableMetric(
      UUID organizationId,
      String code,
      String name,
      String eventType,
      String aggregation,
      String targetProperty,
      List<String> dimensionProperties,
      BigDecimal unitSize,
      String roundingMode,
      int roundingPrecision,
      BigDecimal percentile) {
    this(organizationId, code, name, eventType, aggregation, targetProperty, dimensionProperties);
    this.unitSize = unitSize;
    this.roundingMode = roundingMode;
    this.roundingPrecision = roundingPrecision;
    this.percentile = percentile;
  }

  public void update(
      String name,
      String eventType,
      String aggregation,
      String targetProperty,
      List<String> dimensionProperties,
      BigDecimal unitSize,
      String roundingMode,
      int roundingPrecision,
      BigDecimal percentile) {
    update(name, eventType, aggregation, targetProperty, dimensionProperties);
    this.unitSize = unitSize;
    this.roundingMode = roundingMode;
    this.roundingPrecision = roundingPrecision;
    this.percentile = percentile;
  }

  public BigDecimal getUnitSize() {
    return unitSize;
  }

  public String getRoundingMode() {
    return roundingMode;
  }

  public int getRoundingPrecision() {
    return roundingPrecision;
  }

  public BigDecimal getPercentile() {
    return percentile;
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

  public boolean hasEventTypeAndTargetProperty(String eventType, String targetProperty) {
    return Objects.equals(this.eventType, eventType)
        && Objects.equals(this.targetProperty, targetProperty);
  }

  public void update(
      String name,
      String eventType,
      String aggregation,
      String targetProperty,
      List<String> dimensionProperties) {
    this.name = name;
    this.eventType = eventType;
    this.aggregation = aggregation;
    this.targetProperty = targetProperty;
    this.dimensionProperties = List.copyOf(dimensionProperties);
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

  public List<String> getDimensionProperties() {
    return dimensionProperties;
  }
}
