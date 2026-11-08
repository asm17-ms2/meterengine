package com.meterengine.apikey.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meterengine.global.security.OrganizationStatusChangedEvent;
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
    apiKeyTable.replaceAll(() -> contentsOf(old));

    apiKeyTable.replaceAll(() -> contentsOf(loaded));

    assertThat(apiKeyTable.find(old.keyHash())).isEmpty();
    assertThat(apiKeyTable.find(loaded.keyHash())).contains(loaded);
    assertThat(apiKeyTable.size()).isEqualTo(1);
  }

  @Test
  void 읽기가_실패하면_옛_테이블을_그대로_쓴다() {
    ApiKeySnapshot old = snapshotOf(SecureTokens.generate("me_live_"));
    apiKeyTable.replaceAll(() -> contentsOf(old));

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
          return contentsOf(readBeforeRevoke);
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

  @Test
  void 활성_목록에_없는_도입사의_키는_정지된_도입사로_본다() {
    UUID active = UUID.randomUUID();
    ApiKeySnapshot activeKey = snapshotOf(SecureTokens.generate("me_live_"), active);
    ApiKeySnapshot suspendedKey = snapshotOf(SecureTokens.generate("me_live_"));

    apiKeyTable.replaceAll(
        () -> new ApiKeyTableContents(List.of(activeKey, suspendedKey), Set.of(active)));

    assertThat(apiKeyTable.isOrganizationActive(active)).isTrue();
    assertThat(apiKeyTable.isOrganizationActive(suspendedKey.organizationId())).isFalse();
  }

  @Test
  void 도입사_상태가_바뀌면_키_항목은_남기고_상태만_바뀐다() {
    ApiKeySnapshot snapshot = snapshotOf(SecureTokens.generate("me_live_"));
    apiKeyTable.replaceAll(
        () -> new ApiKeyTableContents(List.of(snapshot), Set.of(snapshot.organizationId())));

    apiKeyTable.apply(new OrganizationStatusChangedEvent(snapshot.organizationId(), false));

    assertThat(apiKeyTable.find(snapshot.keyHash())).contains(snapshot);
    assertThat(apiKeyTable.isOrganizationActive(snapshot.organizationId())).isFalse();
    apiKeyTable.apply(new OrganizationStatusChangedEvent(snapshot.organizationId(), true));
    assertThat(apiKeyTable.isOrganizationActive(snapshot.organizationId())).isTrue();
  }

  @Test
  void 키_변경이_들고_온_도입사_상태는_아는_도입사의_상태를_덮지_않는다() {
    ApiKeySnapshot snapshot = snapshotOf(SecureTokens.generate("me_live_"));
    apiKeyTable.apply(new OrganizationStatusChangedEvent(snapshot.organizationId(), false));

    apiKeyTable.apply(new ApiKeyChangedEvent(snapshot, true));

    assertThat(apiKeyTable.isOrganizationActive(snapshot.organizationId())).isFalse();
  }

  @Test
  void 키_변경이_들고_온_도입사_상태는_모르는_도입사에_쓴다() {
    ApiKeySnapshot snapshot = snapshotOf(SecureTokens.generate("me_live_"));

    apiKeyTable.apply(new ApiKeyChangedEvent(snapshot, true));

    assertThat(apiKeyTable.isOrganizationActive(snapshot.organizationId())).isTrue();
  }

  @Test
  void 읽는_동안_바뀐_도입사_상태는_새_테이블에도_남는다() {
    ApiKeySnapshot snapshot = snapshotOf(SecureTokens.generate("me_live_"));

    apiKeyTable.replaceAll(
        () -> {
          apiKeyTable.apply(new OrganizationStatusChangedEvent(snapshot.organizationId(), false));
          return new ApiKeyTableContents(List.of(snapshot), Set.of(snapshot.organizationId()));
        });

    assertThat(apiKeyTable.isOrganizationActive(snapshot.organizationId())).isFalse();
  }

  private static ApiKeyTableContents contentsOf(ApiKeySnapshot... snapshots) {
    return new ApiKeyTableContents(List.of(snapshots), Set.of());
  }

  private static ApiKeySnapshot snapshotOf(String rawKey) {
    return snapshotOf(rawKey, UUID.randomUUID());
  }

  private static ApiKeySnapshot snapshotOf(String rawKey, UUID organizationId) {
    return new ApiKeySnapshot(
        ApiKeyHash.of(rawKey),
        UUID.randomUUID(),
        organizationId,
        null,
        "live",
        Set.of("read"),
        null,
        null,
        null);
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
