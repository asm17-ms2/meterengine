package com.meterengine.global.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jdbc.autoconfigure.metrics.DataSourcePoolMetricsAutoConfiguration;
import org.springframework.boot.micrometer.metrics.autoconfigure.CompositeMeterRegistryAutoConfiguration;
import org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration;
import org.springframework.boot.micrometer.metrics.autoconfigure.export.simple.SimpleMetricsExportAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.datasource.AbstractDataSource;

class SchemaRoutingDataSourceMetricsTest {

  private static final Set<String> REQUIRED_METERS =
      Set.of(
          "hikaricp.connections.usage",
          "hikaricp.connections.pending",
          "hikaricp.connections.acquire",
          "hikaricp.connections.active",
          "hikaricp.connections.max",
          "hikaricp.connections.timeout");

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  MetricsAutoConfiguration.class,
                  SimpleMetricsExportAutoConfiguration.class,
                  CompositeMeterRegistryAutoConfiguration.class,
                  DataSourcePoolMetricsAutoConfiguration.class))
          .withPropertyValues("management.metrics.use-global-registry=false")
          .withBean(
              "dataSource",
              SchemaRoutingDataSource.class,
              SchemaRoutingDataSourceMetricsTest::pools);

  @Test
  void Boot_registry는_논리_DataSource_내부_세_pool의_기존_Hikari_지표를_구분한다() {
    runner.run(
        context -> {
          assertThat(context).hasNotFailed().hasSingleBean(DataSource.class);
          var registry = context.getBean(MeterRegistry.class);
          var dataSource = context.getBean(SchemaRoutingDataSource.class);
          for (var scope : DatabaseScope.values()) {
            try (var connection = dataSource.getConnection(scope)) {
              assertThat(connection.isValid(1)).isTrue();
            }
            String name = poolName(scope);
            assertThat(
                    registry.getMeters().stream()
                        .filter(meter -> name.equals(meter.getId().getTag("pool")))
                        .map(meter -> meter.getId().getName())
                        .collect(Collectors.toSet()))
                .containsAll(REQUIRED_METERS);
            assertThat(
                    registry.get("hikaricp.connections.acquire").tag("pool", name).timer().count())
                .isEqualTo(1);
            assertThat(registry.get("hikaricp.connections.usage").tag("pool", name).timer().count())
                .isEqualTo(1);
            assertThat(registry.get("hikaricp.connections.max").tag("pool", name).gauge().value())
                .isEqualTo(1);
            System.out.println("S2 metrics: pool=" + name + " acquire=1 usage=1 max=1");
          }
          assertThat(hikariMeterCount(registry)).isEqualTo(30);
          assertThat(
                  registry.getMeters().stream()
                      .filter(meter -> meter.getId().getName().startsWith("hikaricp."))
                      .map(meter -> meter.getId().getTag("pool"))
                      .collect(Collectors.toSet()))
              .containsExactlyInAnyOrder(
                  "meterengine-shared", "meterengine-test", "meterengine-live");
        });
  }

  @Test
  void 같은_registry에_재바인딩해도_중복되지_않고_pool_종료_뒤_지표를_제거한다() {
    runner.run(
        context -> {
          var registry = context.getBean(MeterRegistry.class);
          var dataSource = context.getBean(SchemaRoutingDataSource.class);
          assertThat(hikariMeterCount(registry)).isEqualTo(30);
          assertThatCode(
                  () ->
                      context
                          .getBeansOfType(MeterBinder.class)
                          .values()
                          .forEach(binder -> binder.bindTo(registry)))
              .doesNotThrowAnyException();
          assertThat(hikariMeterCount(registry)).isEqualTo(30);
          dataSource.close();
          assertThat(hikariMeterCount(registry)).isZero();
          assertThatCode(
                  () ->
                      context
                          .getBeansOfType(MeterBinder.class)
                          .values()
                          .forEach(binder -> binder.bindTo(registry)))
              .doesNotThrowAnyException();
          assertThat(hikariMeterCount(registry)).isZero();
          assertThatCode(dataSource::close).doesNotThrowAnyException();
        });
  }

  static long hikariMeterCount(MeterRegistry registry) {
    return registry.getMeters().stream()
        .filter(meter -> meter.getId().getName().startsWith("hikaricp."))
        .count();
  }

  static String poolName(DatabaseScope scope) {
    return "meterengine-" + scope.name().toLowerCase(Locale.ROOT);
  }

  static SchemaRoutingDataSource pools() {
    var poolByScope = new EnumMap<DatabaseScope, HikariDataSource>(DatabaseScope.class);
    for (var scope : DatabaseScope.values()) {
      var config = new HikariConfig();
      config.setPoolName(poolName(scope));
      config.setMaximumPoolSize(1);
      config.setMinimumIdle(0);
      config.setDataSource(
          new AbstractDataSource() {
            @Override
            public Connection getConnection() throws SQLException {
              return connection();
            }

            @Override
            public Connection getConnection(String username, String password) throws SQLException {
              return connection();
            }
          });
      poolByScope.put(scope, new HikariDataSource(config));
    }
    return new SchemaRoutingDataSource(poolByScope);
  }

  static Connection connection() throws SQLException {
    var result = mock(Connection.class);
    when(result.getAutoCommit()).thenReturn(true);
    when(result.isValid(1)).thenReturn(true);
    when(result.getTransactionIsolation()).thenReturn(Connection.TRANSACTION_READ_COMMITTED);
    return result;
  }
}
