package com.meterengine.apikey.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("meterengine.api-key")
public record ApiKeyProperties(@DefaultValue("24h") Duration replacementGrace) {}
