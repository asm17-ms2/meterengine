package com.meterengine.global.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import java.util.EnumMap;
import java.util.Locale;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.vendor.HibernateJpaDialect;

@Configuration(proxyBeanMethods = false)
public class DatabaseConfig {

  @Bean
  @ConditionalOnMissingBean(javax.sql.DataSource.class)
  SchemaRoutingDataSource dataSource(Environment environment) {
    var policy = new CutoverPolicy(environment);
    var configByScope = new EnumMap<DatabaseScope, HikariConfig>(DatabaseScope.class);
    for (var scope : DatabaseScope.values())
      configByScope.put(scope, poolConfig(environment, scope));
    var poolByScope = new EnumMap<DatabaseScope, HikariDataSource>(DatabaseScope.class);
    try {
      configByScope.forEach(
          (scope, config) -> poolByScope.put(scope, new HikariDataSource(config)));
      if (policy.readOnly())
        poolByScope.forEach((scope, pool) -> ReadOnlyDatabaseVerifier.verify(pool, scope));
      return new SchemaRoutingDataSource(poolByScope);
    } catch (RuntimeException error) {
      poolByScope.values().forEach(HikariDataSource::close);
      throw new IllegalStateException("runtime database pools could not be initialized");
    }
  }

  HikariConfig poolConfig(Environment environment, DatabaseScope scope) {
    String host = required(environment, "DB_HOST");
    if (!host.matches("[A-Za-z0-9][A-Za-z0-9.-]*"))
      throw new IllegalArgumentException("invalid DB_HOST");
    String database = required(environment, "DB_NAME");
    if (!database.equals("meterengine_app"))
      throw new IllegalArgumentException("runtime database must be meterengine_app");
    int port;
    try {
      port = Integer.parseInt(required(environment, "DB_PORT"));
    } catch (NumberFormatException error) {
      throw new IllegalArgumentException("invalid DB_PORT");
    }
    if (port < 1 || port > 65535) throw new IllegalArgumentException("invalid DB_PORT");
    String name = scope.name().toLowerCase(Locale.ROOT);
    String username = required(environment, "DB_" + scope + "_USERNAME");
    if (!username.equals("meterengine_" + name))
      throw new IllegalArgumentException("fixed runtime role required for " + scope);
    String certificate = required(environment, "DB_SSL_ROOT_CERT");
    if (!certificate.startsWith("/"))
      throw new IllegalArgumentException("absolute DB_SSL_ROOT_CERT required");
    var config = new HikariConfig();
    config.setJdbcUrl("jdbc:postgresql://" + host + ":" + port + "/" + database);
    config.setUsername(username);
    config.setPassword(required(environment, "DB_" + scope + "_PASSWORD"));
    config.setPoolName("meterengine-" + name);
    config.addDataSourceProperty("currentSchema", name);
    config.addDataSourceProperty("sslmode", "verify-full");
    config.addDataSourceProperty("sslrootcert", certificate);
    config.addDataSourceProperty("connectTimeout", "10");
    config.addDataSourceProperty("socketTimeout", "30");
    return config;
  }

  @Bean
  ScopeTenantConnectionProvider scopeTenantConnectionProvider(SchemaRoutingDataSource dataSource) {
    return new ScopeTenantConnectionProvider(dataSource);
  }

  @Bean
  ScopeTenantIdentifierResolver scopeTenantIdentifierResolver() {
    return new ScopeTenantIdentifierResolver();
  }

  @Bean
  HibernatePropertiesCustomizer scopeHibernatePropertiesCustomizer(
      ScopeTenantConnectionProvider provider, ScopeTenantIdentifierResolver resolver) {
    return properties -> {
      properties.put("hibernate.multi_tenant_connection_provider", provider);
      properties.put("hibernate.tenant_identifier_resolver", resolver);
      properties.put("hibernate.connection.handling_mode", "DELAYED_ACQUISITION_AND_HOLD");
    };
  }

  @Bean
  JpaTransactionManager transactionManager(
      EntityManagerFactory factory, SchemaRoutingDataSource dataSource, Environment environment) {
    var manager = new CutoverTransactions(factory, new CutoverPolicy(environment));
    manager.setDataSource(dataSource);
    manager.setJpaDialect(new HibernateJpaDialect());
    return manager;
  }

  @Bean
  JdbcTemplate jdbcTemplate(SchemaRoutingDataSource dataSource) {
    return new JdbcTemplate(dataSource);
  }

  @Bean
  ScopeTransactions scopeTransactions(JpaTransactionManager transactionManager) {
    return new ScopeTransactions(transactionManager);
  }

  @Bean
  SmartInitializingSingleton databaseSchemaValidation(EntityManagerFactory factory) {
    return () -> {
      var schemaManager = factory.unwrap(SessionFactory.class).getSchemaManager();
      schemaManager.forSchema("test").validateMappedObjects();
      schemaManager.forSchema("live").validateMappedObjects();
    };
  }

  private static String required(Environment environment, String key) {
    String value = environment.getProperty(key);
    if (value == null
        || value.isBlank()
        || value.indexOf('\0') >= 0
        || value.indexOf('\n') >= 0
        || value.indexOf('\r') >= 0) {
      throw new IllegalArgumentException("missing or invalid " + key);
    }
    return value;
  }
}
