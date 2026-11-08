package com.meterengine.apikey.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meterengine.global.security.SecureTokens;
import java.time.OffsetDateTime;
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

  @Test
  void 읽는_동안_고친_항목은_새_테이블에도_남는다() {
    String rawKey = SecureTokens.generate("me_live_");
    ApiKeySnapshot readBeforeRevoke = snapshotOf(rawKey);
    ApiKeySnapshot revoked = snapshotOf(rawKey, null, OffsetDateTime.now());

    apiKeyTable.replaceAll(
        () -> {
          apiKeyTable.put(revoked);
          return List.of(readBeforeRevoke);
        });

    assertThat(apiKeyTable.find(ApiKeyHash.of(rawKey))).contains(revoked);
  }

  @Test
  void 폐기된_항목_위에_폐기_안_된_스냅숏을_넣어도_폐기가_남는다() {
    String rawKey = SecureTokens.generate("me_live_");
    apiKeyTable.put(snapshotOf(rawKey, null, OffsetDateTime.now()));

    apiKeyTable.put(snapshotOf(rawKey));

    assertThat(apiKeyTable.find(ApiKeyHash.of(rawKey)))
        .hasValueSatisfying(snapshot -> assertThat(snapshot.revokedAt()).isNotNull());
  }

  @Test
  void 만료가_당겨진_항목_위에_만료_없는_스냅숏을_넣어도_당겨진_만료가_남는다() {
    String rawKey = SecureTokens.generate("me_live_");
    OffsetDateTime shortened = OffsetDateTime.now().plusHours(1);
    apiKeyTable.put(snapshotOf(rawKey, shortened, null));

    apiKeyTable.put(snapshotOf(rawKey));

    assertThat(apiKeyTable.find(ApiKeyHash.of(rawKey)))
        .hasValueSatisfying(snapshot -> assertThat(snapshot.expiresAt()).isEqualTo(shortened));
  }

  private static ApiKeySnapshot snapshotOf(String rawKey) {
    return snapshotOf(rawKey, null, null);
  }

  private static ApiKeySnapshot snapshotOf(
      String rawKey, OffsetDateTime expiresAt, OffsetDateTime revokedAt) {
    return new ApiKeySnapshot(
        ApiKeyHash.of(rawKey),
        UUID.randomUUID(),
        UUID.randomUUID(),
        null,
        "live",
        Set.of("read"),
        null,
        expiresAt,
        revokedAt);
  }
}
