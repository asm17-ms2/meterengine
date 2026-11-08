package com.meterengine.apikey.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meterengine.global.security.SecureTokens;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ApiKeyTableTest {

  private final ApiKeyTable apiKeyTable = new ApiKeyTable();

  @Test
  void 통째로_바꾸면_새로_읽은_키만_남는다() {
    ApiKeySnapshot old = snapshotOf(SecureTokens.generate("me_live_"));
    ApiKeySnapshot loaded = snapshotOf(SecureTokens.generate("me_live_"));
    apiKeyTable.replaceAll(() -> List.of(old));

    apiKeyTable.replaceAll(() -> List.of(loaded));

    assertThat(apiKeyTable.find(old.keyHash())).isEmpty();
    assertThat(apiKeyTable.find(loaded.keyHash())).contains(loaded);
    assertThat(apiKeyTable.size()).isEqualTo(1);
  }

  @Test
  void 읽기가_실패하면_옛_테이블을_그대로_쓴다() {
    ApiKeySnapshot old = snapshotOf(SecureTokens.generate("me_live_"));
    apiKeyTable.replaceAll(() -> List.of(old));

    assertThatThrownBy(
            () ->
                apiKeyTable.replaceAll(
                    () -> {
                      throw new IllegalStateException("database is down");
                    }))
        .isInstanceOf(IllegalStateException.class);

    assertThat(apiKeyTable.find(old.keyHash())).contains(old);
  }

  private static ApiKeySnapshot snapshotOf(String rawKey) {
    return new ApiKeySnapshot(
        ApiKeyHash.of(rawKey),
        UUID.randomUUID(),
        UUID.randomUUID(),
        null,
        "live",
        Set.of("read"),
        null,
        null,
        null);
  }
}
