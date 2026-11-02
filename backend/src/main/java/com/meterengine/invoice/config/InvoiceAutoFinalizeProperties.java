package com.meterengine.invoice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("meterengine.invoice.auto-finalize")
public record InvoiceAutoFinalizeProperties(@DefaultValue("false") boolean enabled) {}
