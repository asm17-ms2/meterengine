package com.meterengine.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.meterengine.account.service.SessionService;
import com.meterengine.apikey.service.ApiKeyService;
import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class ApiCredentialAuthenticationFilterTest {

  private static final String API_KEY = "me_live_" + "K".repeat(32);

  private static final String SESSION_TOKEN = "me_session_" + "S".repeat(32);

  @Mock private ApiKeyService apiKeyService;
  @Mock private SessionService sessionService;

  private ApiCredentialAuthenticationFilter filter;

  @BeforeEach
  void setUp() {
    filter =
        new ApiCredentialAuthenticationFilter(
            apiKeyService, sessionService, new ErrorResponseWriter(JsonMapper.builder().build()));
  }

  @Test
  void 키_조회가_실패하면_500_internal_server_error를_쓰고_체인을_멈추며_원문은_남기지_않는다(CapturedOutput output)
      throws ServletException, IOException {
    when(apiKeyService.authenticate(API_KEY))
        .thenThrow(new DataAccessResourceFailureException("connection refused"));

    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();
    filter.doFilter(requestWith(API_KEY), response, chain);

    assertThat(response.getStatus()).isEqualTo(500);
    assertThat(response.getContentType()).isEqualTo("application/json");
    assertThat(response.getContentAsString()).contains("\"code\":\"internal_server_error\"");
    assertThat(chain.getRequest()).isNull();
    assertThat(output.getOut() + output.getErr())
        .contains("Credential lookup failed", "connection refused")
        .doesNotContain(API_KEY);
  }

  @Test
  void 세션_조회가_실패해도_500_internal_server_error를_쓰고_체인을_멈춘다(CapturedOutput output)
      throws ServletException, IOException {
    when(sessionService.authenticate(SESSION_TOKEN))
        .thenThrow(new DataAccessResourceFailureException("connection refused"));

    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();
    filter.doFilter(requestWith(SESSION_TOKEN), response, chain);

    assertThat(response.getStatus()).isEqualTo(500);
    assertThat(response.getContentAsString()).contains("\"code\":\"internal_server_error\"");
    assertThat(chain.getRequest()).isNull();
    assertThat(output.getOut() + output.getErr()).doesNotContain(SESSION_TOKEN);
  }

  private static MockHttpServletRequest requestWith(String credential) {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/customers");
    request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + credential);
    return request;
  }
}
