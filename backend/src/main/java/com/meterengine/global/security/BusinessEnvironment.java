package com.meterengine.global.security;

import java.util.UUID;

public record BusinessEnvironment(UUID organizationId, UUID environmentId, String mode) {}
