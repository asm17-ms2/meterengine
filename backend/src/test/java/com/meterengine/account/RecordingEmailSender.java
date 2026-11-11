package com.meterengine.account;

import com.meterengine.mail.service.EmailMessage;
import com.meterengine.mail.service.EmailSender;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

// 발송 스레드에서 불리므로 mock 대신 쓴다. Mockito는 다른 스레드의 호출을 검증할 때 흔들린다.
final class RecordingEmailSender implements EmailSender {

  private static final Duration WAIT = Duration.ofSeconds(10);

  private final BlockingQueue<EmailMessage> sent = new LinkedBlockingQueue<>();
  private final AtomicInteger inFlight = new AtomicInteger();
  private volatile RuntimeException failure;
  private volatile CountDownLatch gate = new CountDownLatch(0);

  @Override
  public void send(EmailMessage message) {
    inFlight.incrementAndGet();
    try {
      if (!gate.await(30, TimeUnit.SECONDS)) throw new IllegalStateException("gate not released");
      RuntimeException current = failure;
      if (current != null) throw current;
      sent.add(message);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(exception);
    } finally {
      inFlight.decrementAndGet();
    }
  }

  @Override
  public boolean delivers() {
    return true;
  }

  void reset() {
    failure = null;
    gate.countDown();
    gate = new CountDownLatch(0);
  }

  void failWith(RuntimeException exception) {
    failure = exception;
  }

  void hold() {
    gate = new CountDownLatch(1);
  }

  void release() {
    gate.countDown();
  }

  int inFlight() {
    return inFlight.get();
  }

  // 앞선 시험의 메일이 늦게 도착할 수 있어 받는 주소로 골라낸다.
  EmailMessage next(String to) {
    long deadline = System.nanoTime() + WAIT.toNanos();
    try {
      while (true) {
        long left = deadline - System.nanoTime();
        EmailMessage message = left > 0 ? sent.poll(left, TimeUnit.NANOSECONDS) : null;
        if (message == null) throw new AssertionError("no verification mail to " + to);
        if (message.to().equals(to)) return message;
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new AssertionError(exception);
    }
  }
}
