package com.meterengine.global.security;

import com.meterengine.global.error.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.web.filter.OncePerRequestFilter;

public class ApiCredentialAuthenticationFilter extends OncePerRequestFilter {

  public static final String PRINCIPAL_ATTRIBUTE = AuthenticatedPrincipal.class.getName();

  public static final String RATE_LIMIT_PER_MINUTE_ATTRIBUTE =
      "com.meterengine.global.security.RateLimitPerMinute";

  private static final String BEARER_PREFIX = "Bearer ";

  private final ApiKeyCredentials apiKeyCredentials;

  private final SessionCredentials sessionCredentials;

  private final ErrorResponseWriter errorResponseWriter;

  private final SecurityContextHolderStrategy securityContextHolderStrategy =
      SecurityContextHolder.getContextHolderStrategy();

  public ApiCredentialAuthenticationFilter(
      ApiKeyCredentials apiKeyCredentials,
      SessionCredentials sessionCredentials,
      ErrorResponseWriter errorResponseWriter) {
    this.apiKeyCredentials = apiKeyCredentials;
    this.sessionCredentials = sessionCredentials;
    this.errorResponseWriter = errorResponseWriter;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (authorization == null) {
      filterChain.doFilter(request, response);
      return;
    }
    Optional<AuthenticatedCredential> credential;
    try {
      credential = authenticate(authorization);
    } catch (RuntimeException exception) {
      logger.error("Credential lookup failed", exception);
      errorResponseWriter.write(response, ErrorCode.INTERNAL_SERVER_ERROR);
      return;
    }
    if (credential.isEmpty()) {
      errorResponseWriter.write(response, ErrorCode.INVALID_CREDENTIALS);
      return;
    }
    AuthenticatedPrincipal principal = credential.get().principal();
    request.setAttribute(PRINCIPAL_ATTRIBUTE, principal);
    if (principal.kind() == PrincipalKind.SESSION
        && sessionCredentials.requiresConsent(principal.userAccountId())
        && !consentPath(request)) {
      errorResponseWriter.write(response, ErrorCode.CONSENT_REQUIRED);
      return;
    }
    SecurityContext context = securityContextHolderStrategy.createEmptyContext();
    context.setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated(
            principal, null, principal.authorities()));
    securityContextHolderStrategy.setContext(context);
    request.setAttribute(PRINCIPAL_ATTRIBUTE, principal);
    request.setAttribute(RATE_LIMIT_PER_MINUTE_ATTRIBUTE, credential.get().rateLimitPerMinute());
    filterChain.doFilter(request, response);
  }

  private static boolean consentPath(HttpServletRequest request) {
    String path = request.getRequestURI();
    return ("/v1/sessions/current".equals(path)
            && ("GET".equals(request.getMethod()) || "DELETE".equals(request.getMethod())))
        || ("/v1/sessions/current/consents".equals(path) && "POST".equals(request.getMethod()));
  }

  private Optional<AuthenticatedCredential> authenticate(String authorization) {
    if (!authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
      return Optional.empty();
    }
    String credential = authorization.substring(BEARER_PREFIX.length());
    if (credential.startsWith(SecureTokens.SESSION_TOKEN_PREFIX)) {
      return sessionCredentials.authenticate(credential);
    }
    return apiKeyCredentials.authenticate(credential);
  }
}
