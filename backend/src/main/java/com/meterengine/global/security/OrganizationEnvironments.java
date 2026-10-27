package com.meterengine.global.security;

import java.util.List;
import java.util.UUID;

public interface OrganizationEnvironments {
  BusinessEnvironment resolve(UUID organizationId, String mode);

  List<BusinessEnvironment> list(UUID organizationId);
}
