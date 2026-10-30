package com.meterengine.invoice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.math.BigDecimal;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
public class InvoiceLine {

  @Id private UUID id;

  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;

  @Column(name = "environment_id", updatable = false)
  private UUID environmentId;

  @Column(name = "invoice_id", nullable = false)
  private UUID invoiceId;

  @Column(name = "billable_metric_code")
  private String billableMetricCode;

  @Column(name = "billable_metric_name", updatable = false)
  private String billableMetricName;

  @Column(name = "target_property")
  private String targetProperty;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "dimension_values", nullable = false)
  private String dimensionValues;

  @Column(nullable = false)
  private BigDecimal quantity;

  @Column(name = "quantity_numerator")
  private BigDecimal quantityNumerator;

  @Column(name = "quantity_denominator")
  private BigDecimal quantityDenominator;

  @Column(name = "unit_price", nullable = false)
  private BigDecimal unitPrice;

  @Column(nullable = false)
  private long amount;

  @Column(name = "line_type", nullable = false)
  private String lineType;

  @Column(name = "tier_index", nullable = false)
  private int tierIndex;

  protected InvoiceLine() {}

  public InvoiceLine(
      UUID id,
      UUID organizationId,
      UUID invoiceId,
      String billableMetricCode,
      String targetProperty,
      String dimensionValues,
      BigDecimal quantity,
      BigDecimal unitPrice,
      long amount) {
    this(
        id,
        organizationId,
        invoiceId,
        billableMetricCode,
        targetProperty,
        dimensionValues,
        quantity,
        unitPrice,
        amount,
        "per_unit",
        0);
  }

  public InvoiceLine(
      UUID id,
      UUID organizationId,
      UUID invoiceId,
      String billableMetricCode,
      String targetProperty,
      String dimensionValues,
      BigDecimal quantity,
      BigDecimal unitPrice,
      long amount,
      String lineType,
      int tierIndex) {
    this.lineType = lineType;
    this.tierIndex = tierIndex;
    this.id = id;
    this.organizationId = organizationId;
    this.invoiceId = invoiceId;
    this.billableMetricCode = billableMetricCode;
    this.targetProperty = targetProperty;
    this.dimensionValues = dimensionValues;
    this.quantity = quantity;
    this.quantityNumerator = quantity;
    this.quantityDenominator = BigDecimal.ONE;
    this.unitPrice = unitPrice;
    this.amount = amount;
  }

  public InvoiceLine(
      UUID id,
      UUID organizationId,
      UUID invoiceId,
      String billableMetricCode,
      String targetProperty,
      String dimensionValues,
      BigDecimal quantity,
      BigDecimal unitPrice,
      long amount,
      String lineType,
      int tierIndex,
      BigDecimal quantityNumerator,
      BigDecimal quantityDenominator) {
    this(
        id,
        organizationId,
        invoiceId,
        billableMetricCode,
        targetProperty,
        dimensionValues,
        quantity,
        unitPrice,
        amount,
        lineType,
        tierIndex);
    this.quantityNumerator = quantityNumerator;
    this.quantityDenominator = quantityDenominator;
  }

  public BigDecimal getQuantityNumerator() {
    return quantityNumerator == null ? quantity : quantityNumerator;
  }

  public BigDecimal getQuantityDenominator() {
    return quantityDenominator == null ? BigDecimal.ONE : quantityDenominator;
  }

  public String getLineType() {
    return lineType;
  }

  public int getTierIndex() {
    return tierIndex;
  }

  public UUID getId() {
    return id;
  }

  public UUID getEnvironmentId() {
    return environmentId;
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public UUID getInvoiceId() {
    return invoiceId;
  }

  public String getBillableMetricCode() {
    return billableMetricCode;
  }

  public String getTargetProperty() {
    return targetProperty;
  }

  public String getDimensionValues() {
    return dimensionValues;
  }

  public BigDecimal getQuantity() {
    return quantity;
  }

  public BigDecimal getUnitPrice() {
    return unitPrice;
  }

  public long getAmount() {
    return amount;
  }

  public InvoiceLine(
      UUID id,
      UUID organizationId,
      UUID invoiceId,
      String billableMetricCode,
      String targetProperty,
      String dimensionValues,
      BigDecimal quantity,
      BigDecimal unitPrice,
      long amount,
      String lineType,
      int tierIndex,
      BigDecimal quantityNumerator,
      BigDecimal quantityDenominator,
      UUID environmentId,
      String billableMetricName) {
    this(
        id,
        organizationId,
        invoiceId,
        billableMetricCode,
        targetProperty,
        dimensionValues,
        quantity,
        unitPrice,
        amount,
        lineType,
        tierIndex,
        quantityNumerator,
        quantityDenominator);
    this.environmentId = environmentId;
    this.billableMetricName = billableMetricName;
  }

  public String getBillableMetricName() {
    return billableMetricName;
  }
}
