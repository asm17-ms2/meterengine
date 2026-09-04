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

  @Column(name = "invoice_id", nullable = false)
  private UUID invoiceId;

  @Column(name = "billable_metric_code", nullable = false)
  private String billableMetricCode;

  @Column(name = "target_property")
  private String targetProperty;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "dimension_values", nullable = false)
  private String dimensionValues;

  @Column(nullable = false)
  private BigDecimal quantity;

  @Column(name = "unit_price", nullable = false)
  private BigDecimal unitPrice;

  @Column(nullable = false)
  private long amount;

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
    this.id = id;
    this.organizationId = organizationId;
    this.invoiceId = invoiceId;
    this.billableMetricCode = billableMetricCode;
    this.targetProperty = targetProperty;
    this.dimensionValues = dimensionValues;
    this.quantity = quantity;
    this.unitPrice = unitPrice;
    this.amount = amount;
  }

  public UUID getId() {
    return id;
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
}
