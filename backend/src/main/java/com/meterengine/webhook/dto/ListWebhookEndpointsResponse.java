package com.meterengine.webhook.dto;

import java.util.List;

public record ListWebhookEndpointsResponse(List<WebhookEndpointResponse> endpoints) {}
