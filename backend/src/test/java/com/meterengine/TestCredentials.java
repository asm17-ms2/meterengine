package com.meterengine;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public final class TestCredentials {

  public static final String BEARER_PREFIX = "Bearer ";

  private static final String ALPHABET =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

  private static final int SECRET_LENGTH = 32;

  private static final List<String> MODES = List.of("live", "test");

  private static final String UNUSED_PASSWORD_HASH =
      "{bcrypt}$2a$10$Kqg4kLMfEy5ainWZ/SYDPOzmdZZi9Edq3zdE2tYtMeFpeTW1.ODGq";

  private static final SecureRandom RANDOM = new SecureRandom();

  private TestCredentials() {}

  // 도입사에 API 키를 넣고 Authorization 헤더 값을 돌려준다.
  public static String issueApiKey(
      JdbcTemplate jdbcTemplate, UUID organizationId, String... modeAndScopes) {
    boolean modeGiven = modeAndScopes.length > 0 && MODES.contains(modeAndScopes[0]);
    String mode = modeGiven ? modeAndScopes[0] : "live";
    List<String> scopes =
        Arrays.asList(modeAndScopes).subList(modeGiven ? 1 : 0, modeAndScopes.length);
    String rawKey = "me_" + mode + "_" + randomSecret();

    jdbcTemplate.update(
        "INSERT INTO organization (id, name) VALUES (?, '테스트 도입사') ON CONFLICT (id) DO NOTHING",
        organizationId);
    jdbcTemplate.update(
        """
        INSERT INTO api_key
          (id, organization_id, mode, label, key_hash, last_four, scopes, created_at)
        VALUES (?, ?, ?, '테스트 키', sha256(convert_to(?, 'UTF8')), ?, CAST(? AS VARCHAR[]), now())
        """,
        UUID.randomUUID(),
        organizationId,
        mode,
        rawKey,
        rawKey.substring(rawKey.length() - 4),
        "{" + String.join(",", scopes.isEmpty() ? List.of("admin") : scopes) + "}");
    return BEARER_PREFIX + rawKey;
  }

  // 도입사에 소속된 member와 그 도입사를 고른 로그인 세션을 넣고 Authorization 헤더 값을 돌려준다.
  public static String issueMemberSession(JdbcTemplate jdbcTemplate, UUID organizationId) {
    jdbcTemplate.update(
        "INSERT INTO organization (id, name) VALUES (?, '테스트 도입사') ON CONFLICT (id) DO NOTHING",
        organizationId);
    UUID userAccountId = insertUserAccount(jdbcTemplate, "member");
    jdbcTemplate.update(
        "INSERT INTO organization_member (organization_id, user_account_id, created_at) VALUES (?, ?, now())",
        organizationId,
        userAccountId);
    return insertSession(jdbcTemplate, userAccountId, organizationId);
  }

  // 소속 없는 operator와 도입사가 없는 로그인 세션을 넣고 Authorization 헤더 값을 돌려준다.
  public static String issueOperatorSession(JdbcTemplate jdbcTemplate) {
    return insertSession(jdbcTemplate, insertUserAccount(jdbcTemplate, "operator"), null);
  }

  public static String rawKeyOf(String authorization) {
    return authorization.substring(BEARER_PREFIX.length());
  }

  private static UUID insertUserAccount(JdbcTemplate jdbcTemplate, String role) {
    UUID userAccountId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO user_account (id, email, password_hash, name, role, created_at)
        VALUES (?, ?, ?, '테스트 사용자', ?, now())
        """,
        userAccountId,
        userAccountId + "@test.meterengine.local",
        UNUSED_PASSWORD_HASH,
        role);
    return userAccountId;
  }

  private static String insertSession(
      JdbcTemplate jdbcTemplate, UUID userAccountId, UUID organizationId) {
    String rawToken = "me_session_" + randomSecret();
    jdbcTemplate.update(
        """
        INSERT INTO user_session
          (id, user_account_id, token_hash, organization_id, created_at, expires_at)
        VALUES (?, ?, sha256(convert_to(?, 'UTF8')), ?, now(), now() + interval '12 hours')
        """,
        UUID.randomUUID(),
        userAccountId,
        rawToken,
        organizationId);
    return BEARER_PREFIX + rawToken;
  }

  private static String randomSecret() {
    StringBuilder secret = new StringBuilder(SECRET_LENGTH);
    for (int index = 0; index < SECRET_LENGTH; index++) {
      secret.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
    }
    return secret.toString();
  }
}
