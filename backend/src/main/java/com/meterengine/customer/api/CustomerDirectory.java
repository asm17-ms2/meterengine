package com.meterengine.customer.api;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public interface CustomerDirectory {
  boolean exists(UUID organizationId, UUID customerId);

  void check(UUID organizationId, UUID customerId);

  Map<UUID, Boolean> active(UUID organizationId, Collection<UUID> customerIds);
}
