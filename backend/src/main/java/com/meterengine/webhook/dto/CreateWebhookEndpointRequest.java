package com.meterengine.webhook.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.global.validation.StorableText;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateWebhookEndpointRequest(
    @NotBlank @Size(max = 2048) @StorableText String url,
    @NotEmpty @Size(max = 20) @JsonProperty("event_types")
        List<@NotBlank @StorableText String> eventTypes,
    @Size(max = 1000) @StorableText String description) {}
