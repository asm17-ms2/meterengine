package com.meterengine.global.migration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;

public final class SchemaMigrator {

  private final DataSource owner;

  public SchemaMigrator(DataSource owner) {
    this.owner = owner;
  }

  public int migrate() throws SQLException {
    try (var connection = owner.getConnection()) {
      requireTarget(connection);
      DatabaseBootstrap.verifyRuntimeBoundary(connection);
      try (var result =
          connection
              .createStatement()
              .executeQuery("SELECT to_regclass('shared.flyway_schema_history')")) {
        result.next();
        if (result.getString(1) == null) DatabaseBootstrap.requireEmptyTarget(connection);
      }
    }
    var flyway = flyway();
    int applied = flyway.migrate().migrationsExecuted;
    flyway.validate();
    return applied;
  }

  private Flyway flyway() {
    return Flyway.configure()
        .dataSource(owner)
        .defaultSchema("shared")
        .schemas("shared")
        .locations("classpath:db/migration")
        .baselineOnMigrate(false)
        .cleanDisabled(true)
        .load();
  }

  static void requireTarget(Connection connection) throws SQLException {
    try (var result =
        connection
            .createStatement()
            .executeQuery("SELECT current_database(),session_user,current_user")) {
      result.next();
      if (!MigrationSettings.TARGET_DATABASE.equals(result.getString(1))
          || result.getString(2).matches("meterengine_(shared|test|live)")
          || !result.getString(2).equals(result.getString(3))) {
        throw new IllegalArgumentException("separate owner connection to meterengine_app required");
      }
    }
  }

  public static boolean run(String[] args, Map<String, String> environment) {
    long commands =
        Arrays.stream(args)
            .filter(
                a ->
                    a.equals("--migrate-only")
                        || a.equals("--bootstrap-database")
                        || a.equals("--activate-target"))
            .count();
    boolean fresh = Arrays.asList(args).contains("--fresh-target");
    boolean reserved =
        Arrays.stream(args)
            .anyMatch(
                a ->
                    a.startsWith("--migrate")
                        || a.startsWith("--bootstrap-database")
                        || a.startsWith("--activate-target")
                        || a.startsWith("--fresh-target"));
    if (!reserved) return false;
    if (commands != 1
        || args.length != (fresh ? 2 : 1)
        || fresh && !Arrays.asList(args).contains("--activate-target")) {
      throw new IllegalArgumentException("exactly one standalone database command required");
    }
    var settings = MigrationSettings.from(environment);
    try {
      var migrator = new SchemaMigrator(settings.dataSource(MigrationSettings.TARGET_DATABASE));
      if (Arrays.asList(args).contains("--bootstrap-database")) {
        try (var admin = settings.dataSource("postgres").getConnection()) {
          DatabaseBootstrap.run(
              admin, settings.dataSource(MigrationSettings.TARGET_DATABASE), settings, environment);
        }
      } else if (Arrays.asList(args).contains("--migrate-only")) {
        migrator.migrate();
      } else {
        String identifier = MigrationSettings.required(environment, "DB_TARGET_IDENTIFIER");
        migrator.activate(
            fresh,
            identifier,
            fresh
                ? null
                : Files.readString(
                    Path.of(MigrationSettings.required(environment, "DB_ACTIVATION_REPORT"))));
      }
      System.out.println("Standalone database command completed");
      return true;
    } catch (Exception error) {
      throw new IllegalStateException("Standalone database command failed");
    }
  }

  public void activate(boolean fresh, String identifier, String report) throws SQLException {
    flyway().validate();
    if (flyway().info().pending().length != 0)
      throw new IllegalArgumentException("all target migrations required");
    TargetActivation.run(owner, fresh, identifier, report);
  }
}
