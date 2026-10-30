package com.meterengine.global.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.web.firewall.RequestRejectedException;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UriUtils;

public class RequestLogFilter extends OncePerRequestFilter {

  private static final Logger requestLogger = LoggerFactory.getLogger(RequestLogFilter.class);

  private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";

  private static final String ANONYMOUS_PRINCIPAL_KIND = "anonymous";

  private static final String UNKNOWN_CLIENT_ADDRESS = "-";

  private static final String UNDECODABLE_PATH = "{undecodable}";

  private static final String LOGGED_ATTRIBUTE = RequestLogFilter.class.getName() + ".logged";

  private static final int MAX_DECODE_ROUNDS = 4;

  // deploy/caddy/Caddyfile의 redacted_path와 같은 경로 목록이다. 바꾸면 같이 바꾼다.
  private static final Pattern PAYMENT_METHOD_REGISTRATION_TOKEN =
      Pattern.compile(
          "(/(?:payment-method-registrations|organization-invitations)).+",
          Pattern.CASE_INSENSITIVE);

  private static final Pattern UNSAFE_ADDRESS_CHARACTERS =
      Pattern.compile("[^A-Za-z0-9.:%\\[\\]-]");

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    long startedAt = System.nanoTime();
    String clientAddress = clientAddress(request);
    int status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
    try {
      filterChain.doFilter(request, response);
      status = response.getStatus();
    } catch (RequestRejectedException exception) {
      status = HttpServletResponse.SC_BAD_REQUEST;
      throw exception;
    } finally {
      long durationMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
      request.setAttribute(LOGGED_ATTRIBUTE, Boolean.TRUE);
      requestLogger.info(line(request, status, durationMillis, clientAddress));
    }
  }

  // 방화벽이 체인 앞에서 거절해 이 필터를 거치지 않은 요청을 같은 꼴의 한 줄로 남긴다.
  static void logRejected(HttpServletRequest request) {
    if (request.getAttribute(LOGGED_ATTRIBUTE) != null) {
      return;
    }
    request.setAttribute(LOGGED_ATTRIBUTE, Boolean.TRUE);
    requestLogger.info(
        line(request, HttpServletResponse.SC_BAD_REQUEST, 0, clientAddress(request)));
  }

  private static String line(
      HttpServletRequest request, int status, long durationMillis, String clientAddress) {
    StringBuilder line =
        new StringBuilder()
            .append("method=")
            .append(request.getMethod())
            .append(" path=")
            .append(maskedPath(request.getRequestURI()))
            .append(" status=")
            .append(status)
            .append(" duration_ms=")
            .append(durationMillis);
    if (request.getAttribute(ApiCredentialAuthenticationFilter.PRINCIPAL_ATTRIBUTE)
        instanceof AuthenticatedPrincipal principal) {
      appendPrincipal(line, principal);
    } else {
      line.append(" principal_kind=").append(ANONYMOUS_PRINCIPAL_KIND);
    }
    return line.append(" client_address=").append(clientAddress).toString();
  }

  private static void appendPrincipal(StringBuilder line, AuthenticatedPrincipal principal) {
    line.append(" principal_kind=").append(principal.kind().name().toLowerCase(Locale.ROOT));
    if (principal.organizationId() != null) {
      line.append(" organization_id=").append(principal.organizationId());
    }
    switch (principal.kind()) {
      case API_KEY -> line.append(" api_key_id=").append(principal.apiKeyId());
      case SESSION -> line.append(" user_account_id=").append(principal.userAccountId());
    }
  }

  // 겹치거나 바꿔 쓴 인코딩에도 토큰이 남지 않게 다 디코딩해 판단하고 등록 경로 뒤는 통째로 가린다.
  private static String maskedPath(String requestUri) {
    String decodedPath = requestUri;
    for (int round = 0; round < MAX_DECODE_ROUNDS; round++) {
      String once;
      try {
        once = UriUtils.decode(decodedPath, StandardCharsets.UTF_8);
      } catch (IllegalArgumentException exception) {
        return UNDECODABLE_PATH;
      }
      if (once.equals(decodedPath)) {
        String encodedPath = UriUtils.encodePath(decodedPath, StandardCharsets.UTF_8);
        return PAYMENT_METHOD_REGISTRATION_TOKEN.matcher(encodedPath).replaceFirst("$1/{token}");
      }
      decodedPath = once;
    }
    return UNDECODABLE_PATH;
  }

  private static String clientAddress(HttpServletRequest request) {
    String forwardedFor;
    try {
      forwardedFor = request.getHeader(FORWARDED_FOR_HEADER);
    } catch (RequestRejectedException exception) {
      return UNKNOWN_CLIENT_ADDRESS;
    }
    String address = request.getRemoteAddr();
    if (forwardedFor != null) {
      String firstForwardedFor = forwardedFor.split(",", 2)[0].strip();
      if (!firstForwardedFor.isEmpty()) {
        address = firstForwardedFor;
      }
    }
    return address == null
        ? UNKNOWN_CLIENT_ADDRESS
        : UNSAFE_ADDRESS_CHARACTERS.matcher(address).replaceAll("_");
  }
}
