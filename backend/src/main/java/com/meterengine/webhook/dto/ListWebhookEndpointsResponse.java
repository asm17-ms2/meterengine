package com.meterengine.webhook.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record ListWebhookEndpointsResponse(
    @Schema(description = "웹훅 URL 목록입니다. 등록한 순서대로 싣습니다.")
        List<WebhookEndpointResponse> endpoints) {}
