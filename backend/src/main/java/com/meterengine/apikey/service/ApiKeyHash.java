package com.meterengine.apikey.service;

import com.meterengine.global.security.SecureTokens;
import java.util.Arrays;

// 바이트 배열은 내용이 같아도 다른 맵 키가 되므로, 키 테이블에서 내용으로 찾으려고 감싼다.
public record ApiKeyHash(byte[] value) {

  public ApiKeyHash {
    value = value.clone();
  }

  public static ApiKeyHash of(String rawKey) {
    return new ApiKeyHash(SecureTokens.hash(rawKey));
  }

  @Override
  public byte[] value() {
    return value.clone();
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof ApiKeyHash that && Arrays.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(value);
  }
}
