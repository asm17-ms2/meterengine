package com.meterengine.apikey.service;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public record ApiKeyTableContents(
    List<ApiKeySnapshot> snapshots, Set<UUID> activeOrganizationIds) {}
