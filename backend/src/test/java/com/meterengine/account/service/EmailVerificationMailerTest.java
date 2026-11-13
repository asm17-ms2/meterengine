package com.meterengine.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.meterengine.account.config.EmailVerificationProperties;
import com.meterengine.mail.service.EmailMessage;
import com.meterengine.mail.service.EmailSender;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.URI;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class EmailVerificationMailerTest {

  private static final EmailVerificationProperties PROPERTIES =
      new EmailVerificationProperties(
          true, Duration.ofHours(24), URI.create("https://example.test"));

  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final CountDownLatch gate = new CountDownLatch(1);
  private final CountDownLatch started = new CountDownLatch(1);
  private ExecutorService executor;

  @AfterEach
  void tearDown() {
    gate.countDown();
    if (executor != null) executor.shutdownNow();
  }

  @Test
  void 발송_대기열이_차면_요청을_막지_않고_메일을_버리며_실패로_센다() throws InterruptedException {
    executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1));
    EmailVerificationMailer mailer =
        new EmailVerificationMailer(blockingSender(true), PROPERTIES, meterRegistry, executor);

    mailer.send(issued("first@example.test"));
    assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
    mailer.send(issued("queued@example.test"));

    assertThatCode(() -> mailer.send(issued("dropped@example.test"))).doesNotThrowAnyException();
    assertThat(count("failure")).isEqualTo(1);
    assertThat(count("success")).isZero();
  }

  @Test
  void 로그로만_남기는_발송기는_success가_아니라_logged로_센다() throws InterruptedException {
    executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1));
    gate.countDown();
    EmailVerificationMailer mailer =
        new EmailVerificationMailer(blockingSender(false), PROPERTIES, meterRegistry, executor);

    mailer.send(issued("logged@example.test"));
    executor.shutdown();
    assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

    assertThat(count("logged")).isEqualTo(1);
    assertThat(count("success")).isZero();
  }

  private EmailSender blockingSender(boolean delivers) {
    return new EmailSender() {
      @Override
      public void send(EmailMessage message) {
        started.countDown();
        try {
          gate.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
          Thread.currentThread().interrupt();
        }
      }

      @Override
      public boolean delivers() {
        return delivers;
      }
    };
  }

  private double count(String outcome) {
    return meterRegistry.counter(EmailVerificationMailer.SEND_COUNTER, "outcome", outcome).count();
  }

  private static EmailVerificationIssued issued(String email) {
    return new EmailVerificationIssued(
        email, "me_verify_" + "a".repeat(32), OffsetDateTime.now().plusHours(24));
  }
}
