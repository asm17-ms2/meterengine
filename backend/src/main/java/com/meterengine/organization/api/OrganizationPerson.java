package com.meterengine.organization.api;

import java.util.UUID;

public record OrganizationPerson(UUID id, String email, String name, boolean consentRequired) {}
