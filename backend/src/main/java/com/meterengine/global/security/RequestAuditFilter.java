package com.meterengine.global.security;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
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
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.UrlPathHelper;

public class RequestAuditFilter extends OncePerRequestFilter {
  public static final String REQUEST_ID_ATTRIBUTE =
      RequestAuditFilter.class.getName() + ".requestId";
  static final String AUDITED_ATTRIBUTE = RequestAuditFilter.class.getName() + ".audited";
  static final List<String> UUID_VARIABLES = List.of("delivery_id", "id", "organization_id");
  private static final Pattern UUID_VALUE =
      Pattern.compile(
          "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
  private static final String INGEST_METHOD = "POST";
  private static final Set<String> INGEST_PATHS = Set.of("/v1/events", "/v1/event-batches");
  private final RequestAuditRecorder requestAuditRecorder;
  private final Counter failures;

  public RequestAuditFilter(RequestAuditRecorder requestAuditRecorder, MeterRegistry registry) {
    this.requestAuditRecorder = requestAuditRecorder;
    this.failures =
        Counter.builder("meterengine.request.audit.failures")
            .description("요청 감사 저장 실패 건수")
            .register(registry);
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !isApiRequest(request);
  }

  // 퍼센트 인코딩한 경로도 MVC와 보안 설정처럼 디코딩한 경로로 /v1 요청인지 본다.
  static boolean isApiRequest(HttpServletRequest request) {
    return UrlPathHelper.defaultInstance.getPathWithinApplication(request).startsWith("/v1/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    long started = System.nanoTime();
    int status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
    UUID requestId = UUID.randomUUID();
    response.setHeader("X-Request-ID", requestId.toString());
    request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
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
            actor instanceof AuthenticatedPrincipal principal
                ? attributedActor(request, principal)
                : null,
            request.getMethod(),
            route,
            status,
            TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started),
            requestId,
            route.equals("/v1/{unmatched}") ? RequestAuditTarget.empty() : target(request));
      } catch (RuntimeException exception) {
        failures.increment();
        logger.error("Request audit failed request_id=" + requestId, exception);
      }
    }
  }

  // 운영자 전용 경로는 플랫폼 전체를 다루므로 운영자 세션이 고른 도입사에 요청 감사를 남기지 않는다.
  private static AuthenticatedPrincipal attributedActor(
      HttpServletRequest request, AuthenticatedPrincipal principal) {
    if (!(request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE)
            instanceof HandlerMethod handler)
        || !handler.hasMethodAnnotation(OperatorAccess.class)) return principal;
    return new AuthenticatedPrincipal(
        principal.kind(),
        null,
        principal.apiKeyId(),
        principal.userAccountId(),
        principal.userSessionId(),
        principal.mode(),
        principal.role(),
        principal.scopes(),
        principal.environmentId());
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
