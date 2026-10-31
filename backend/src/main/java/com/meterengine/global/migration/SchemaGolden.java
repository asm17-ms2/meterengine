package com.meterengine.global.migration;

import java.util.Map;
import java.util.Set;

final class SchemaGolden {
  private SchemaGolden() {}

  static String loadTargetHash() {
    return targetHash(SchemaFingerprint.resource("/db/schema-golden.v2.json"));
  }

  static String targetHash(String text) {
    var golden =
        object(
            SchemaFingerprint.parse(text),
            Set.of("schema_version", "postgresql_version_num", "sources", "target"));
    if (!Integer.valueOf(2).equals(golden.get("schema_version"))
        || !Integer.valueOf(180004).equals(golden.get("postgresql_version_num"))) {
      throw new IllegalArgumentException("unsupported schema golden version");
    }
    var sources = object(golden.get("sources"), Set.of("source49", "source53"));
    source(sources.get("source49"), "49");
    source(sources.get("source53"), "53");
    var target = object(golden.get("target"), Set.of("flyway_version", "schema_sha256"));
    version(target, "3");
    return hexadecimal(target.get("schema_sha256"), 64);
  }

  private static void source(Object value, String version) {
    var source = object(value, Set.of("source_commit", "flyway_version", "schema_sha256"));
    version(source, version);
    hexadecimal(source.get("source_commit"), 40);
    hexadecimal(source.get("schema_sha256"), 64);
  }

  private static void version(Map<?, ?> value, String expected) {
    if (!expected.equals(value.get("flyway_version"))) {
      throw new IllegalArgumentException("unsupported schema golden Flyway version");
    }
  }

  private static Map<?, ?> object(Object value, Set<String> fields) {
    if (!(value instanceof Map<?, ?> map) || !map.keySet().equals(fields)) {
      throw new IllegalArgumentException("unexpected schema golden fields");
    }
    return map;
  }

  private static String hexadecimal(Object value, int length) {
    if (!(value instanceof String text) || !text.matches("[0-9a-f]{" + length + "}")) {
      throw new IllegalArgumentException("lowercase hexadecimal schema golden value required");
    }
    return text;
  }
}
