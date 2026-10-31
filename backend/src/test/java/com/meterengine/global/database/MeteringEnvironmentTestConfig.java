package com.meterengine.global.database;

import com.meterengine.query.repository.WorkspaceQueryRepository;
import java.util.stream.Stream;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@TestComponent
@ComponentScan(
    basePackages = {
      "com.meterengine.account",
      "com.meterengine.organization",
      "com.meterengine.apikey",
      "com.meterengine.customer",
      "com.meterengine.audit",
      "com.meterengine.idempotency",
      "com.meterengine.global.config",
      "com.meterengine.global.error",
      "com.meterengine.event",
      "com.meterengine.metric",
      "com.meterengine.pricing"
    })
@EnableJpaRepositories(
    basePackages = {"com.meterengine.metric.repository", "com.meterengine.pricing.repository"})
@Import(WorkspaceQueryRepository.class)
public class MeteringEnvironmentTestConfig extends EnvironmentTestConfig {
  public static final java.util.concurrent.atomic.AtomicReference<
          java.util.function.Consumer<String>>
      AFTER_QUERY = new java.util.concurrent.atomic.AtomicReference<>(sql -> {});

  @Override
  protected com.zaxxer.hikari.HikariDataSource pool(com.zaxxer.hikari.HikariConfig config) {
    return new TracingPool(config) {
      @Override
      public java.sql.Connection getConnection() throws java.sql.SQLException {
        var connection = super.getConnection();
        return (java.sql.Connection)
            java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[] {java.sql.Connection.class},
                (proxy, method, args) -> {
                  Object result = invoke(method, connection, args);
                  if (method.getName().equals("prepareStatement")
                      && result instanceof java.sql.PreparedStatement statement) {
                    String sql = (String) args[0];
                    return java.lang.reflect.Proxy.newProxyInstance(
                        getClass().getClassLoader(),
                        new Class<?>[] {java.sql.PreparedStatement.class},
                        (statementProxy, statementMethod, statementArgs) -> {
                          Object value = invoke(statementMethod, statement, statementArgs);
                          if (statementMethod.getName().equals("executeQuery"))
                            AFTER_QUERY.get().accept(sql);
                          return value;
                        });
                  }
                  return result;
                });
      }
    };
  }

  private static Object invoke(java.lang.reflect.Method method, Object target, Object[] args)
      throws Throwable {
    try {
      return method.invoke(target, args);
    } catch (java.lang.reflect.InvocationTargetException exception) {
      throw exception.getCause();
    }
  }

  @Override
  protected String[] managedTypes() {
    return Stream.concat(
            Stream.of(super.managedTypes()),
            Stream.of(
                "com.meterengine.metric.entity.BillableMetric",
                "com.meterengine.metric.entity.BillableMetricPeriod",
                "com.meterengine.pricing.entity.PriceRate",
                "com.meterengine.pricing.entity.PricePlan"))
        .toArray(String[]::new);
  }
}
