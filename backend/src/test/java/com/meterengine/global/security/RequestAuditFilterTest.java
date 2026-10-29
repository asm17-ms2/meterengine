package com.meterengine.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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
import org.springframework.security.web.firewall.RequestRejectedException;
import org.springframework.web.servlet.HandlerMapping;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(OutputCaptureExtension.class)
class RequestAuditFilterTest {
  @Test
  void 이미_처리된_요청의_감사저장이_실패해도_응답은_그대로_두고_요청식별자와_오류를_남긴다(CapturedOutput output) throws Exception {
    RequestAuditRecorder recorder = mock(RequestAuditRecorder.class);
    doThrow(new IllegalStateException("audit database unavailable"))
        .when(recorder)
        .record(isNull(), eq("POST"), eq("/v1/customers"), eq(201), anyLong(), any(), any());
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    RequestAuditFilter filter = new RequestAuditFilter(recorder, registry);
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/customers");
    request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/v1/customers");
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicBoolean processed = new AtomicBoolean();
    filter.doFilter(
        request,
        response,
        (incoming, outgoing) -> {
          processed.set(true);
          response.setStatus(201);
        });
    assertThat(processed).isTrue();
    assertThat(response.getStatus()).isEqualTo(201);
    String requestId = response.getHeader("X-Request-ID");
    assertThat(requestId).isNotBlank();
    assertThat(output.getOut() + output.getErr())
        .contains("Request audit failed request_id=" + requestId);
    assertThat(registry.get("meterengine.request.audit.failures").counter().count()).isEqualTo(1.0);
  }

  @Test
  void 처리중_예외가_난_요청은_감사저장이_실패해도_원래_예외를_던진다() {
    RequestAuditRecorder recorder = mock(RequestAuditRecorder.class);
    doThrow(new IllegalStateException("audit database unavailable"))
        .when(recorder)
        .record(isNull(), eq("POST"), eq("/v1/customers"), eq(500), anyLong(), any(), any());
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    RequestAuditFilter filter = new RequestAuditFilter(recorder, registry);
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/customers");
    request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/v1/customers");
    IllegalArgumentException failure = new IllegalArgumentException("handler failed");
    assertThatThrownBy(
            () ->
                filter.doFilter(
                    request,
                    new MockHttpServletResponse(),
                    (incoming, outgoing) -> {
                      throw failure;
                    }))
        .isSameAs(failure);
    assertThat(registry.get("meterengine.request.audit.failures").counter().count()).isEqualTo(1.0);
  }

  @Test
  void 단건_수집_요청은_감사기록을_남기지_않고_요청식별자만_돌려준다() throws Exception {
    assertIngestionSkipsAudit("/v1/events");
  }

  @Test
  void 배치_수집_요청은_감사기록을_남기지_않고_요청식별자만_돌려준다() throws Exception {
    assertIngestionSkipsAudit("/v1/event-batches");
  }

  @Test
  void 퍼센트_인코딩한_v1_경로도_요청식별자와_감사기록을_남긴다() throws Exception {
    RequestAuditRecorder recorder = mock(RequestAuditRecorder.class);
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/%761/customers");
    MockHttpServletResponse response = new MockHttpServletResponse();

    new RequestAuditFilter(recorder, new SimpleMeterRegistry())
        .doFilter(request, response, (incoming, outgoing) -> response.setStatus(200));

    assertThat(response.getHeader("X-Request-ID")).isNotBlank();
    verify(recorder).record(isNull(), eq("GET"), anyString(), eq(200), anyLong(), any(), any());
  }

  @Test
  void 퍼센트_인코딩한_수집_경로는_수집으로_보지_않아_감사기록을_남긴다() throws Exception {
    RequestAuditRecorder recorder = mock(RequestAuditRecorder.class);
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/%761/events");
    MockHttpServletResponse response = new MockHttpServletResponse();

    new RequestAuditFilter(recorder, new SimpleMeterRegistry())
        .doFilter(request, response, (incoming, outgoing) -> response.setStatus(200));

    verify(recorder).record(isNull(), eq("POST"), anyString(), eq(200), anyLong(), any(), any());
  }

  @Test
  void 방화벽이_거절한_퍼센트_인코딩_v1_경로도_익명_400으로_감사한다() throws Exception {
    RequestAuditRecorder recorder = mock(RequestAuditRecorder.class);
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/%761/customers%3Bx");

    new JsonRequestRejectedHandler(new ErrorResponseWriter(JsonMapper.builder().build()), recorder)
        .handle(request, new MockHttpServletResponse(), new RequestRejectedException("rejected"));

    verify(recorder)
        .record(isNull(), eq("GET"), eq("/v1/{unmatched}"), eq(400), anyLong(), any(), any());
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
    new RequestAuditFilter(recorder, new SimpleMeterRegistry())
        .doFilter(request, response, (incoming, outgoing) -> response.setStatus(status));
    ArgumentCaptor<RequestAuditTarget> target = ArgumentCaptor.forClass(RequestAuditTarget.class);
    verify(recorder)
        .record(isNull(), eq("PUT"), eq(route), eq(status), anyLong(), any(), target.capture());
    return target.getValue();
  }

  private static void assertIngestionSkipsAudit(String path) throws Exception {
    RequestAuditRecorder recorder = mock(RequestAuditRecorder.class);
    MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
    request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, path);
    MockHttpServletResponse response = new MockHttpServletResponse();
    new RequestAuditFilter(recorder, new SimpleMeterRegistry())
        .doFilter(request, response, (incoming, outgoing) -> response.setStatus(200));
    verifyNoInteractions(recorder);
    assertThat(response.getHeader("X-Request-ID")).isNotBlank();
  }
}
