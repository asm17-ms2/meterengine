package com.meterengine.global.migration;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;

final class TargetActivation {

  private TargetActivation() {}

  static void run(DataSource owner, boolean fresh, String identifier, String reportText)
      throws SQLException {
    if (identifier == null
        || !identifier.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}")
        || identifier.contains("://")) {
      throw new IllegalArgumentException("logical target identifier required");
    }
    try (var connection = owner.getConnection()) {
      SchemaMigrator.requireTarget(connection);
      DatabaseBootstrap.verifyRuntimeBoundary(connection);
      connection.setAutoCommit(false);
      try {
        connection.createStatement().execute("SELECT pg_advisory_xact_lock(52100001)");
        var tables = tableCounts(connection);
        for (String table : tables.keySet())
          connection.createStatement().execute("LOCK TABLE " + qualified(table) + " IN SHARE MODE");
        try (var fingerprint = owner.getConnection()) {
          String expected = SchemaGolden.loadTargetHash();
          if (!expected.equals(SchemaFingerprint.catalogHash(fingerprint)))
            throw new IllegalArgumentException("target schema differs from verified golden");
        }
        tables = tableCounts(connection);
        if (fresh) verifyFresh(connection, tables);
        else verifyLegacy(connection, identifier, reportText, tables);
        try (var result =
            connection
                .createStatement()
                .executeQuery(
                    "SELECT EXISTS(SELECT 1 FROM pg_constraint WHERE connamespace IN ('shared'::regnamespace,'test'::regnamespace,'live'::regnamespace) AND (NOT convalidated OR NOT conenforced)) OR EXISTS(SELECT 1 FROM pg_trigger t JOIN pg_class c ON c.oid=t.tgrelid JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname IN ('shared','test','live') AND t.tgenabled<>'O')")) {
          result.next();
          if (result.getBoolean(1))
            throw new IllegalArgumentException("target guards are not enabled and validated");
        }
        connection.createStatement().execute(SchemaFingerprint.resource("/db/activate-target.sql"));
        connection.commit();
      } catch (SQLException | RuntimeException error) {
        connection.rollback();
        throw error;
      }
    }
  }

  private static Map<String, Long> tableCounts(Connection c) throws SQLException {
    var names = new ArrayList<String>();
    try (var result =
        c.createStatement()
            .executeQuery(
                "SELECT n.nspname||'.'||c.relname FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname IN ('shared','test','live') AND c.relkind IN ('r','p') AND c.relname<>'flyway_schema_history' ORDER BY n.nspname COLLATE \"C\",c.relname COLLATE \"C\"")) {
      while (result.next()) names.add(result.getString(1));
    }
    var counts = new LinkedHashMap<String, Long>();
    for (String table : names)
      try (var result =
          c.createStatement().executeQuery("SELECT count(*) FROM " + qualified(table))) {
        result.next();
        counts.put(table, result.getLong(1));
      }
    return counts;
  }

  private static String qualified(String name) {
    String[] pieces = name.split("\\.", -1);
    if (pieces.length != 2
        || !pieces[0].matches("shared|test|live")
        || !pieces[1].matches("[a-z_]+"))
      throw new IllegalArgumentException("unexpected app table");
    return "\"" + pieces[0] + "\".\"" + pieces[1] + "\"";
  }

  private static void verifyFresh(Connection c, Map<String, Long> tables) throws SQLException {
    for (var entry : tables.entrySet()) {
      boolean marker = entry.getKey().matches("(test|live)\\.event_rejection_recording_start");
      if (entry.getValue() != (marker ? 1L : 0L))
        throw new IllegalArgumentException("fresh target contains unexpected data");
    }
    for (String scope : new String[] {"test", "live"}) {
      try (var result =
          c.createStatement()
              .executeQuery(
                  "SELECT last_value,is_called FROM " + scope + ".invoice_display_number_seq")) {
        result.next();
        if (result.getLong(1) != 1 || result.getBoolean(2))
          throw new IllegalArgumentException("fresh identity sequence has been used");
      }
    }
  }

  private static void verifyLegacy(
      Connection c, String identifier, String text, Map<String, Long> counts) throws SQLException {
    if (text == null) throw new IllegalArgumentException("legacy verification report required");
    Map<String, Object> report = object(SchemaFingerprint.parse(text));
    exact(report, Set.of("schema_version", "kind", "target", "verification"));
    if (!Integer.valueOf(1).equals(report.get("schema_version"))
        || !"legacy_import".equals(report.get("kind")))
      throw new IllegalArgumentException("unsupported activation report");
    var target = object(report.get("target"));
    exact(target, Set.of("identifier", "database_name", "schema_fingerprint_sha256"));
    if (!identifier.equals(target.get("identifier"))
        || !MigrationSettings.TARGET_DATABASE.equals(target.get("database_name"))
        || !SchemaGolden.loadTargetHash().equals(target.get("schema_fingerprint_sha256")))
      throw new IllegalArgumentException("report target mismatch");
    var verification = object(report.get("verification"));
    exact(
        verification,
        Set.of(
            "bundle_sha256",
            "source_fingerprint_sha256",
            "target_fingerprint_sha256",
            "tables",
            "constraints",
            "triggers",
            "passed"));
    passed(verification);
    passed(object(verification.get("constraints")));
    passed(object(verification.get("triggers")));
    for (String key :
        new String[] {"bundle_sha256", "source_fingerprint_sha256", "target_fingerprint_sha256"})
      digest(verification.get(key));
    if (!SchemaFingerprint.hash(target).equals(verification.get("target_fingerprint_sha256")))
      throw new IllegalArgumentException("report fingerprint mismatch");
    var tableResults = object(verification.get("tables"));
    var expectedTables = new java.util.HashSet<>(counts.keySet());
    expectedTables.remove("shared.legacy_import");
    exact(tableResults, expectedTables);
    for (var entry : tableResults.entrySet()) {
      var result = object(entry.getValue());
      exact(
          result,
          Set.of("expected_rows", "actual_rows", "expected_sha256", "actual_sha256", "passed"));
      passed(result);
      long expectedRows = rows(result.get("expected_rows"));
      long actualRows = rows(result.get("actual_rows"));
      digest(result.get("expected_sha256"));
      digest(result.get("actual_sha256"));
      if (expectedRows != actualRows
          || actualRows != counts.get(entry.getKey())
          || !result.get("expected_sha256").equals(result.get("actual_sha256")))
        throw new IllegalArgumentException("report table comparison failed");
    }
    if (counts.get("shared.legacy_import") != 1)
      throw new IllegalArgumentException("committed import success required");
    try (var result =
        c.createStatement()
            .executeQuery(
                "SELECT bundle_sha256,source_fingerprint_sha256,target_fingerprint_sha256,verification_sha256 FROM shared.legacy_import WHERE singleton")) {
      if (!result.next()
          || !result.getString(1).equals(verification.get("bundle_sha256"))
          || !result.getString(2).equals(verification.get("source_fingerprint_sha256"))
          || !result.getString(3).equals(verification.get("target_fingerprint_sha256"))
          || !result.getString(4).equals(SchemaFingerprint.hash(verification)))
        throw new IllegalArgumentException("committed verification does not match report");
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> object(Object value) {
    if (!(value instanceof Map<?, ?> map)
        || map.keySet().stream().anyMatch(k -> !(k instanceof String)))
      throw new IllegalArgumentException("JSON object required");
    return (Map<String, Object>) map;
  }

  private static void exact(Map<String, ?> value, Set<String> fields) {
    if (!value.keySet().equals(fields))
      throw new IllegalArgumentException("unexpected report fields");
  }

  private static void passed(Map<String, ?> value) {
    if (!Boolean.TRUE.equals(value.get("passed")))
      throw new IllegalArgumentException("passed verification required");
  }

  private static void digest(Object value) {
    if (!(value instanceof String s) || !s.matches("[0-9a-f]{64}"))
      throw new IllegalArgumentException("SHA256 required");
  }

  private static long rows(Object value) {
    if (!(value instanceof Integer || value instanceof Long) || ((Number) value).longValue() < 0)
      throw new IllegalArgumentException("nonnegative integer row count required");
    return ((Number) value).longValue();
  }
}
