package com.meterengine.event.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record IngestEventResponse(
    @JsonProperty("transaction_id") String transactionId, boolean duplicate) {

  public static IngestEventResponse stored(String transactionId) {
    return new IngestEventResponse(transactionId, false);
  }

  public static IngestEventResponse alreadyStored(String transactionId) {
    return new IngestEventResponse(transactionId, true);
  }
}
