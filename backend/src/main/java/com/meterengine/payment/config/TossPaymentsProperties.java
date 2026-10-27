package com.meterengine.payment.config;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "tosspayments")
public record TossPaymentsProperties(@DefaultValue("https://api.tosspayments.com") URI baseUrl) {}
