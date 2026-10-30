package com.meterengine.global.crypto;

// 비밀값을 로그나 toString에 남길 때 마지막 네 자만 보인다.
public final class SecretMask {

  private static final String MASK = "****";
  private static final int VISIBLE_SUFFIX_LENGTH = 4;
  private static final int MIN_LENGTH_TO_SHOW_SUFFIX = 9;

  private SecretMask() {}

  public static String mask(String secret) {
    if (secret == null) {
      return null;
    }
    if (secret.length() < MIN_LENGTH_TO_SHOW_SUFFIX) {
      return MASK;
    }
    return MASK + secret.substring(secret.length() - VISIBLE_SUFFIX_LENGTH);
  }
}
