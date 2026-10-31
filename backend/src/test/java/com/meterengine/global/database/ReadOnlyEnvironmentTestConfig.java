package com.meterengine.global.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Configuration;

@Configuration
@TestComponent
public class ReadOnlyEnvironmentTestConfig extends SideEffectEnvironmentTestConfig {
  @Override
  protected boolean activateTarget() {
    return false;
  }

  @Override
  protected HikariDataSource pool(HikariConfig config) {
    var source = super.pool(config);
    try {
      ReadOnlyDatabaseVerifier.verify(
          source,
          DatabaseScope.valueOf(
              config
                  .getUsername()
                  .substring("meterengine_".length())
                  .toUpperCase(java.util.Locale.ROOT)));
      return source;
    } catch (RuntimeException exception) {
      source.close();
      throw exception;
    }
  }
}
