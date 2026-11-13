package com.meterengine.apikey.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meterengine.TestClock;
import com.meterengine.apikey.config.ApiKeyProperties;
import com.meterengine.global.security.OrganizationStatusChangedEvent;
import com.meterengine.global.security.SecureTokens;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ApiKeyCredentialServiceTest {

  private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-08T00:00:00Z");

  private static final int TEST_LIMIT = 100;

  private final ApiKeyTable apiKeyTable = new ApiKeyTable();
  private final TestClock clock = new TestClock();
  private final ApiKeyCredentialService apiKeyCredentialService =
      new ApiKeyCredentialService(
          apiKeyTable,
          clock,
          new ApiKeyProperties(
              Duration.ofHours(24), Duration.ZERO, Duration.ofSeconds(30), TEST_LIMIT));

  private final String rawKey = SecureTokens.generate("me_live_");

  @Test
  void 테이블에_있는_키는_그_항목으로_자격_증명을_만든다() {
    ApiKeySnapshot snapshot = snapshotExpiringAt(null);
    apiKeyTable.put(snapshot);

    apiKeyTable.apply(new OrganizationStatusChangedEvent(snapshot.organizationId(), true));

    assertThat(apiKeyCredentialService.authenticate(rawKey)).contains(snapshot.credential(true));
  }

  @Test
  void 도입사가_정지된_키는_자격_증명에_정지를_싣는다() {
    ApiKeySnapshot snapshot = snapshotExpiringAt(null);
    apiKeyTable.put(snapshot);
    apiKeyTable.apply(new OrganizationStatusChangedEvent(snapshot.organizationId(), false));

    assertThat(apiKeyCredentialService.authenticate(rawKey)).contains(snapshot.credential(false));
  }

  @Test
  void 테이블에_없는_키는_인증하지_않는다() {
    assertThat(apiKeyCredentialService.authenticate(rawKey)).isEmpty();
  }

  @Test
  void 테이블에_있어도_만료_시각이_지났으면_인증하지_않는다() {
    clock.fix(NOW);
    apiKeyTable.put(snapshotExpiringAt(NOW));

    assertThat(apiKeyCredentialService.authenticate(rawKey)).isEmpty();
  }

  @Test
  void 테스트_환경_키의_한도는_키에_넣은_값과_플랫폼_상한_가운데_작은_쪽이다() {
    assertThat(limitOf(snapshot(UUID.randomUUID(), "test", null))).isEqualTo(TEST_LIMIT);
    assertThat(limitOf(snapshot(UUID.randomUUID(), "test", TEST_LIMIT + 1))).isEqualTo(TEST_LIMIT);
    assertThat(limitOf(snapshot(UUID.randomUUID(), "test", TEST_LIMIT - 1)))
        .isEqualTo(TEST_LIMIT - 1);
  }

  @Test
  void 실제_환경_키와_환경이_없는_키의_한도는_키에_넣은_값_그대로다() {
    assertThat(limitOf(snapshot(UUID.randomUUID(), "live", null))).isNull();
    assertThat(limitOf(snapshot(UUID.randomUUID(), "live", TEST_LIMIT + 1)))
        .isEqualTo(TEST_LIMIT + 1);
    assertThat(limitOf(snapshot(null, "test", null))).isNull();
    assertThat(limitOf(snapshot(null, "test", TEST_LIMIT + 1))).isEqualTo(TEST_LIMIT + 1);
  }

  private Integer limitOf(ApiKeySnapshot snapshot) {
    apiKeyTable.put(snapshot);
    return apiKeyCredentialService.authenticate(rawKey).orElseThrow().rateLimitPerMinute();
  }

  private ApiKeySnapshot snapshot(UUID environmentId, String mode, Integer rateLimitPerMinute) {
    return new ApiKeySnapshot(
        ApiKeyHash.of(rawKey),
        UUID.randomUUID(),
        UUID.randomUUID(),
        environmentId,
        mode,
        Set.of("ingest"),
        rateLimitPerMinute,
        null,
        null);
  }

  private ApiKeySnapshot snapshotExpiringAt(OffsetDateTime expiresAt) {
    return new ApiKeySnapshot(
        ApiKeyHash.of(rawKey),
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        "live",
        Set.of("ingest"),
        30,
        expiresAt,
        null);
  }
}
