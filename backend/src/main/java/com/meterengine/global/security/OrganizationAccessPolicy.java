package com.meterengine.global.security;

import java.util.UUID;

public interface OrganizationAccessPolicy {
  void check(UUID organizationId);
}
