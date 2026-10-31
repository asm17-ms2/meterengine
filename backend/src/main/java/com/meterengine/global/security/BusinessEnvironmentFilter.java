package com.meterengine.global.security;

import com.meterengine.global.database.DatabaseScopes;
import com.meterengine.global.error.BusinessException;
import com.meterengine.global.error.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

public class BusinessEnvironmentFilter extends OncePerRequestFilter {
  private final BusinessEnvironmentInterceptor interceptor;
  private final ErrorResponseWriter errorResponseWriter;

  public BusinessEnvironmentFilter(
      OrganizationEnvironments environments, ErrorResponseWriter errorResponseWriter) {
    this.interceptor = new BusinessEnvironmentInterceptor(environments);
    this.errorResponseWriter = errorResponseWriter;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    return !(path.equals("/v1") || path.startsWith("/v1/"));
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    try {
      interceptor.preHandle(request, response, null);
    } catch (BusinessException exception) {
      errorResponseWriter.write(response, exception.getErrorCode());
      return;
    } catch (RuntimeException exception) {
      logger.error("Business environment lookup failed", exception);
      errorResponseWriter.write(response, ErrorCode.INTERNAL_SERVER_ERROR);
      return;
    }
    var environment =
        (BusinessEnvironment) request.getAttribute(BusinessEnvironmentContext.ATTRIBUTE);
    if (environment == null) {
      chain.doFilter(request, response);
      return;
    }
    try {
      BusinessEnvironmentContext.run(
          environment,
          () ->
              DatabaseScopes.call(
                  EnvironmentRoutePolicy.classify(request.getRequestURI())
                      .databaseScope(environment),
                  () -> {
                    try {
                      chain.doFilter(request, response);
                    } catch (IOException | ServletException exception) {
                      throw new FilterChainException(exception);
                    }
                    return null;
                  }));
    } catch (FilterChainException exception) {
      if (exception.getCause() instanceof IOException io) throw io;
      throw (ServletException) exception.getCause();
    }
  }

  private static final class FilterChainException extends RuntimeException {
    FilterChainException(Exception cause) {
      super(cause);
    }
  }
}
