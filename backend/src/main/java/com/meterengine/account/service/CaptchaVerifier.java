package com.meterengine.account.service;

public interface CaptchaVerifier {

  // 통과하지 못하면 InvalidRequestException(captcha_failed), 결과를 받지 못하면
  // ServiceUnavailableException(captcha_unavailable)을 던진다. remoteAddress는 없으면 null이다.
  void verify(String token, String action, String remoteAddress);
}
