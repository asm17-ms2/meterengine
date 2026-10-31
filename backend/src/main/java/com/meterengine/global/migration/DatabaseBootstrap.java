package com.meterengine.global.migration;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;

public final class DatabaseBootstrap {

  private DatabaseBootstrap() {}

  public static boolean run(
      Connection admin,
      DataSource targetSource,
      MigrationSettings settings,
      Map<String, String> environment)
      throws SQLException {
    try (var result =
        admin.createStatement().executeQuery("SELECT current_database(),session_user")) {
      result.next();
      if (!result.getString(1).equals("postgres")
          || !result.getString(2).equals(settings.username())) {
        throw new IllegalArgumentException("bootstrap requires owner login to postgres");
      }
    }
    var passwords = new LinkedHashMap<String, String>();
    for (String scope : new String[] {"shared", "test", "live"}) {
      String key = "DB_" + scope.toUpperCase(java.util.Locale.ROOT);
      String role = MigrationSettings.required(environment, key + "_USERNAME");
      if (!role.equals("meterengine_" + scope))
        throw new IllegalArgumentException("fixed runtime login required");
      passwords.put(role, MigrationSettings.required(environment, key + "_PASSWORD"));
    }
    boolean exists;
    try (var result =
        admin
            .createStatement()
            .executeQuery(
                "SELECT EXISTS(SELECT 1 FROM pg_database WHERE datname='meterengine_app')")) {
      result.next();
      exists = result.getBoolean(1);
    }
    if (exists) {
      try (var result =
          admin
              .createStatement()
              .executeQuery(
                  "SELECT pg_get_userbyid(datdba) FROM pg_database WHERE datname='meterengine_app'")) {
        result.next();
        if (!result.getString(1).equals(settings.username()))
          throw new IllegalArgumentException("target owner mismatch");
      }
      try (var target = targetSource.getConnection()) {
        verifyRuntimeBoundary(target);
        requireEmptyTarget(target);
      }
    }
    var missing = new LinkedHashMap<String, String>();
    for (var entry : passwords.entrySet()) {
      try (var statement =
          admin.prepareStatement(
              "SELECT rolcanlogin,rolsuper,rolcreatedb,rolcreaterole,rolreplication,rolbypassrls,rolinherit, EXISTS(SELECT 1 FROM pg_auth_members m WHERE m.member=r.oid OR m.roleid=r.oid) AS member FROM pg_roles r WHERE rolname=?")) {
        statement.setString(1, entry.getKey());
        try (var result = statement.executeQuery()) {
          if (!result.next()) {
            missing.put(entry.getKey(), entry.getValue());
            continue;
          }
          if (!result.getBoolean(1)) throw new IllegalArgumentException("runtime role conflict");
          for (int field = 2; field <= 8; field++)
            if (result.getBoolean(field))
              throw new IllegalArgumentException("runtime role conflict");
        }
      }
    }
    if (exists && missing.isEmpty() && prepared(admin)) return false;
    for (var entry : missing.entrySet()) {
      try (var format =
          admin.prepareStatement(
              "SELECT format('CREATE ROLE %I LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS NOINHERIT PASSWORD %L',?,?)")) {
        format.setString(1, entry.getKey());
        format.setString(2, entry.getValue());
        try (var result = format.executeQuery()) {
          result.next();
          admin.createStatement().execute(result.getString(1));
        }
      }
    }
    if (!exists)
      admin
          .createStatement()
          .execute(
              "CREATE DATABASE meterengine_app OWNER \""
                  + settings.username()
                  + "\" TEMPLATE template0");
    admin.createStatement().execute("REVOKE ALL ON DATABASE meterengine_app FROM PUBLIC");
    for (var scope : new String[] {"shared", "test", "live"}) {
      admin
          .createStatement()
          .execute("GRANT CONNECT ON DATABASE meterengine_app TO meterengine_" + scope);
      String path = scope.equals("shared") ? "shared,pg_catalog" : scope + ",shared,pg_catalog";
      admin
          .createStatement()
          .execute(
              "ALTER ROLE meterengine_"
                  + scope
                  + " IN DATABASE meterengine_app SET search_path="
                  + path);
    }
    return true;
  }

  private static boolean prepared(Connection admin) throws SQLException {
    try (var result =
        admin
            .createStatement()
            .executeQuery(
                "SELECT EXISTS(SELECT 1 FROM pg_database d CROSS JOIN LATERAL aclexplode(coalesce(d.datacl,acldefault('d',d.datdba))) a WHERE d.datname='meterengine_app' AND a.grantee=0)")) {
      result.next();
      if (result.getBoolean(1)) return false;
    }
    for (String scope : new String[] {"shared", "test", "live"}) {
      String role = "meterengine_" + scope;
      try (var statement =
          admin.prepareStatement(
              "SELECT has_database_privilege(?, 'meterengine_app','CONNECT'),(SELECT split_part(setting,'=',2) FROM pg_db_role_setting s CROSS JOIN LATERAL unnest(s.setconfig) setting WHERE s.setdatabase=(SELECT oid FROM pg_database WHERE datname='meterengine_app') AND s.setrole=(SELECT oid FROM pg_roles WHERE rolname=?) AND setting LIKE 'search_path=%')")) {
        statement.setString(1, role);
        statement.setString(2, role);
        try (var result = statement.executeQuery()) {
          result.next();
          String path = result.getString(2);
          String expected =
              scope.equals("shared") ? "shared,pg_catalog" : scope + ",shared,pg_catalog";
          if (!result.getBoolean(1) || path == null || !path.replace(" ", "").equals(expected))
            return false;
        }
      }
    }
    return true;
  }

  static void requireEmptyTarget(Connection target) throws SQLException {
    try (var result =
        target
            .createStatement()
            .executeQuery(
                "SELECT current_database(), EXISTS(SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname NOT IN ('pg_catalog','information_schema') AND n.nspname NOT LIKE 'pg_toast%' AND n.nspname NOT LIKE 'pg_temp%') OR EXISTS(SELECT 1 FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname NOT IN ('pg_catalog','information_schema')) OR EXISTS(SELECT 1 FROM pg_type t JOIN pg_namespace n ON n.oid=t.typnamespace WHERE n.nspname NOT IN ('pg_catalog','information_schema')) OR EXISTS(SELECT 1 FROM pg_collation c JOIN pg_namespace n ON n.oid=c.collnamespace WHERE n.nspname NOT IN ('pg_catalog','information_schema')) OR EXISTS(SELECT 1 FROM pg_namespace WHERE nspname NOT IN ('public','pg_catalog','information_schema') AND nspname NOT LIKE 'pg_toast%' AND nspname NOT LIKE 'pg_temp%') OR EXISTS(SELECT 1 FROM pg_extension WHERE extname<>'plpgsql')")) {
      result.next();
      if (!result.getString(1).equals(MigrationSettings.TARGET_DATABASE) || result.getBoolean(2)) {
        throw new IllegalArgumentException("new migration target must be empty");
      }
    }
  }

  static void verifyRuntimeBoundary(Connection target) throws SQLException {
    String sql =
        """
        WITH runtime AS (
          SELECT * FROM pg_roles WHERE rolname IN ('meterengine_shared','meterengine_test','meterengine_live')
        )
        SELECT EXISTS(SELECT 1 FROM runtime r WHERE NOT r.rolcanlogin OR r.rolsuper OR r.rolcreatedb
          OR r.rolcreaterole OR r.rolreplication OR r.rolbypassrls OR r.rolinherit
          OR EXISTS(SELECT 1 FROM pg_auth_members m WHERE m.member=r.oid OR m.roleid=r.oid))
          OR EXISTS(SELECT 1 FROM pg_database d
            CROSS JOIN LATERAL aclexplode(coalesce(d.datacl,acldefault('d',d.datdba))) a
            JOIN runtime r ON r.oid=a.grantee WHERE d.datname=current_database()
            AND a.privilege_type IN ('CREATE','TEMPORARY'))
          OR EXISTS(SELECT 1 FROM pg_namespace n JOIN runtime r ON r.oid=n.nspowner)
          OR EXISTS(SELECT 1 FROM pg_namespace n
            CROSS JOIN LATERAL aclexplode(coalesce(n.nspacl,acldefault('n',n.nspowner))) a
            JOIN runtime r ON r.oid=a.grantee WHERE a.privilege_type='CREATE')
          OR EXISTS(SELECT 1 FROM pg_class c JOIN runtime r ON r.oid=c.relowner)
          OR EXISTS(SELECT 1 FROM pg_proc p JOIN runtime r ON r.oid=p.proowner)
          OR EXISTS(SELECT 1 FROM pg_default_acl d
            CROSS JOIN LATERAL aclexplode(d.defaclacl) a
            LEFT JOIN pg_namespace n ON n.oid=d.defaclnamespace
            WHERE d.defaclrole=current_user::regrole AND a.grantee<>d.defaclrole
            AND d.defaclobjtype IN ('r','S','f','n')
            AND (d.defaclnamespace=0 OR n.nspname IN ('shared','test','live')))
        """;
    try (var result = target.createStatement().executeQuery(sql)) {
      result.next();
      if (result.getBoolean(1))
        throw new IllegalArgumentException("runtime role privileges conflict with target boundary");
    }
  }
}
