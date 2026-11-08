package com.meterengine.apikey.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meterengine.TestClock;
import com.meterengine.global.security.SecureTokens;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ApiKeyCredentialServiceTest {

  private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-08T00:00:00Z");

  private final ApiKeyTable apiKeyTable = new ApiKeyTable();
  private final TestClock clock = new TestClock();
  private final ApiKeyCredentialService apiKeyCredentialService =
      new ApiKeyCredentialService(apiKeyTable, clock);

  private final String rawKey = SecureTokens.generate("me_live_");

  @Test
  void 테이블에_있는_키는_그_항목으로_자격_증명을_만든다() {
    ApiKeySnapshot snapshot = snapshotExpiringAt(null);
    apiKeyTable.put(snapshot);

    assertThat(apiKeyCredentialService.authenticate(rawKey)).contains(snapshot.credential());
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
