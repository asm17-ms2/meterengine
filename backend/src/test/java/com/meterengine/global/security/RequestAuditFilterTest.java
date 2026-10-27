package com.meterengine.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

@ExtendWith(OutputCaptureExtension.class)
class RequestAuditFilterTest {
  @Test
  void 이미_처리된_요청의_감사저장이_실패해도_예외와_요청식별자를_숨기지_않는다(CapturedOutput output) {
    RequestAuditRecorder recorder = mock(RequestAuditRecorder.class);
    IllegalStateException failure = new IllegalStateException("audit database unavailable");
    doThrow(failure)
        .when(recorder)
        .record(isNull(), eq("POST"), eq("/v1/customers"), eq(201), anyLong(), any(), any());
    RequestAuditFilter filter = new RequestAuditFilter(recorder);
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/customers");
    request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/v1/customers");
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicBoolean processed = new AtomicBoolean();
    assertThatThrownBy(
            () ->
                filter.doFilter(
                    request,
                    response,
                    (incoming, outgoing) -> {
                      processed.set(true);
                      response.setStatus(201);
                      response.flushBuffer();
                    }))
        .isSameAs(failure);
    assertThat(processed).isTrue();
    assertThat(response.isCommitted()).isTrue();
    String requestId = response.getHeader("X-Request-ID");
    assertThat(requestId).isNotBlank();
    assertThat(output.getOut() + output.getErr())
        .contains("Request audit failed request_id=" + requestId);
  }

  @Test
  void UUID_대상만_명시허용하고_토큰과_코드와_본문은_보존하지_않는다() throws Exception {
    String endpointId = UUID.randomUUID().toString();
    String deliveryId = UUID.randomUUID().toString();
    String excluded = UUID.randomUUID().toString();
    var target =
        capture(
            "/v1/webhook-endpoints/{id}/deliveries/{delivery_id}/retries",
            Map.of(
                "id",
                endpointId,
                "delivery_id",
                deliveryId,
                "token",
                excluded,
                "code",
                excluded,
                "authKey",
                excluded,
                "mode",
                "test"),
            404);
    assertThat(target.id()).isEqualTo(deliveryId);
    assertThat(target.idByVariable())
        .containsExactlyInAnyOrderEntriesOf(Map.of("id", endpointId, "delivery_id", deliveryId));
    assertThat(target.mode()).isEqualTo("test");
    assertThat(target.toString())
        .doesNotContain(excluded, "private-query", "private-body", "private-header");
  }

  @Test
  void UUID형_토큰과_코드도_이름이_허용되지_않으면_제외한다() throws Exception {
    String excluded = UUID.randomUUID().toString();
    var target =
        capture(
            "/v1/organization-invitations/{token}",
            Map.of("token", excluded, "code", excluded),
            400);
    assertThat(target).isEqualTo(RequestAuditTarget.empty());
  }

  @Test
  void 축약_UUID와_임의_모드는_제외하고_조직_UUID와_live는_허용한다() throws Exception {
    var invalid =
        capture("/v1/customers/{id}", Map.of("id", "1-1-1-1-1", "mode", "secret-mode"), 400);
    assertThat(invalid).isEqualTo(RequestAuditTarget.empty());
    String id = UUID.randomUUID().toString();
    var valid =
        capture(
            "/v1/toss-payments-webhooks/{organization_id}",
            Map.of("organization_id", id, "mode", "live"),
            200);
    assertThat(valid.id()).isEqualTo(id);
    assertThat(valid.idByVariable()).containsExactlyEntriesOf(Map.of("organization_id", id));
    assertThat(valid.mode()).isEqualTo("live");
  }

  private RequestAuditTarget capture(String route, Map<String, String> variables, int status)
      throws Exception {
    RequestAuditRecorder recorder = mock(RequestAuditRecorder.class);
    var request = new MockHttpServletRequest("PUT", "/v1/private-raw-path");
    request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, route);
    request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, variables);
    request.setQueryString("password=private-query");
    request.addHeader("X-Private", "private-header");
    request.setContent("private-body".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    var response = new MockHttpServletResponse();
    new RequestAuditFilter(recorder)
        .doFilter(request, response, (incoming, outgoing) -> response.setStatus(status));
    ArgumentCaptor<RequestAuditTarget> target = ArgumentCaptor.forClass(RequestAuditTarget.class);
    verify(recorder)
        .record(isNull(), eq("PUT"), eq(route), eq(status), anyLong(), any(), target.capture());
    return target.getValue();
  }
}
