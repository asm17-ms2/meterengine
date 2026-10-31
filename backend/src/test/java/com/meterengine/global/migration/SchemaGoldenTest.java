package com.meterengine.global.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class SchemaGoldenTest {
  static final String VALID =
      """
      {"schema_version":2,"postgresql_version_num":180004,
       "sources":{
         "source49":{"source_commit":"be62d9138d10f8df69bfdbad53a9b405564eff2d","flyway_version":"49","schema_sha256":"ccbf69043951f38a77d0e378cbb912a90de04219b258406c27eff592d7d265b7"},
         "source53":{"source_commit":"7889d845c9f908528a156c3f5d363e14565e9582","flyway_version":"53","schema_sha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}},
       "target":{"flyway_version":"3","schema_sha256":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"}}
      """;

  @Test
  void 현재_target은_v2_자원을_필수로_읽는다() {
    assertThatCode(() -> SchemaFingerprint.resource("/db/schema-golden.v2.json"))
        .doesNotThrowAnyException();
    assertThatCode(() -> loadTargetHash()).doesNotThrowAnyException();
    var golden =
        object(SchemaFingerprint.parse(SchemaFingerprint.resource("/db/schema-golden.v2.json")));
    assertThat(loadTargetHash()).isEqualTo(object(golden.get("target")).get("schema_sha256"));
    assertThat(loadTargetHash())
        .isNotEqualTo(
            object(SchemaFingerprint.parse(SchemaFingerprint.resource("/db/schema-golden.v1.json")))
                .get("target_schema_sha256"));
  }

  @Test
  void 완전한_v2에서_target_hash를_선택한다() {
    assertThat(targetHash(VALID))
        .isEqualTo("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
  }

  @Test
  void 각_객체의_누락과_추가_필드를_거절한다() {
    for (String path :
        new String[] {
          "schema_version",
          "postgresql_version_num",
          "sources",
          "sources.source49",
          "sources.source53",
          "sources.source49.source_commit",
          "sources.source49.flyway_version",
          "sources.source49.schema_sha256",
          "sources.source53.source_commit",
          "sources.source53.flyway_version",
          "sources.source53.schema_sha256",
          "target",
          "target.flyway_version",
          "target.schema_sha256"
        }) {
      var document = object(SchemaFingerprint.parse(VALID));
      parent(document, path).remove(leaf(path));
      assertThatThrownBy(() -> targetHash(SchemaFingerprint.canonical(document)))
          .as(path)
          .isInstanceOf(IllegalArgumentException.class);
    }
    for (String path :
        new String[] {
          "extra",
          "sources.extra",
          "sources.source49.extra",
          "sources.source53.extra",
          "target.extra"
        }) {
      var document = object(SchemaFingerprint.parse(VALID));
      parent(document, path).put(leaf(path), "extra");
      assertThatThrownBy(() -> targetHash(SchemaFingerprint.canonical(document)))
          .as(path)
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  void 값의_타입과_null을_거절한다() {
    for (String path :
        new String[] {
          "schema_version",
          "postgresql_version_num",
          "sources",
          "sources.source49",
          "sources.source53",
          "sources.source49.source_commit",
          "sources.source49.flyway_version",
          "sources.source49.schema_sha256",
          "sources.source53.source_commit",
          "sources.source53.flyway_version",
          "sources.source53.schema_sha256",
          "target",
          "target.flyway_version",
          "target.schema_sha256"
        }) {
      for (Object value : new Object[] {null, Boolean.TRUE, java.util.List.of(), 42, "wrong"}) {
        var document = object(SchemaFingerprint.parse(VALID));
        parent(document, path).put(leaf(path), value);
        assertThatThrownBy(() -> targetHash(SchemaFingerprint.canonical(document)))
            .as(path + ":" + value)
            .isInstanceOf(IllegalArgumentException.class);
      }
    }
    assertThatThrownBy(() -> targetHash(VALID.replace("180004", "180004.0")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> targetHash(VALID.replace("\"schema_version\":2", "\"schema_version\":2.0")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void 지원하지_않는_내부와_생성환경과_Flyway_버전을_거절한다() {
    for (String[] change :
        new String[][] {
          {"schema_version", "3"},
          {"postgresql_version_num", "180003"},
          {"sources.source49.flyway_version", "48"},
          {"sources.source53.flyway_version", "52"},
          {"target.flyway_version", "4"}
        }) {
      var document = object(SchemaFingerprint.parse(VALID));
      parent(document, change[0])
          .put(leaf(change[0]), change[0].contains(".") ? change[1] : Integer.valueOf(change[1]));
      assertThatThrownBy(() -> targetHash(SchemaFingerprint.canonical(document)))
          .as(change[0])
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  void commit과_hash는_정확한_길이의_소문자_16진수만_받는다() {
    for (String path :
        new String[] {
          "sources.source49.source_commit",
          "sources.source53.source_commit",
          "sources.source49.schema_sha256",
          "sources.source53.schema_sha256",
          "target.schema_sha256"
        }) {
      for (String value :
          new String[] {
            "",
            "a".repeat(path.endsWith("source_commit") ? 39 : 63),
            "a".repeat(path.endsWith("source_commit") ? 41 : 65),
            "A".repeat(path.endsWith("source_commit") ? 40 : 64),
            "g".repeat(path.endsWith("source_commit") ? 40 : 64)
          }) {
        var document = object(SchemaFingerprint.parse(VALID));
        parent(document, path).put(leaf(path), value);
        assertThatThrownBy(() -> targetHash(SchemaFingerprint.canonical(document)))
            .as(path)
            .isInstanceOf(IllegalArgumentException.class);
      }
    }
  }

  @Test
  void source49는_v1과_기존_원문에_source53는_새_원문에_연결된다() {
    var golden =
        object(SchemaFingerprint.parse(SchemaFingerprint.resource("/db/schema-golden.v2.json")));
    var sources = object(golden.get("sources"));
    var old =
        object(SchemaFingerprint.parse(SchemaFingerprint.resource("/db/schema-golden.v1.json")));
    var source49 = object(sources.get("source49"));
    var source53 = object(sources.get("source53"));
    assertThat(source49.get("source_commit"))
        .isEqualTo("be62d9138d10f8df69bfdbad53a9b405564eff2d")
        .isEqualTo(old.get("legacy_source_commit"))
        .isEqualTo(provenance("legacy49").get("source_commit"));
    assertThat(source49.get("schema_sha256")).isEqualTo(old.get("legacy_schema_sha256"));
    assertThat(source53.get("source_commit"))
        .isEqualTo("7889d845c9f908528a156c3f5d363e14565e9582")
        .isEqualTo(provenance("legacy53").get("source_commit"));
  }

  @Test
  void v1_입력과_없는_입력은_fallback_없이_거절한다() {
    assertThatThrownBy(() -> targetHash(SchemaFingerprint.resource("/db/schema-golden.v1.json")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> targetHash("null")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> targetHash("[]")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> targetHash("{}")).isInstanceOf(IllegalArgumentException.class);
  }

  static Map<String, Object> provenance(String folder) {
    return object(
        SchemaFingerprint.parse(SchemaFingerprint.resource("/db/" + folder + "/provenance.json")));
  }

  @SuppressWarnings("unchecked")
  static Map<String, Object> object(Object value) {
    return (Map<String, Object>) value;
  }

  static Map<String, Object> parent(Map<String, Object> document, String path) {
    String[] parts = path.split("\\.");
    for (int i = 0; i < parts.length - 1; i++) document = object(document.get(parts[i]));
    return document;
  }

  static String leaf(String path) {
    return path.substring(path.lastIndexOf('.') + 1);
  }

  static String targetHash(String text) {
    return SchemaGolden.targetHash(text);
  }

  static String loadTargetHash() {
    return SchemaGolden.loadTargetHash();
  }
}
