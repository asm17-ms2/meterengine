package com.meterengine.account.service;

import com.meterengine.account.config.AuthRateLimitProperties;
import com.meterengine.account.repository.AuthRequestRateLimitRepository;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.TooManyRequestsException;
import com.meterengine.global.security.SecureTokens;
import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class AuthRequestRateLimitService {
  public static final String CLIENT_IP_HEADER = "X-MeterEngine-Client-IP";
  private final AuthRequestRateLimitRepository authRequestRateLimitRepository;
  private final AuthRateLimitProperties authRateLimitProperties;
  private final List<Network> trustedProxies;

  public AuthRequestRateLimitService(
      AuthRequestRateLimitRepository authRequestRateLimitRepository,
      AuthRateLimitProperties authRateLimitProperties) {
    this.authRequestRateLimitRepository = authRequestRateLimitRepository;
    this.authRateLimitProperties = authRateLimitProperties;
    this.trustedProxies =
        authRateLimitProperties.trustedProxies().stream().map(Network::parse).toList();
  }

  public void login(String email, HttpServletRequest request) {
    check(
        "login",
        email,
        request,
        authRateLimitProperties.loginIpLimit(),
        authRateLimitProperties.loginAccountLimit(),
        authRateLimitProperties.loginWindow());
  }

  public void signup(String email, HttpServletRequest request) {
    check(
        "signup",
        email,
        request,
        authRateLimitProperties.signupIpLimit(),
        authRateLimitProperties.signupAccountLimit(),
        authRateLimitProperties.signupWindow());
  }

  private void check(
      String action,
      String email,
      HttpServletRequest request,
      int addressLimit,
      int accountLimit,
      Duration window) {
    if (!authRateLimitProperties.enabled()) return;
    String account = email.strip().toLowerCase(Locale.ROOT);
    authRequestRateLimitRepository.cleanExpired();
    long retryAfter =
        authRequestRateLimitRepository.consume(
            action,
            SecureTokens.hash(clientAddress(request)),
            SecureTokens.hash(account),
            addressLimit,
            accountLimit,
            window.toSeconds());
    if (retryAfter > 0)
      throw new TooManyRequestsException(ErrorCode.RATE_LIMIT_EXCEEDED, retryAfter);
  }

  String clientAddress(HttpServletRequest request) {
    byte[] peer = numericAddress(request.getRemoteAddr());
    if (peer == null) return "unknown-peer";
    if (trustedProxies.stream().anyMatch(network -> network.contains(peer))) {
      List<String> values = Collections.list(request.getHeaders(CLIENT_IP_HEADER));
      if (values.size() == 1) {
        byte[] forwarded = numericAddress(values.getFirst().strip());
        if (forwarded != null) return canonicalAddress(forwarded);
      }
    }
    return canonicalAddress(peer);
  }

  private static byte[] numericAddress(String value) {
    if (value == null || value.length() > 45) return null;
    if (!(value.matches("[0-9.]+") || value.contains(":") && value.matches("[0-9a-fA-F:.]+")))
      return null;
    if (!value.contains(":")) {
      String[] components = value.split("\\.", -1);
      if (components.length != 4) return null;
      for (String component : components) {
        if (component.isEmpty()
            || component.length() > 3
            || component.length() > 1 && component.startsWith("0")
            || Integer.parseInt(component) > 255) return null;
      }
    }
    try {
      return InetAddress.getByName(value).getAddress();
    } catch (UnknownHostException exception) {
      return null;
    }
  }

  private static String canonicalAddress(byte[] value) {
    try {
      return InetAddress.getByAddress(value).getHostAddress();
    } catch (UnknownHostException exception) {
      throw new IllegalArgumentException("Invalid numeric IP address", exception);
    }
  }

  private record Network(byte[] address, int prefix) {
    static Network parse(String value) {
      String[] components = value.split("/", -1);
      byte[] address = numericAddress(components[0]);
      if (address == null || components.length > 2)
        throw new IllegalArgumentException(
            "Trusted authentication proxy must be a numeric IP/CIDR");
      int prefix = address.length * 8;
      if (components.length == 2) {
        try {
          prefix = Integer.parseInt(components[1]);
        } catch (NumberFormatException exception) {
          throw new IllegalArgumentException(
              "Trusted proxy CIDR prefix must be numeric", exception);
        }
      }
      if (prefix < 1 || prefix > address.length * 8)
        throw new IllegalArgumentException(
            "Trusted proxy CIDR prefix is outside the address range");
      return new Network(address, prefix);
    }

    boolean contains(byte[] candidate) {
      if (candidate.length != address.length) return false;
      for (int index = 0; index < prefix; index++) {
        int mask = 1 << (7 - index % 8);
        if ((candidate[index / 8] & mask) != (address[index / 8] & mask)) return false;
      }
      return true;
    }
  }
}
