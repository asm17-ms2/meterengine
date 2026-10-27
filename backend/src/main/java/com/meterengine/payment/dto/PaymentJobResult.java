package com.meterengine.payment.dto;

public record PaymentJobResult(int processedCount, int skippedCount, int failedCount) {}
