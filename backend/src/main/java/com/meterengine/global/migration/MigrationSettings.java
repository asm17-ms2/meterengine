package com.meterengine.global.migration;

import java.util.Map;
import java.util.Properties;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

public final class MigrationSettings {

  public static final String TARGET_DATABASE = "meterengine_app";
  private final String host;
  private final int port;
  private final String username;
  private final String password;
  private final String rootCertificate;

  private MigrationSettings(
      String host, int port, String username, String password, String rootCertificate) {
    this.host = host;
    this.port = port;
    this.username = username;
    this.password = password;
    this.rootCertificate = rootCertificate;
  }

  public static MigrationSettings from(Map<String, String> environment) {
    if (!TARGET_DATABASE.equals(required(environment, "DB_NAME"))) {
      throw new IllegalArgumentException("migration target must be meterengine_app");
    }
    String host = required(environment, "DB_HOST");
    if (!host.matches("[A-Za-z0-9][A-Za-z0-9.-]*")) {
      throw new IllegalArgumentException("invalid database host");
    }
    int port;
    try {
      port = Integer.parseInt(required(environment, "DB_PORT"));
    } catch (NumberFormatException error) {
      throw new IllegalArgumentException("invalid database port");
    }
    if (port < 1 || port > 65535) throw new IllegalArgumentException("invalid database port");
    String owner = required(environment, "DB_MIGRATION_USERNAME");
    if (!owner.matches("[a-z_][a-z0-9_]{0,62}")
        || owner.matches("meterengine_(shared|test|live)")) {
      throw new IllegalArgumentException("a separate migration owner is required");
    }
    String certificate = required(environment, "DB_SSL_ROOT_CERT");
    if (!certificate.startsWith("/"))
      throw new IllegalArgumentException("absolute CA path required");
    return new MigrationSettings(
        host, port, owner, required(environment, "DB_MIGRATION_PASSWORD"), certificate);
  }

  public DataSource dataSource(String database) {
    if (!database.equals(TARGET_DATABASE) && !database.equals("postgres")) {
      throw new IllegalArgumentException("unsupported owner database");
    }
    var properties = new Properties();
    properties.setProperty("user", username);
    properties.setProperty("password", password);
    properties.setProperty("sslmode", "verify-full");
    properties.setProperty("sslrootcert", rootCertificate);
    properties.setProperty("connectTimeout", "10");
    properties.setProperty("socketTimeout", "30");
    return new DriverManagerDataSource(
        "jdbc:postgresql://" + host + ":" + port + "/" + database, properties);
  }

  public String username() {
    return username;
  }

  static String required(Map<String, String> environment, String key) {
    String value = environment.get(key);
    if (value == null
        || value.isBlank()
        || value.indexOf('\0') >= 0
        || value.indexOf('\n') >= 0
        || value.indexOf('\r') >= 0) {
      throw new IllegalArgumentException("missing or invalid " + key);
    }
    return value;
  }

  @Override
  public String toString() {
    return "MigrationSettings[target=meterengine_app]";
  }
}
