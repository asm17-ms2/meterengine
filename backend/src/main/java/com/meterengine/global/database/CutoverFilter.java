package com.meterengine.global.database;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public final class CutoverFilter extends OncePerRequestFilter {
  private static final Set<String> HEALTH =
      Set.of("/actuator/health", "/actuator/health/readiness");
  private final CutoverPolicy policy;

  public CutoverFilter(CutoverPolicy policy) {
    this.policy = policy;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (policy.readOnly()
        && !("GET".equals(request.getMethod()) && HEALTH.contains(request.getRequestURI()))) {
      response.setStatus(503);
      response.setContentType("application/json");
      response.getWriter().write("{\"code\":\"read_only\"}");
      return;
    }
    chain.doFilter(request, response);
  }

  @Override
  protected boolean shouldNotFilterErrorDispatch() {
    return false;
  }

  @Override
  protected boolean shouldNotFilterAsyncDispatch() {
    return false;
  }
}
