package com.meterengine.global.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SchemaMigratorTest {

  @Test
  void 숫자와_한글과_중첩_키를_정렬해_Python과_같은_canonical_bytes를_만든다() {
    Object value =
        SchemaFingerprint.parse(
            "{\"한글\":\"값\\n\\\"\",\"z\":[true,null,-1],\"a\":{\"b\":2,\"a\":1}}");
    assertThat(SchemaFingerprint.canonical(value))
        .isEqualTo("{\"a\":{\"a\":1,\"b\":2},\"z\":[true,null,-1],\"한글\":\"값\\n\\\"\"}");
    assertThat(SchemaFingerprint.hash(value))
        .isEqualTo("38f592e9ccbc1720247f332ab6041b1459aa0ddd3afea9f2bdc45c8be548af27");
  }

  @Test
  void 잘못_섞은_명령과_fresh_단독_명령은_DB에_접속하기_전에_거절한다() {
    for (var args :
        List.of(
            new String[] {"--migrate-only", "--bootstrap-database"},
            new String[] {"--fresh-target"},
            new String[] {"--migrate-only", "--fresh-target"},
            new String[] {"--activate-target=fresh"})) {
      assertThatThrownBy(() -> SchemaMigrator.run(args, Map.of()))
          .isInstanceOf(IllegalArgumentException.class);
    }
    assertThat(SchemaMigrator.run(new String[] {}, Map.of())).isFalse();
  }

  @Test
  void canonical_키는_UTF16_대신_유니코드_코드포인트_순서로_정렬한다() {
    Object value = SchemaFingerprint.parse("{\"\\ue000\":2,\"\\ud800\\udc00\":1}");
    assertThat(SchemaFingerprint.canonical(value)).isEqualTo("{\"\uE000\":2,\"\uD800\uDC00\":1}");
  }
}
