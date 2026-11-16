package com.meterengine.event.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import tools.jackson.databind.JsonNode;

public record IngestEventBatchRequest(
    @NotEmpty @Size(min = 1, max = 100) @ArraySchema(
            arraySchema =
                @Schema(
                    description =
                        "보낼 이벤트 배열로 1건부터 100건까지 받습니다. 각 이벤트의 형식은 단건 수집(POST /v1/events) 요청과 같습니다."),
            schema = @Schema(implementation = IngestEventRequest.class),
            minItems = 1,
            maxItems = 100)
        List<JsonNode> events) {}
