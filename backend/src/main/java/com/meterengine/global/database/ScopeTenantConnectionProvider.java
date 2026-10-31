package com.meterengine.global.database;

import java.sql.Connection;
import java.sql.SQLException;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;

public final class ScopeTenantConnectionProvider implements MultiTenantConnectionProvider<String> {

  private final SchemaRoutingDataSource dataSource;

  public ScopeTenantConnectionProvider(SchemaRoutingDataSource dataSource) {
    this.dataSource = dataSource;
  }

  @Override
  public Connection getAnyConnection() throws SQLException {
    // 부팅 메타데이터는 업무 표와 명시적 shared 매핑을 볼 수 있는 고정 test 역할로 조회한다.
    return dataSource.getConnection(DatabaseScope.TEST);
  }

  @Override
  public void releaseAnyConnection(Connection connection) throws SQLException {
    connection.close();
  }

  @Override
  public Connection getConnection(String tenantIdentifier) throws SQLException {
    return dataSource.getConnection(DatabaseScope.valueOf(tenantIdentifier));
  }

  @Override
  public void releaseConnection(String tenantIdentifier, Connection connection)
      throws SQLException {
    connection.close();
  }

  @Override
  public boolean supportsAggressiveRelease() {
    return false;
  }

  @Override
  public boolean handlesConnectionSchema() {
    return true;
  }

  @Override
  public boolean isUnwrappableAs(Class<?> type) {
    return type.isInstance(this);
  }

  @Override
  public <T> T unwrap(Class<T> type) {
    if (type.isInstance(this)) return type.cast(this);
    throw new IllegalArgumentException("unsupported connection provider type");
  }
}
