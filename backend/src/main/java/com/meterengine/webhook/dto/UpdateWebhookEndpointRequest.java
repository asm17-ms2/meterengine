package com.meterengine.webhook.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.global.validation.StorableText;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UpdateWebhookEndpointRequest(
    @NotBlank @Size(max = 2048) @StorableText String url,
    @ArraySchema(
            schema =
                @Schema(
                    allowableValues = {
                      "webhook_test",
                      "unbilled_usage_detected",
                      "payment_succeeded",
                      "payment_failed",
                      "payment_canceled"
                    }))
        @NotEmpty @Size(min = 1, max = 20) @JsonProperty("event_types")
        List<@NotBlank @StorableText String> eventTypes,
    @NotNull Boolean enabled,
    @Size(max = 1000) @StorableText String description) {}
