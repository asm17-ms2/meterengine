package com.meterengine.metric.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CustomerUsage(UUID customerId, String customerName, BigDecimal quantity) {}
