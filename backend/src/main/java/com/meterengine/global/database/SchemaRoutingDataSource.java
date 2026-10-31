package com.meterengine.global.database;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.metrics.micrometer.MicrometerMetricsTrackerFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.jdbc.datasource.AbstractDataSource;

public final class SchemaRoutingDataSource extends AbstractDataSource
    implements AutoCloseable, MeterBinder {

  private final Map<DatabaseScope, HikariDataSource> poolByScope;

  SchemaRoutingDataSource(Map<DatabaseScope, HikariDataSource> pools) {
    poolByScope = new EnumMap<>(pools);
    for (var scope : DatabaseScope.values()) {
      if (poolByScope.get(scope) == null)
        throw new IllegalArgumentException("all scope pools required");
    }
  }

  @Override
  public Connection getConnection() throws SQLException {
    return getConnection(DatabaseScopes.current());
  }

  Connection getConnection(DatabaseScope scope) throws SQLException {
    return poolByScope.get(scope).getConnection();
  }

  @Override
  public Connection getConnection(String username, String password) throws SQLException {
    throw new SQLException("per-call database credentials are not supported");
  }

  @Override
  public synchronized void bindTo(MeterRegistry registry) {
    for (var pool : poolByScope.values()) {
      if (!pool.isClosed()
          && pool.getMetricRegistry() == null
          && pool.getMetricsTrackerFactory() == null) {
        pool.setMetricsTrackerFactory(new MicrometerMetricsTrackerFactory(registry));
      }
    }
  }

  @Override
  public synchronized void close() {
    poolByScope.values().forEach(HikariDataSource::close);
  }
}
