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
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

public class RequestAuditFilter extends OncePerRequestFilter {
  public static final String REQUEST_ID_ATTRIBUTE =
      RequestAuditFilter.class.getName() + ".requestId";
  private static final List<String> UUID_VARIABLES =
      List.of("delivery_id", "id", "organization_id");
  private static final Pattern UUID_VALUE =
      Pattern.compile(
          "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
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
    boolean failed = true;
    UUID requestId = UUID.randomUUID();
    response.setHeader("X-Request-ID", requestId.toString());
    request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
    try {
      filterChain.doFilter(request, response);
      failed = false;
    } finally {
      Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
      String route = pattern == null ? "/v1/{unmatched}" : pattern.toString();
      if (!route.startsWith("/v1/")) route = "/v1/{unmatched}";
      Object actor = request.getAttribute(ApiCredentialAuthenticationFilter.PRINCIPAL_ATTRIBUTE);
      try {
        requestAuditRecorder.record(
            actor instanceof AuthenticatedPrincipal principal ? principal : null,
            request.getMethod(),
            route,
            failed ? 500 : response.getStatus(),
            TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started),
            requestId,
            route.equals("/v1/{unmatched}") ? RequestAuditTarget.empty() : target(request));
      } catch (RuntimeException exception) {
        logger.error("Request audit failed request_id=" + requestId, exception);
        throw exception;
      }
    }
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
