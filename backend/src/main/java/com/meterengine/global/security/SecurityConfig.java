package com.meterengine.global.security;

import jakarta.servlet.DispatcherType;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.firewall.RequestRejectedHandler;
import org.springframework.security.web.session.DisableEncodeUrlFilter;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class SecurityConfig {

  @Bean
  ErrorResponseWriter errorResponseWriter(JsonMapper jsonMapper) {
    return new ErrorResponseWriter(jsonMapper);
  }

  @Bean
  RequestRejectedHandler requestRejectedHandler(
      ErrorResponseWriter errorResponseWriter, RequestAuditRecorder requestAuditRecorder) {
    return new JsonRequestRejectedHandler(errorResponseWriter, requestAuditRecorder);
  }

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      ApiKeyCredentials apiKeyCredentials,
      SessionCredentials sessionCredentials,
      ErrorResponseWriter errorResponseWriter,
      RequestAuditRecorder requestAuditRecorder,
      Clock clock)
      throws Exception {
    return http.csrf(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .requestCache(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(
            exceptions ->
                exceptions
                    .authenticationEntryPoint(new JsonAuthenticationEntryPoint(errorResponseWriter))
                    .accessDeniedHandler(new JsonAccessDeniedHandler(errorResponseWriter)))
        .authorizeHttpRequests(
            requests ->
                requests
                    .dispatcherTypeMatchers(DispatcherType.ERROR)
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/v1/sessions", "/v1/signups")
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.POST,
                        "/v1/public/invoice-links/lookup",
                        "/v1/public/invoice-links/payment-retries")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/v1/organization-invitations/*")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/v1/organization-invitations/*/accept")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/v1/payment-method-registrations/*")
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.POST,
                        "/v1/payment-method-registrations/*/billing-keys",
                        "/v1/toss-payments-webhooks/*")
                    .permitAll()
                    .requestMatchers(
                        "/actuator/health",
                        "/actuator/health/**",
                        "/actuator/prometheus",
                        "/v3/api-docs",
                        "/v3/api-docs/**",
                        "/v3/api-docs.yaml",
                        "/scalar",
                        "/scalar/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(new RequestLogFilter(), DisableEncodeUrlFilter.class)
        .addFilterBefore(new RequestAuditFilter(requestAuditRecorder), RequestLogFilter.class)
        .addFilterBefore(
            new ApiCredentialAuthenticationFilter(
                apiKeyCredentials, sessionCredentials, errorResponseWriter),
            AnonymousAuthenticationFilter.class)
        .addFilterAfter(
            new RateLimitFilter(clock, errorResponseWriter),
            ApiCredentialAuthenticationFilter.class)
        .build();
  }
}
