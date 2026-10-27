package com.meterengine.webhook.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meterengine.webhook.dto.WebhookOutcome;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WebhookClientTest {
  private static final char[] PASSWORD = "changeit".toCharArray();

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

  @Test
  void 운영_정책은_전송_시점에_차단_주소면_연결하지_않고_blocked_address로_끝낸다() throws Exception {
    try (ServerSocket serverSocket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
        WebhookClient client =
            new WebhookClient(new WebhookAddressPolicy(false), Duration.ofSeconds(2))) {
      WebhookOutcome outcome =
          client.send(
              "https://127.0.0.1:" + serverSocket.getLocalPort() + "/hook",
              "event",
              "1",
              "v1,signature",
              "{}");

      assertThat(outcome.failureReason()).isEqualTo("blocked_address");
      assertThat(outcome.responseStatus()).isNull();
      serverSocket.setSoTimeout(200);
      assertThatThrownBy(serverSocket::accept).isInstanceOf(SocketTimeoutException.class);
    }
  }

  @Test
  void HTTPS는_고정한_주소_위에서_인증서의_호스트를_검증한_뒤_전송한다(@TempDir Path directory) throws Exception {
    KeyStore keyStore = selfSignedKeyStore(directory, "localhost");
    InetAddress address = InetAddress.getAllByName("localhost")[0];
    try (SSLServerSocket serverSocket =
            (SSLServerSocket)
                serverContext(keyStore).getServerSocketFactory().createServerSocket(0, 1, address);
        var receiver = Executors.newSingleThreadExecutor();
        WebhookClient client =
            new WebhookClient(
                new WebhookAddressPolicy(true),
                Duration.ofSeconds(5),
                clientContext(keyStore).getSocketFactory())) {
      var received =
          receiver.submit(
              () -> {
                try (SSLSocket socket = (SSLSocket) serverSocket.accept()) {
                  socket.setSoTimeout(5000);
                  socket.startHandshake();
                  String request = readRequest(socket.getInputStream());
                  socket
                      .getOutputStream()
                      .write(
                          "HTTP/1.1 204 No Content\r\nContent-Length: 0\r\n\r\n"
                              .getBytes(StandardCharsets.US_ASCII));
                  socket.getOutputStream().flush();
                  return request;
                }
              });

      WebhookOutcome outcome =
          client.send(
              "https://localhost:" + serverSocket.getLocalPort() + "/hook",
              "event",
              "1",
              "v1,signature",
              "{}");

      assertThat(outcome.failureReason()).isNull();
      assertThat(outcome.responseStatus()).isEqualTo(204);
      assertThat(received.get(5, TimeUnit.SECONDS))
          .startsWith("POST /hook HTTP/1.1\r\n")
          .contains("webhook-id: event\r\n")
          .endsWith("{}");
    }
  }

  @Test
  void HTTPS_인증서의_호스트가_다르면_요청을_보내지_않고_connection_error로_끝난다(@TempDir Path directory)
      throws Exception {
    KeyStore keyStore = selfSignedKeyStore(directory, "other.example");
    InetAddress address = InetAddress.getAllByName("localhost")[0];
    try (SSLServerSocket serverSocket =
            (SSLServerSocket)
                serverContext(keyStore).getServerSocketFactory().createServerSocket(0, 1, address);
        var receiver = Executors.newSingleThreadExecutor();
        WebhookClient client =
            new WebhookClient(
                new WebhookAddressPolicy(true),
                Duration.ofSeconds(5),
                clientContext(keyStore).getSocketFactory())) {
      var handshake =
          receiver.submit(
              () -> {
                try (SSLSocket socket = (SSLSocket) serverSocket.accept()) {
                  socket.setSoTimeout(5000);
                  try {
                    socket.startHandshake();
                    return readRequest(socket.getInputStream());
                  } catch (SSLException exception) {
                    return null;
                  }
                }
              });

      WebhookOutcome outcome =
          client.send(
              "https://localhost:" + serverSocket.getLocalPort() + "/hook",
              "event",
              "1",
              "v1,signature",
              "{}");

      assertThat(outcome.failureReason()).isEqualTo("connection_error");
      assertThat(outcome.responseStatus()).isNull();
      assertThat(handshake.get(5, TimeUnit.SECONDS)).isNull();
    }
  }

  private static KeyStore selfSignedKeyStore(Path directory, String host) throws Exception {
    Path file = directory.resolve(host + ".p12");
    Process process =
        new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "keytool").toString(),
                "-genkeypair",
                "-alias",
                "webhook",
                "-keyalg",
                "EC",
                "-groupname",
                "secp256r1",
                "-dname",
                "CN=" + host,
                "-ext",
                "SAN=dns:" + host,
                "-validity",
                "1",
                "-storetype",
                "PKCS12",
                "-keystore",
                file.toString(),
                "-storepass",
                new String(PASSWORD))
            .redirectErrorStream(true)
            .start();
    String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    assertThat(process.waitFor(30, TimeUnit.SECONDS)).isTrue();
    assertThat(process.exitValue()).as(output).isZero();
    KeyStore keyStore = KeyStore.getInstance("PKCS12");
    try (InputStream input = Files.newInputStream(file)) {
      keyStore.load(input, PASSWORD);
    }
    return keyStore;
  }

  private static SSLContext serverContext(KeyStore keyStore) throws Exception {
    KeyManagerFactory keyManagers =
        KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    keyManagers.init(keyStore, PASSWORD);
    SSLContext context = SSLContext.getInstance("TLS");
    context.init(keyManagers.getKeyManagers(), null, null);
    return context;
  }

  private static SSLContext clientContext(KeyStore keyStore) throws Exception {
    KeyStore trusted = KeyStore.getInstance("PKCS12");
    trusted.load(null, null);
    trusted.setCertificateEntry("webhook", keyStore.getCertificate("webhook"));
    TrustManagerFactory trustManagers =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    trustManagers.init(trusted);
    SSLContext context = SSLContext.getInstance("TLS");
    context.init(null, trustManagers.getTrustManagers(), null);
    return context;
  }

  private static String readRequest(InputStream input) throws IOException {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    int contentLength = -1;
    while (true) {
      int value = input.read();
      if (value == -1) {
        break;
      }
      buffer.write(value);
      String text = buffer.toString(StandardCharsets.US_ASCII);
      if (contentLength < 0 && text.endsWith("\r\n\r\n")) {
        contentLength =
            text.lines()
                .filter(line -> line.toLowerCase().startsWith("content-length:"))
                .map(line -> Integer.parseInt(line.substring(line.indexOf(':') + 1).trim()))
                .findFirst()
                .orElse(0);
        buffer.write(input.readNBytes(contentLength));
        break;
      }
    }
    return buffer.toString(StandardCharsets.UTF_8);
  }
}
