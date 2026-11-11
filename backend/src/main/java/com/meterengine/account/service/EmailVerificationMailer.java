package com.meterengine.account.service;

import com.meterengine.account.config.EmailVerificationProperties;
import com.meterengine.global.security.SecureTokens;
import com.meterengine.mail.service.EmailMessage;
import com.meterengine.mail.service.EmailSender;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class EmailVerificationMailer implements DisposableBean {

  static final String SEND_COUNTER = "meterengine.email.verification.send";

  static final String SUBJECT = "[MeterEngine] 이메일 주소를 인증해 주세요";

  static final int SEND_THREADS = 2;

  static final int SEND_QUEUE_CAPACITY = 100;

  private static final Logger logger = LoggerFactory.getLogger(EmailVerificationMailer.class);

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  private static final DateTimeFormatter EXPIRES_AT_FORMAT =
      DateTimeFormatter.ofPattern("yyyy년 M월 d일 HH:mm");

  private final EmailSender emailSender;
  private final EmailVerificationProperties emailVerificationProperties;
  private final ExecutorService sendExecutor;
  private final Counter sentCounter;
  private final Counter loggedCounter;
  private final Counter failedCounter;

  @Autowired
  EmailVerificationMailer(
      EmailSender emailSender,
      EmailVerificationProperties emailVerificationProperties,
      MeterRegistry meterRegistry) {
    this(emailSender, emailVerificationProperties, meterRegistry, sendExecutor());
  }

  EmailVerificationMailer(
      EmailSender emailSender,
      EmailVerificationProperties emailVerificationProperties,
      MeterRegistry meterRegistry,
      ExecutorService sendExecutor) {
    this.emailSender = emailSender;
    this.emailVerificationProperties = emailVerificationProperties;
    this.sendExecutor = sendExecutor;
    this.sentCounter =
        Counter.builder(SEND_COUNTER).tag("outcome", "success").register(meterRegistry);
    this.loggedCounter =
        Counter.builder(SEND_COUNTER).tag("outcome", "logged").register(meterRegistry);
    this.failedCounter =
        Counter.builder(SEND_COUNTER).tag("outcome", "failure").register(meterRegistry);
    if (emailVerificationProperties.enabled() && !emailSender.delivers()) {
      logger.warn(
          "Email verification is required but the mail sender only logs. New accounts cannot receive the verification mail");
    }
  }

  // 상한을 넘으면 버린다. 요청 스레드가 대신 보내면 SES를 기다리는 동안 가입 응답이 묶인다.
  private static ExecutorService sendExecutor() {
    return new ThreadPoolExecutor(
        SEND_THREADS,
        SEND_THREADS,
        0,
        TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(SEND_QUEUE_CAPACITY),
        Thread.ofPlatform().name("email-verification-mail-", 0).daemon(true).factory(),
        new ThreadPoolExecutor.AbortPolicy());
  }

  // 커밋 뒤 발송 스레드에 넘기기만 한다. 가입은 이미 커밋됐으므로 발송이 실패해도 되돌리지 않고,
  // 서버가 내려가며 대기 중인 메일을 잃어도 사용자는 다시 받기로 새 메일을 받는다.
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void send(EmailVerificationIssued issued) {
    EmailMessage message = message(issued);
    try {
      sendExecutor.execute(() -> deliver(message));
    } catch (RejectedExecutionException exception) {
      failedCounter.increment();
      logger.warn(
          "Email verification mail dropped, send queue full to_hash={}", recipientHash(message));
    }
  }

  private void deliver(EmailMessage message) {
    try {
      emailSender.send(message);
      (emailSender.delivers() ? sentCounter : loggedCounter).increment();
    } catch (RuntimeException exception) {
      failedCounter.increment();
      logger.warn(
          "Email verification mail failed to_hash={} cause={}",
          recipientHash(message),
          exception.toString());
    }
  }

  // 받는 주소를 로그에 남기지 않고, 같은 주소의 실패끼리 묶어 볼 수 있게 해시 앞부분만 남긴다.
  private static String recipientHash(EmailMessage message) {
    return HexFormat.of().formatHex(SecureTokens.hash(message.to())).substring(0, 12);
  }

  @Override
  public void destroy() {
    sendExecutor.shutdownNow();
  }

  EmailMessage message(EmailVerificationIssued issued) {
    String link =
        UriComponentsBuilder.fromUri(emailVerificationProperties.linkBaseUrl())
            .replacePath("/email-verifications/" + issued.token())
            .build()
            .toUriString();
    String expiresAt = issued.expiresAt().atZoneSameInstant(KST).format(EXPIRES_AT_FORMAT);
    String text =
        """
        MeterEngine 가입을 마치려면 아래 링크를 열어 이메일 주소를 인증해 주세요.

        %s

        이 링크는 %s(한국 시간)까지 한 번만 쓸 수 있어요.

        직접 가입하지 않았다면 이 메일을 무시해 주세요. 인증하지 않은 계정으로는 로그인할 수 없어요.
        """
            .formatted(link, expiresAt);
    String escapedLink = HtmlUtils.htmlEscape(link);
    String html =
        """
        <!doctype html>
        <html lang="ko">
        <body style="font-family: sans-serif; line-height: 1.6; color: #1f2328;">
        <p>MeterEngine 가입을 마치려면 아래 버튼을 눌러 이메일 주소를 인증해 주세요.</p>
        <p><a href="%s" style="display: inline-block; padding: 10px 18px; background: #1f6feb; color: #ffffff; text-decoration: none; border-radius: 6px;">이메일 인증하기</a></p>
        <p>버튼이 열리지 않으면 아래 주소를 브라우저에 붙여 넣어 주세요.<br><a href="%s">%s</a></p>
        <p>이 링크는 %s(한국 시간)까지 한 번만 쓸 수 있어요.</p>
        <p>직접 가입하지 않았다면 이 메일을 무시해 주세요. 인증하지 않은 계정으로는 로그인할 수 없어요.</p>
        </body>
        </html>
        """
            .formatted(escapedLink, escapedLink, escapedLink, expiresAt);
    return new EmailMessage(issued.email(), SUBJECT, text, html);
  }
}
