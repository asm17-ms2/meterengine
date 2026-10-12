package com.meterengine.invoice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record FinalizeInvoicesResponse(
    String month, @JsonProperty("finalized_count") int finalizedCount) {}
