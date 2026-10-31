package com.meterengine.global.database;

import java.net.URI;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public final class EnvironmentSideEffectPolicy {
  private final CutoverPolicy cutover;
  private final Set<Destination> allowed;

  public EnvironmentSideEffectPolicy(CutoverPolicy cutover, Environment environment) {
    this.cutover = cutover;
    var entries = new HashSet<Destination>();
    String configured = environment.getProperty("METERENGINE_STAGING_WEBHOOK_ALLOWED_URLS", "");
    if (!configured.isEmpty()) {
      for (String entry : configured.split(",", -1)) {
        if (!entries.add(destination(entry.trim())))
          throw new IllegalArgumentException("duplicate staging webhook URL");
      }
    }
    allowed = Set.copyOf(entries);
  }

  public void requirePayment(String secretKey) {
    cutover.requireWritable();
    if (cutover.staging() && (secretKey == null || !secretKey.startsWith("test_sk_")))
      throw new IllegalStateException("staging requires TEST payment credentials");
  }

  public void requirePaymentMode(String mode) {
    cutover.requireWritable();
    if (cutover.staging() && !"test".equals(mode))
      throw new com.meterengine.global.error.ConflictException(
          com.meterengine.global.error.ErrorCode.PAYMENT_MODE_MISMATCH);
  }

  public boolean allowsWebhook(String url) {
    if (cutover.readOnly()) return false;
    if (!cutover.staging()) return true;
    try {
      return allowed.contains(destination(url));
    } catch (IllegalArgumentException exception) {
      return false;
    }
  }

  private static Destination destination(String value) {
    URI uri = URI.create(value);
    if (!uri.isAbsolute()
        || !"https".equalsIgnoreCase(uri.getScheme())
        || uri.getHost() == null
        || uri.getHost().isEmpty()
        || !uri.getHost().chars().allMatch(c -> c < 128)
        || uri.getRawUserInfo() != null
        || uri.getRawFragment() != null
        || uri.getRawAuthority().endsWith(":")
        || (uri.getPort() != -1 && (uri.getPort() < 1 || uri.getPort() > 65535)))
      throw new IllegalArgumentException("invalid staging webhook URL");
    String path = uri.getRawPath();
    return new Destination(
        uri.getHost().toLowerCase(Locale.ROOT),
        uri.getPort() == -1 ? 443 : uri.getPort(),
        path == null || path.isEmpty() ? "/" : path,
        uri.getRawQuery());
  }

  private record Destination(String host, int port, String path, String query) {}
}
