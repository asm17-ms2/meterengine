package com.meterengine.payment;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "tosspayments")
public record TossPaymentsProperties(@NotBlank String secretKey) {}
