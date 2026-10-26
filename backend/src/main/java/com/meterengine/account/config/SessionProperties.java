package com.meterengine.account.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("meterengine.session")
public record SessionProperties(@DefaultValue("12h") Duration ttl) {}
