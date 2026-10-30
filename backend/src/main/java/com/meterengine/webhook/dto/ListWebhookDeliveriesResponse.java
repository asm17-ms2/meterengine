package com.meterengine.webhook.dto;

import java.util.List;

public record ListWebhookDeliveriesResponse(
    int page, int size, long total, List<WebhookDeliveryResponse> deliveries) {}
