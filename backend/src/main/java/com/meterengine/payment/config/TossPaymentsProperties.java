package com.meterengine.payment.config;

import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "tosspayments")
public record TossPaymentsProperties(
    @NotBlank String secretKey, @DefaultValue("https://api.tosspayments.com") URI baseUrl) {}
