package com.meterengine.account.service;

import java.time.OffsetDateTime;

// 인증 메일 하나를 보내라는 사건이다. 가입이나 다시 보내기 트랜잭션이 커밋된 뒤에 보낸다.
public record EmailVerificationIssued(String email, String token, OffsetDateTime expiresAt) {

  @Override
  public String toString() {
    return "EmailVerificationIssued[email="
        + email
        + ", token=(redacted), expiresAt="
        + expiresAt
        + "]";
  }
}
