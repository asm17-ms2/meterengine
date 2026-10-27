package com.meterengine.webhook.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.meterengine.webhook.dto.WebhookOutcome;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class WebhookClientTest {
  @Test
  void 수신기가_본문을_읽지_않아_write가_막혀도_전체_기한에_취소하고_소켓을_닫는다() throws Exception {
    String body = "x".repeat(16 * 1024 * 1024);
    CountDownLatch accepted = new CountDownLatch(1);
    CountDownLatch allowRead = new CountDownLatch(1);
    try (ServerSocket serverSocket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
        var receiver = Executors.newSingleThreadExecutor();
        WebhookClient client =
            new WebhookClient(new WebhookAddressPolicy(true), Duration.ofMillis(500))) {
      serverSocket.setReceiveBufferSize(1024);
      var received =
          receiver.submit(
              () -> {
                try (Socket socket = serverSocket.accept()) {
                  accepted.countDown();
                  allowRead.await(5, TimeUnit.SECONDS);
                  socket.setSoTimeout(2000);
                  byte[] buffer = new byte[8192];
                  int total = 0;
                  try {
                    int length;
                    while ((length = socket.getInputStream().read(buffer)) != -1) {
                      total += length;
                    }
                  } catch (java.net.SocketTimeoutException exception) {
                    throw new AssertionError("canceled webhook socket remained open", exception);
                  } catch (IOException exception) {
                    return total;
                  }
                  return total;
                }
              });
      long started = System.nanoTime();
      WebhookOutcome outcome =
          client.send(
              "http://127.0.0.1:" + serverSocket.getLocalPort() + "/webhook",
              "event",
              "1",
              "v1,signature",
              body);
      assertThat(accepted.await(1, TimeUnit.SECONDS)).isTrue();
      assertThat(outcome.failureReason()).isEqualTo("timeout");
      assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(3));
      allowRead.countDown();
      assertThat(received.get(5, TimeUnit.SECONDS)).isPositive().isLessThan(body.length());
    } finally {
      allowRead.countDown();
    }
  }
}
