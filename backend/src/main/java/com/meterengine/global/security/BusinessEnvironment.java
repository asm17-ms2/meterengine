package com.meterengine.global.security;

import com.meterengine.global.database.DatabaseScope;
import java.util.UUID;

public record BusinessEnvironment(UUID organizationId, UUID environmentId, String mode) {
  public DatabaseScope databaseScope() {
    return switch (mode) {
      case "test" -> DatabaseScope.TEST;
      case "live" -> DatabaseScope.LIVE;
      default -> throw new IllegalStateException("Explicit business environment required");
    };
  }
}
