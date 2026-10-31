package com.meterengine.global.migration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import tools.jackson.databind.json.JsonMapper;

public final class SchemaFingerprint {

  private static final JsonMapper JSON = new JsonMapper();

  private SchemaFingerprint() {}

  public static Object parse(String json) {
    return JSON.readValue(json, Object.class);
  }

  public static String canonical(Object value) {
    if (value == null
        || value instanceof Boolean
        || value instanceof String
        || value instanceof Byte
        || value instanceof Short
        || value instanceof Integer
        || value instanceof Long
        || value instanceof java.math.BigInteger) {
      return JSON.writeValueAsString(value);
    }
    if (value instanceof Map<?, ?> map) {
      var sorted =
          new TreeMap<String, Object>(
              (left, right) ->
                  java.util.Arrays.compare(
                      left.codePoints().toArray(), right.codePoints().toArray()));
      map.forEach(
          (key, item) -> {
            if (!(key instanceof String))
              throw new IllegalArgumentException("JSON keys must be strings");
            sorted.put((String) key, item);
          });
      return "{"
          + sorted.entrySet().stream()
              .map(e -> canonical(e.getKey()) + ":" + canonical(e.getValue()))
              .collect(java.util.stream.Collectors.joining(","))
          + "}";
    }
    if (value instanceof List<?> list) {
      return "["
          + list.stream()
              .map(SchemaFingerprint::canonical)
              .collect(java.util.stream.Collectors.joining(","))
          + "]";
    }
    throw new IllegalArgumentException("canonical JSON requires integer numbers");
  }

  public static String hash(Object value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(canonical(value).getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException error) {
      throw new IllegalStateException(error);
    }
  }

  public static String catalogHash(Connection connection) throws SQLException {
    return catalogHash(connection, "shared");
  }

  public static String catalogHash(Connection connection, String historySchema)
      throws SQLException {
    if (!"shared".equals(historySchema) && !"public".equals(historySchema))
      throw new IllegalArgumentException("history schema must be shared or public");
    if (!connection.getAutoCommit())
      throw new IllegalArgumentException("fingerprint needs a separate transaction");
    connection.setReadOnly(true);
    connection.setAutoCommit(false);
    try {
      connection.createStatement().execute("SET LOCAL search_path=pg_catalog");
      try (var statement = connection.prepareStatement(resource("/db/schema-fingerprint.sql"))) {
        statement.setString(1, historySchema);
        try (var result = statement.executeQuery()) {
          result.next();
          return hash(parse(result.getString(1)));
        }
      }
    } finally {
      connection.rollback();
      connection.setAutoCommit(true);
      connection.setReadOnly(false);
    }
  }

  static String resource(String path) {
    try (var stream = SchemaFingerprint.class.getResourceAsStream(path)) {
      if (stream == null) throw new IllegalStateException("missing migration resource");
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException error) {
      throw new IllegalStateException("cannot read migration resource");
    }
  }
}
