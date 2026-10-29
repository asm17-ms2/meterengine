package com.meterengine.metric.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.util.UUID;

@Entity
public class BillableMetricPeriod {
  @Id private UUID id;

  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;

  @Column(name = "billable_metric_code", nullable = false)
  private String billableMetricCode;

  @Column(name = "active_from", nullable = false)
  private String activeFrom;

  @Column(name = "active_until", nullable = false)
  private String activeUntil;

  protected BillableMetricPeriod() {}

  public BillableMetricPeriod(UUID organizationId, String code, String from, String until) {
    id = UUID.randomUUID();
    this.organizationId = organizationId;
    billableMetricCode = code;
    activeFrom = from;
    activeUntil = until;
  }

  public String getBillableMetricCode() {
    return billableMetricCode;
  }

  public boolean includes(String month) {
    return activeFrom.compareTo(month) <= 0 && activeUntil.compareTo(month) > 0;
  }
}
