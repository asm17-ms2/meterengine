package com.meterengine.customer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.customer.entity.Customer;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CustomerResponse(
    UUID id,
    String name,
    @Schema(description = "청구 메일을 받을 주소. 등록하지 않았으면 null") @JsonProperty("billing_email")
        String billingEmail,
    @JsonProperty("created_at") OffsetDateTime createdAt,
    @JsonProperty("external_id") String externalId,
    @JsonProperty("archived_at") OffsetDateTime archivedAt,
    long version) {

  public CustomerResponse(
      UUID id,
      String name,
      String billingEmail,
      OffsetDateTime createdAt,
      String externalId,
      OffsetDateTime archivedAt) {
    this(id, name, billingEmail, createdAt, externalId, archivedAt, 0);
  }

  public static CustomerResponse from(Customer customer) {
    return new CustomerResponse(
        customer.getId(),
        customer.getName(),
        customer.getBillingEmail(),
        customer.getCreatedAt(),
        customer.getExternalId(),
        customer.getArchivedAt(),
        customer.getVersion());
  }
}
