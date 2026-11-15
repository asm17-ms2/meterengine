package com.meterengine.event.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties("meterengine.event.body-limit")
public record EventBodyLimitProperties(
    @DefaultValue("64KB") DataSize single, @DefaultValue("512KB") DataSize batch) {}
