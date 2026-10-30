package com.meterengine.event.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import tools.jackson.databind.JsonNode;

public record IngestEventBatchRequest(
    @NotEmpty @Size(min = 1, max = 100) @ArraySchema(
            schema = @Schema(implementation = IngestEventRequest.class),
            minItems = 1,
            maxItems = 100)
        List<JsonNode> events) {}
