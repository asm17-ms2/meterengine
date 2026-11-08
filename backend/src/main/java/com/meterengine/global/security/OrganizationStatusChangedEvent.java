package com.meterengine.global.security;

import java.util.UUID;

public record OrganizationStatusChangedEvent(UUID organizationId, boolean active) {}
