package com.meterengine.mail.entity;

import com.meterengine.global.security.BusinessEnvironmentContext;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

@Entity
public class OutboundEmail implements Persistable<UUID> {

  public static final String INVOICE = "invoice";

  @Id private UUID id;

  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;

  @Column(name = "environment_id", updatable = false)
  private UUID environmentId;

  @Column(nullable = false)
  private String kind;

  @Column(nullable = false)
  private String period;

  @Column(name = "invoice_id")
  private UUID invoiceId;

  @Column(name = "recipient_email")
  private String recipientEmail;

  @Column(nullable = false)
  private String subject;

  @Column(name = "html_body", nullable = false)
  private String htmlBody;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  protected OutboundEmail() {}

  public OutboundEmail(
      UUID id,
      UUID organizationId,
      String kind,
      String period,
      UUID invoiceId,
      String recipientEmail,
      String subject,
      String htmlBody,
      OffsetDateTime createdAt) {
    this(
        id,
        organizationId,
        kind,
        period,
        invoiceId,
        recipientEmail,
        subject,
        htmlBody,
        createdAt,
        BusinessEnvironmentContext.environmentId());
  }

  public OutboundEmail(
      UUID id,
      UUID organizationId,
      String kind,
      String period,
      UUID invoiceId,
      String recipientEmail,
      String subject,
      String htmlBody,
      OffsetDateTime createdAt,
      UUID environmentId) {
    this.id = id;
    this.organizationId = organizationId;
    this.environmentId = environmentId;
    this.kind = kind;
    this.period = period;
    this.invoiceId = invoiceId;
    this.recipientEmail = recipientEmail;
    this.subject = subject;
    this.htmlBody = htmlBody;
    this.createdAt = createdAt;
  }

  @Override
  public UUID getId() {
    return id;
  }

  @Override
  public boolean isNew() {
    return true;
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public UUID getEnvironmentId() {
    return environmentId;
  }

  public String getKind() {
    return kind;
  }

  public String getPeriod() {
    return period;
  }

  public UUID getInvoiceId() {
    return invoiceId;
  }

  public String getRecipientEmail() {
    return recipientEmail;
  }

  public String getSubject() {
    return subject;
  }

  public String getHtmlBody() {
    return htmlBody;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }
}
