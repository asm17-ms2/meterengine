package com.meterengine.organization.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.organization.entity.Organization;

public record SupplierResponse(
    @JsonProperty("business_registration_number") String businessRegistrationNumber,
    @JsonProperty("representative_name") String representativeName,
    @JsonProperty("business_address") String businessAddress) {
  public static SupplierResponse from(Organization organization) {
    return new SupplierResponse(
        organization.getBusinessRegistrationNumber(),
        organization.getRepresentativeName(),
        organization.getBusinessAddress());
  }
}
