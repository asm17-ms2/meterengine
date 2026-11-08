package com.meterengine.apikey.service;

import com.meterengine.global.security.ApiKeyCredentials;
import com.meterengine.global.security.AuthenticatedCredential;
import com.meterengine.global.security.SecureTokens;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class ApiKeyCredentialService implements ApiKeyCredentials {

  private final ApiKeyTable apiKeyTable;
  private final Clock clock;

  public ApiKeyCredentialService(ApiKeyTable apiKeyTable, Clock clock) {
    this.apiKeyTable = apiKeyTable;
    this.clock = clock;
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
        .map(ApiKeySnapshot::credential);
  }
}
