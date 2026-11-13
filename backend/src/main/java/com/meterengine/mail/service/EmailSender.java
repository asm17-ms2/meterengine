package com.meterengine.mail.service;

public interface EmailSender {

  // 보내지 못하면 예외를 던진다. 다시 보낼지는 부른 쪽이 정한다.
  void send(EmailMessage message);

  // 받는 사람에게 실제로 메일이 가면 true다. 로그로만 남기는 발송기는 false다.
  boolean delivers();
}
