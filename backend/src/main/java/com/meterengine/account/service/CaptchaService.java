package com.meterengine.account.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

@Service
public class CaptchaService {

  // 위젯이 토큰에 싣는 action이다. 프론트의 위젯 action과 같아야 한다.
  public static final String SIGNUP_ACTION = "signup";
  public static final String EMAIL_VERIFICATION_ACTION = "email_verification";

  private static final String UNKNOWN_PEER = "unknown-peer";

  private final CaptchaVerifier captchaVerifier;
  private final AuthRequestRateLimitService authRequestRateLimitService;

  CaptchaService(
      CaptchaVerifier captchaVerifier, AuthRequestRateLimitService authRequestRateLimitService) {
    this.captchaVerifier = captchaVerifier;
    this.authRequestRateLimitService = authRequestRateLimitService;
  }

  // 요청 수 제한과 같은 규칙으로 고른 사용자 IP를 Siteverify에 함께 넘긴다.
  public void verify(String token, String action, HttpServletRequest request) {
    String address = authRequestRateLimitService.clientAddress(request);
    captchaVerifier.verify(token, action, UNKNOWN_PEER.equals(address) ? null : address);
  }
}
