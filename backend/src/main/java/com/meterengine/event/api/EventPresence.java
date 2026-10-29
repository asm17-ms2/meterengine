package com.meterengine.event.api;

import java.util.UUID;

public interface EventPresence {
  boolean exists(UUID organizationId, String type, String property);

  boolean exists(UUID organizationId, String type);
}
