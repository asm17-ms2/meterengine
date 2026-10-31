package com.meterengine.global.database;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;

public final class ScopeTenantIdentifierResolver
    implements CurrentTenantIdentifierResolver<String> {

  @Override
  public String resolveCurrentTenantIdentifier() {
    return DatabaseScopes.current().name();
  }

  @Override
  public boolean validateExistingCurrentSessions() {
    return true;
  }
}
