package com.meterengine.mail.service;

import java.util.Objects;

public record EmailMessage(String to, String subject, String text, String html) {

  public EmailMessage {
    Objects.requireNonNull(to);
    Objects.requireNonNull(subject);
    Objects.requireNonNull(text);
    Objects.requireNonNull(html);
  }

  // 본문에 일회용 링크가 들어가므로 toString이 로그로 새어 나가지 않게 받는 주소와 제목만 남긴다.
  @Override
  public String toString() {
    return "EmailMessage[to=" + to + ", subject=" + subject + "]";
  }
}
