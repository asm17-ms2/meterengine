package com.meterengine.webhook.client;

import com.meterengine.webhook.dto.WebhookOutcome;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class WebhookClient implements AutoCloseable {
  private static final int CONNECT_TIMEOUT_MS = 5000;
  private static final int READ_TIMEOUT_MS = 10000;
  private final WebhookAddressPolicy webhookAddressPolicy;
  private final Duration requestTimeout;
  private final SSLSocketFactory sslSocketFactory;
  private final ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();

  @Autowired
  WebhookClient(WebhookAddressPolicy webhookAddressPolicy) {
    this(webhookAddressPolicy, Duration.ofSeconds(15));
  }

  WebhookClient(WebhookAddressPolicy webhookAddressPolicy, Duration requestTimeout) {
    this(webhookAddressPolicy, requestTimeout, (SSLSocketFactory) SSLSocketFactory.getDefault());
  }

  WebhookClient(
      WebhookAddressPolicy webhookAddressPolicy,
      Duration requestTimeout,
      SSLSocketFactory sslSocketFactory) {
    this.webhookAddressPolicy = webhookAddressPolicy;
    this.requestTimeout = requestTimeout;
    this.sslSocketFactory = sslSocketFactory;
  }

  public WebhookOutcome send(
      String url, String id, String timestamp, String signature, String body) {
    long started = System.nanoTime();
    Future<WebhookOutcome> result =
        executorService.submit(() -> sendPinned(url, id, timestamp, signature, body));
    try {
      return result.get(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException exception) {
      result.cancel(true);
      return new WebhookOutcome(null, "timeout", elapsed(started));
    } catch (InterruptedException exception) {
      result.cancel(true);
      Thread.currentThread().interrupt();
      return new WebhookOutcome(null, "connection_error", elapsed(started));
    } catch (ExecutionException exception) {
      return new WebhookOutcome(null, "connection_error", elapsed(started));
    }
  }

  @Override
  @PreDestroy
  public void close() {
    executorService.shutdownNow();
  }

  private WebhookOutcome sendPinned(
      String url, String id, String timestamp, String signature, String body) {
    long started = System.nanoTime();
    try {
      requireNotInterrupted();
      URI uri = URI.create(url);
      InetAddress address = webhookAddressPolicy.resolve(url);
      requireNotInterrupted();
      int port =
          uri.getPort() == -1
              ? ("https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80)
              : uri.getPort();
      try (Socket transport = new Socket()) {
        transport.connect(new InetSocketAddress(address, port), CONNECT_TIMEOUT_MS);
        transport.setSoTimeout(READ_TIMEOUT_MS);
        Socket socket = transport;
        if ("https".equalsIgnoreCase(uri.getScheme())) {
          SSLSocket tls =
              (SSLSocket) sslSocketFactory.createSocket(transport, uri.getHost(), port, true);
          SSLParameters parameters = tls.getSSLParameters();
          parameters.setEndpointIdentificationAlgorithm("HTTPS");
          tls.setSSLParameters(parameters);
          tls.setSoTimeout(READ_TIMEOUT_MS);
          tls.startHandshake();
          socket = tls;
        }
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        String path = uri.getRawPath();
        if (path == null || path.isEmpty()) {
          path = "/";
        }
        if (uri.getRawQuery() != null) {
          path += "?" + uri.getRawQuery();
        }
        requireNotInterrupted();
        String headers =
            "POST "
                + path
                + " HTTP/1.1\r\nHost: "
                + uri.getRawAuthority()
                + "\r\nContent-Type: application/json\r\nContent-Length: "
                + payload.length
                + "\r\nwebhook-id: "
                + id
                + "\r\nwebhook-timestamp: "
                + timestamp
                + "\r\nwebhook-signature: "
                + signature
                + "\r\nConnection: close\r\n\r\n";
        socket.getOutputStream().write(headers.getBytes(StandardCharsets.US_ASCII));
        socket.getOutputStream().write(payload);
        socket.getOutputStream().flush();
        int status = readStatus(socket, System.nanoTime() + READ_TIMEOUT_MS * 1_000_000L);
        return new WebhookOutcome(
            status, status >= 200 && status < 300 ? null : "unexpected_status", elapsed(started));
      }
    } catch (BlockedAddressException exception) {
      return new WebhookOutcome(null, "blocked_address", elapsed(started));
    } catch (SocketTimeoutException exception) {
      return new WebhookOutcome(null, "timeout", elapsed(started));
    } catch (IOException | IllegalArgumentException exception) {
      return new WebhookOutcome(null, "connection_error", elapsed(started));
    }
  }

  private static void requireNotInterrupted() throws SocketTimeoutException {
    if (Thread.currentThread().isInterrupted()) {
      throw new SocketTimeoutException();
    }
  }

  private static int readStatus(Socket socket, long deadline) throws IOException {
    InputStream input = socket.getInputStream();
    StringBuilder statusLine = new StringBuilder();
    while (statusLine.length() < 1024) {
      long remaining = deadline - System.nanoTime();
      if (remaining <= 0) {
        throw new SocketTimeoutException();
      }
      socket.setSoTimeout((int) Math.max(1, remaining / 1_000_000L));
      int value = input.read();
      if (value == -1) {
        throw new IOException("missing HTTP status");
      }
      if (value == '\n') {
        String[] parts = statusLine.toString().trim().split(" +", 3);
        if (parts.length < 2
            || !parts[0].matches("HTTP/1\\.[01]")
            || !parts[1].matches("[1-5][0-9]{2}")) {
          throw new IOException("invalid HTTP status");
        }
        return Integer.parseInt(parts[1]);
      }
      statusLine.append((char) value);
    }
    throw new IOException("HTTP status line too long");
  }

  private static long elapsed(long started) {
    return (System.nanoTime() - started) / 1_000_000L;
  }
}
