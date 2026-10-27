package com.meterengine.global.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.springframework.security.web.firewall.RequestRejectedException;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

public class RequestAuditFilter extends OncePerRequestFilter {
  static final String AUDITED_ATTRIBUTE = RequestAuditFilter.class.getName() + ".audited";
  private static final List<String> UUID_VARIABLES =
      List.of("delivery_id", "id", "organization_id");
  private static final Pattern UUID_VALUE =
      Pattern.compile(
          "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
  private static final String INGEST_METHOD = "POST";
  private static final Set<String> INGEST_PATHS = Set.of("/v1/events", "/v1/event-batches");
  private final RequestAuditRecorder requestAuditRecorder;

  public RequestAuditFilter(RequestAuditRecorder requestAuditRecorder) {
    this.requestAuditRecorder = requestAuditRecorder;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith("/v1/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    long started = System.nanoTime();
    int status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
    UUID requestId = UUID.randomUUID();
    response.setHeader("X-Request-ID", requestId.toString());
    if (isIngestion(request)) {
      filterChain.doFilter(request, response);
      return;
    }
    try {
      filterChain.doFilter(request, response);
      status = response.getStatus();
    } catch (RequestRejectedException exception) {
      status = HttpServletResponse.SC_BAD_REQUEST;
      throw exception;
    } finally {
      request.setAttribute(AUDITED_ATTRIBUTE, Boolean.TRUE);
      Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
      String route = pattern == null ? "/v1/{unmatched}" : pattern.toString();
      if (!route.startsWith("/v1/")) route = "/v1/{unmatched}";
      Object actor = request.getAttribute(ApiCredentialAuthenticationFilter.PRINCIPAL_ATTRIBUTE);
      try {
        requestAuditRecorder.record(
            actor instanceof AuthenticatedPrincipal principal ? principal : null,
            request.getMethod(),
            route,
            status,
            TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started),
            requestId,
            route.equals("/v1/{unmatched}") ? RequestAuditTarget.empty() : target(request));
      } catch (RuntimeException exception) {
        logger.error("Request audit failed request_id=" + requestId, exception);
      }
    }
  }

  // 수집은 요청이 많아 요청마다 감사 행을 동기로 쓰면 지연과 DB 쓰기가 크게 늘어 감사에서 뺀다.
  private static boolean isIngestion(HttpServletRequest request) {
    return INGEST_METHOD.equals(request.getMethod())
        && INGEST_PATHS.contains(request.getRequestURI());
  }

  private static RequestAuditTarget target(HttpServletRequest request) {
    Object attribute = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
    if (!(attribute instanceof Map<?, ?> variables)) return RequestAuditTarget.empty();
    Map<String, String> identifiers = new LinkedHashMap<>();
    for (String name : UUID_VARIABLES) {
      if (variables.get(name) instanceof String value && UUID_VALUE.matcher(value).matches())
        identifiers.put(name, value);
    }
    String id = identifiers.values().stream().findFirst().orElse(null);
    String mode =
        variables.get("mode") instanceof String value && Set.of("test", "live").contains(value)
            ? value
            : null;
    return new RequestAuditTarget(id, identifiers, mode);
  }
}
