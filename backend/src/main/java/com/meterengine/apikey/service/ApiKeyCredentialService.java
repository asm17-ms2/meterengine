package com.meterengine.apikey.service;

import com.meterengine.apikey.config.ApiKeyProperties;
import com.meterengine.global.security.ApiKeyCredentials;
import com.meterengine.global.security.AuthenticatedCredential;
import com.meterengine.global.security.SecureTokens;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class ApiKeyCredentialService implements ApiKeyCredentials {

  private static final String TEST_MODE = "test";

  private final ApiKeyTable apiKeyTable;
  private final Clock clock;
  private final int testRateLimitPerMinute;

  public ApiKeyCredentialService(
      ApiKeyTable apiKeyTable, Clock clock, ApiKeyProperties apiKeyProperties) {
    this.apiKeyTable = apiKeyTable;
    this.clock = clock;
    this.testRateLimitPerMinute = apiKeyProperties.testRateLimitPerMinute();
  }

  // 테이블에 없는 키는 DB에 묻지 않고 거절한다. 수집이 DB 상태에 묶이지 않게 하려는 것이다.
  // 만료 시각은 테이블을 다시 읽는 사이에도 지나가므로 지금 시각으로 다시 본다.
  @Override
  public Optional<AuthenticatedCredential> authenticate(String token) {
    if (!SecureTokens.isApiKey(token)) {
      return Optional.empty();
    }
    OffsetDateTime now = OffsetDateTime.now(clock);
    return apiKeyTable
        .find(ApiKeyHash.of(token))
        .filter(snapshot -> snapshot.isUsableAt(now))
        .map(
            snapshot ->
                limited(
                    snapshot,
                    snapshot.credential(
                        apiKeyTable.isOrganizationActive(snapshot.organizationId()))));
  }

  // 테스트 환경은 가입만 하면 열리므로 그 환경의 키는 플랫폼 상한을 넘겨 쓰지 못한다.
  private AuthenticatedCredential limited(
      ApiKeySnapshot snapshot, AuthenticatedCredential credential) {
    if (snapshot.environmentId() == null || !TEST_MODE.equals(snapshot.mode())) {
      return credential;
    }
    Integer rateLimitPerMinute = credential.rateLimitPerMinute();
    return new AuthenticatedCredential(
        credential.principal(),
        rateLimitPerMinute == null
            ? testRateLimitPerMinute
            : Math.min(rateLimitPerMinute, testRateLimitPerMinute),
        credential.organizationActive());
  }
}
