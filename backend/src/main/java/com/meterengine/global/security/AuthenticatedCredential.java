package com.meterengine.global.security;

public record AuthenticatedCredential(
    AuthenticatedPrincipal principal, Integer rateLimitPerMinute) {}
