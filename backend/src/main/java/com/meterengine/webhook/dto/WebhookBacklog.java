package com.meterengine.webhook.dto;

public record WebhookBacklog(long dueCount, long oldestDueAgeSeconds) {}
