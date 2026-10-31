package com.meterengine.global.database;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;

final class ReadOnlyDatabaseVerifier {
  private ReadOnlyDatabaseVerifier() {}

  static void verify(DataSource source, DatabaseScope scope) {
    String name = scope.name().toLowerCase(java.util.Locale.ROOT);
    try (Connection connection = source.getConnection()) {
      try (var statement = connection.createStatement();
          var row =
              statement.executeQuery(
                  "SELECT session_user,current_user,current_database(),current_schema()")) {
        if (!row.next()
            || !row.getString(1).equals("meterengine_" + name)
            || !row.getString(1).equals(row.getString(2))
            || !row.getString(3).equals("meterengine_app")
            || !row.getString(4).equals(name))
          throw new IllegalStateException("read_only runtime identity mismatch");
      }
      String sql =
          """
          SELECT EXISTS (SELECT 1 FROM pg_roles WHERE rolname=current_user AND
            (rolsuper OR rolcreaterole OR rolcreatedb OR rolreplication OR rolbypassrls OR rolinherit OR NOT rolcanlogin))
          OR EXISTS (SELECT 1 FROM pg_auth_members m JOIN pg_roles r ON r.oid=m.member WHERE r.rolname=current_user)
          OR has_database_privilege(current_database(),'CREATE,TEMP')
          OR EXISTS (SELECT 1 FROM pg_database WHERE datname=current_database() AND pg_get_userbyid(datdba)=current_user)
          OR EXISTS (SELECT 1 FROM pg_namespace n WHERE n.nspname NOT LIKE 'pg_%' AND n.nspname<>'information_schema'
            AND (has_schema_privilege(n.oid,'CREATE') OR pg_get_userbyid(n.nspowner)=current_user))
          OR EXISTS (SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
            WHERE n.nspname NOT LIKE 'pg_%' AND n.nspname<>'information_schema' AND
            (pg_get_userbyid(c.relowner)=current_user
              OR (c.relkind IN ('r','p','v','m','f') AND
                (has_table_privilege(c.oid,'INSERT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER')
                  OR has_any_column_privilege(c.oid,'INSERT,UPDATE,REFERENCES')))
              OR (c.relkind='S' AND has_sequence_privilege(c.oid,'USAGE,UPDATE'))))
          OR EXISTS (SELECT 1 FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
            WHERE n.nspname NOT LIKE 'pg_%' AND n.nspname<>'information_schema'
              AND (pg_get_userbyid(p.proowner)=current_user OR (has_function_privilege(p.oid,'EXECUTE') AND NOT (
                p.prokind='f' AND p.provolatile<>'v' AND (
                  (n.nspname='shared' AND p.proname='runtime_scope' AND p.proargtypes::text='' AND p.prorettype='varchar'::regtype)
                  OR (n.nspname='shared' AND p.proname IN ('job_last_success','job_last_completion') AND p.proargtypes::text='1043' AND p.prorettype='timestamptz'::regtype)
                  OR (n.nspname='shared' AND p.proname IN ('job_watermark','job_earliest') AND p.proargtypes::text='1043 1184' AND p.prorettype='timestamptz'::regtype)
                  OR (n.nspname IN ('test','live') AND p.proname='valid_price_tiers' AND p.proargtypes::text='3802' AND p.prorettype='bool'::regtype))))))
          """;
      try (var statement = connection.createStatement();
          var row = statement.executeQuery(sql)) {
        if (!row.next() || row.getBoolean(1))
          throw new IllegalStateException(
              "read_only requires initial SELECT-only runtime privileges");
      }
    } catch (SQLException exception) {
      throw new IllegalStateException(
          "read_only runtime privilege verification failed: " + exception.getSQLState(), exception);
    }
  }
}
