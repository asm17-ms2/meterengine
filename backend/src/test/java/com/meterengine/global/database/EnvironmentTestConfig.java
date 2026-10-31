package com.meterengine.global.database;

import com.meterengine.global.migration.DatabaseBootstrap;
import com.meterengine.global.migration.MigrationSettings;
import com.meterengine.global.migration.SchemaMigrator;
import com.meterengine.global.security.SecurityConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.DriverManager;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Configuration
@TestComponent
@EnableAutoConfiguration
@EnableTransactionManagement
@ConfigurationPropertiesScan(
    basePackages = {"com.meterengine.account.config", "com.meterengine.apikey.config"})
@ComponentScan(
    basePackages = {
      "com.meterengine.account",
      "com.meterengine.organization",
      "com.meterengine.apikey",
      "com.meterengine.customer",
      "com.meterengine.audit",
      "com.meterengine.idempotency",
      "com.meterengine.global.config",
      "com.meterengine.global.error"
    })
@EnableJpaRepositories(
    basePackages = {
      "com.meterengine.account.repository",
      "com.meterengine.organization.repository",
      "com.meterengine.apikey.repository",
      "com.meterengine.customer.repository",
      "com.meterengine.audit.repository"
    })
@Import({
  DatabaseScopeFilter.class,
  SecurityConfig.class,
  CutoverPolicy.class,
  CutoverFilter.class,
  EnvironmentSideEffectPolicy.class,
  PublicResourceLocator.class,
  com.meterengine.global.metrics.DatabaseOperationTimer.class
})
public class EnvironmentTestConfig extends DatabaseConfig {
  @Bean(destroyMethod = "stop")
  PostgreSQLContainer environmentPostgres() {
    var postgres = new PostgreSQLContainer("postgres:18.4-alpine").withDatabaseName("postgres");
    postgres.start();
    var environment = new HashMap<String, String>();
    environment.put("DB_NAME", "meterengine_app");
    environment.put("DB_HOST", postgres.getHost());
    environment.put("DB_PORT", postgres.getMappedPort(5432).toString());
    environment.put("DB_SSL_ROOT_CERT", "/fixture/ca.pem");
    environment.put("DB_MIGRATION_USERNAME", postgres.getUsername());
    environment.put("DB_MIGRATION_PASSWORD", postgres.getPassword());
    for (var scope : DatabaseScope.values()) {
      environment.put(
          "DB_" + scope + "_USERNAME", "meterengine_" + scope.name().toLowerCase(Locale.ROOT));
      environment.put("DB_" + scope + "_PASSWORD", "fixture-password");
    }
    var owner =
        new DriverManagerDataSource(
            postgres.getJdbcUrl().replace("/postgres", "/meterengine_app"),
            postgres.getUsername(),
            postgres.getPassword());
    try (var connection =
        DriverManager.getConnection(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
      DatabaseBootstrap.run(connection, owner, MigrationSettings.from(environment), environment);
      var migrator = new SchemaMigrator(owner);
      migrator.migrate();
      if (activateTarget()) migrator.activate(true, "s3-disposable", null);
      return postgres;
    } catch (Exception exception) {
      postgres.stop();
      throw new IllegalStateException(exception);
    }
  }

  @Override
  @Bean
  SchemaRoutingDataSource dataSource(org.springframework.core.env.Environment environment) {
    var environmentPostgres = environmentPostgres();
    var pools = new EnumMap<DatabaseScope, HikariDataSource>(DatabaseScope.class);
    for (var scope : DatabaseScope.values()) {
      var config = new HikariConfig();
      config.setJdbcUrl(environmentPostgres.getJdbcUrl().replace("/postgres", "/meterengine_app"));
      config.setUsername("meterengine_" + scope.name().toLowerCase(Locale.ROOT));
      config.setPassword("fixture-password");
      config.addDataSourceProperty("currentSchema", scope.name().toLowerCase(Locale.ROOT));
      config.setMaximumPoolSize(4);
      config.setMinimumIdle(0);
      pools.put(scope, pool(config));
    }
    return new SchemaRoutingDataSource(pools);
  }

  protected HikariDataSource pool(HikariConfig config) {
    return new TracingPool(config);
  }

  protected boolean activateTarget() {
    return true;
  }

  @Bean
  LocalContainerEntityManagerFactoryBean entityManagerFactory(
      SchemaRoutingDataSource dataSource,
      org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer customizer) {
    var factory = new LocalContainerEntityManagerFactoryBean();
    factory.setDataSource(dataSource);
    factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
    factory.setManagedTypes(
        org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes.of(managedTypes()));
    var properties = new HashMap<String, Object>();
    properties.put("hibernate.hbm2ddl.auto", "none");
    properties.put(
        "hibernate.physical_naming_strategy",
        "org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl");
    customizer.customize(properties);
    factory.setJpaPropertyMap(properties);
    return factory;
  }

  protected String[] managedTypes() {
    return new String[] {
      "com.meterengine.account.entity.UserAccount",
      "com.meterengine.account.entity.UserSession",
      "com.meterengine.account.entity.Consent",
      "com.meterengine.organization.entity.Organization",
      "com.meterengine.organization.entity.OrganizationMember",
      "com.meterengine.organization.entity.OrganizationInvitation",
      "com.meterengine.apikey.entity.ApiKey",
      "com.meterengine.audit.entity.AuditLog",
      "com.meterengine.customer.entity.Customer",
      "com.meterengine.scheduling.entity.ScheduledJobRun"
    };
  }

  @Bean
  TransactionTemplate transactionTemplate(
      org.springframework.orm.jpa.JpaTransactionManager manager) {
    return new TransactionTemplate(manager);
  }

  public static final java.util.concurrent.ConcurrentLinkedQueue<ConnectionTrace>
      CONNECTION_TRACES = new java.util.concurrent.ConcurrentLinkedQueue<>();

  public record ConnectionTrace(
      String thread, DatabaseScope scope, String role, String schema, int pid) {}

  static class TracingPool extends HikariDataSource {
    TracingPool(HikariConfig config) {
      super(config);
    }

    @Override
    public java.sql.Connection getConnection() throws java.sql.SQLException {
      var connection = super.getConnection();
      try (var statement = connection.createStatement();
          var result =
              statement.executeQuery("SELECT current_user,current_schema(),pg_backend_pid()")) {
        result.next();
        CONNECTION_TRACES.add(
            new ConnectionTrace(
                Thread.currentThread().getName(),
                DatabaseScopes.current(),
                result.getString(1),
                result.getString(2),
                result.getInt(3)));
        return connection;
      } catch (java.sql.SQLException exception) {
        connection.close();
        throw exception;
      }
    }
  }
}
