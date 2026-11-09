package com.meterengine.global.security;

import java.util.Set;
import java.util.UUID;

public interface ActiveOrganizations {
  boolean contains(UUID organizationId);

  Set<UUID> list();
}
