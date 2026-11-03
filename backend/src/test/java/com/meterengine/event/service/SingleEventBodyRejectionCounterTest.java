package com.meterengine.event.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.security.IngestAccess;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerMapping;

class SingleEventBodyRejectionCounterTest {

  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final SingleEventBodyRejectionCounter singleEventBodyRejectionCounter =
      new SingleEventBodyRejectionCounter(new EventIngestMetrics(meterRegistry));

  @AfterEach
  void clearAuthentication() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void 단건_수집의_본문_거절은_rejected로_센다() {
    authenticateWith("SCOPE_ingest");

    singleEventBodyRejectionCounter.onRejected(
        request("POST", "/v1/events"), ErrorCode.VALIDATION_ERROR);

    assertThat(rejectedCount("validation_error")).isEqualTo(1.0);
  }

  @Test
  void 배치_수집의_봉투_거절은_세지_않는다() {
    authenticateWith("SCOPE_ingest");

    singleEventBodyRejectionCounter.onRejected(
        request("POST", "/v1/event-batches"), ErrorCode.VALIDATION_ERROR);

    assertThat(rejectedCount("validation_error")).isZero();
  }

  @Test
  void 수집_권한이_없는_요청의_본문_거절은_세지_않는다() {
    authenticateWith("SCOPE_read");

    singleEventBodyRejectionCounter.onRejected(
        request("POST", "/v1/events"), ErrorCode.VALIDATION_ERROR);

    assertThat(rejectedCount("validation_error")).isZero();
  }

  @Test
  void 수집_권한은_IngestAccess가_검사하는_권한과_같다() {
    PreAuthorize ingestAccess = IngestAccess.class.getAnnotation(PreAuthorize.class);

    assertThat(ingestAccess.value())
        .isEqualTo(
            "hasAuthority('%s')".formatted(SingleEventBodyRejectionCounter.INGEST_AUTHORITY));
  }

  private static void authenticateWith(String authority) {
    SecurityContextHolder.getContext()
        .setAuthentication(new TestingAuthenticationToken("api-key", null, authority));
  }

  private static MockHttpServletRequest request(String method, String uriPattern) {
    MockHttpServletRequest request = new MockHttpServletRequest(method, uriPattern);
    request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, uriPattern);
    return request;
  }

  private double rejectedCount(String reason) {
    return meterRegistry
        .get(EventIngestMetrics.INGEST_COUNTER_NAME)
        .tag(EventIngestMetrics.RESULT_TAG, "rejected")
        .tag(EventIngestMetrics.REASON_TAG, reason)
        .counters()
        .stream()
        .mapToDouble(Counter::count)
        .sum();
  }
}
