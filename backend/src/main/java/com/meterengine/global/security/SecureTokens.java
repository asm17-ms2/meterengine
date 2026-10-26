package com.meterengine.global.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.regex.Pattern;

public final class SecureTokens {

  public static final String LIVE_API_KEY_PREFIX = "me_live_";

  public static final String TEST_API_KEY_PREFIX = "me_test_";

  public static final String SESSION_TOKEN_PREFIX = "me_session_";

  private static final Pattern API_KEY_PATTERN =
      Pattern.compile("^me_(live|test)_[A-Za-z0-9]{32}$");

  private static final Pattern SESSION_TOKEN_PATTERN =
      Pattern.compile("^me_session_[A-Za-z0-9]{32}$");

  private static final String BASE62_ALPHABET =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

  private static final int SECRET_LENGTH = 32;

  private static final int LAST_FOUR_LENGTH = 4;

  private static final String HASH_ALGORITHM = "SHA-256";

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private SecureTokens() {}

  // 접두사 뒤에 난수 base62 32자를 붙인 원문을 만든다.
  public static String generate(String prefix) {
    StringBuilder token = new StringBuilder(prefix.length() + SECRET_LENGTH).append(prefix);
    for (int index = 0; index < SECRET_LENGTH; index++) {
      token.append(BASE62_ALPHABET.charAt(SECURE_RANDOM.nextInt(BASE62_ALPHABET.length())));
    }
    return token.toString();
  }

  // 원문의 UTF-8 바이트를 SHA-256으로 줄인 32바이트를 돌려준다.
  public static byte[] hash(String raw) {
    try {
      return MessageDigest.getInstance(HASH_ALGORITHM).digest(raw.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException(HASH_ALGORITHM + " is not available", exception);
    }
  }

  // 화면과 로그에 원문 대신 보여 줄 원문 끝 네 글자를 돌려준다.
  public static String lastFour(String raw) {
    return raw.substring(raw.length() - LAST_FOUR_LENGTH);
  }

  // API 키 원문의 접두사에서 모드(live, test)를 돌려주고, 키 형식이 아니면 예외를 던진다.
  public static String apiKeyMode(String rawApiKey) {
    if (!isApiKey(rawApiKey)) {
      throw new IllegalArgumentException("value is not an API key");
    }
    return rawApiKey.startsWith(LIVE_API_KEY_PREFIX) ? "live" : "test";
  }

  public static boolean isApiKey(String raw) {
    return API_KEY_PATTERN.matcher(raw).matches();
  }

  public static boolean isSessionToken(String raw) {
    return SESSION_TOKEN_PATTERN.matcher(raw).matches();
  }
}
