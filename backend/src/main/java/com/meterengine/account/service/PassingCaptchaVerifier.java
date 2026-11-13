package com.meterengine.account.service;

// 캡차를 끈 환경(로컬, 시험, 위젯을 만들기 전 운영)에서 쓴다. 토큰이 없어도 통과시킨다.
public class PassingCaptchaVerifier implements CaptchaVerifier {

  @Override
  public void verify(String token, String action, String remoteAddress) {}
}
