package com.meterengine.scheduling.dto;

public record ScheduledJobResult(int processedCount, int skippedCount, int failedCount) {}
