package com.meterengine.event.service;

import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.RequestBodyRejectionListener;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerMapping;

// 배치는 원소의 형식 오류를 거절로 세므로, 단건도 본문 형식 오류를 거절로 세어 뜻을 맞춘다.
@Component
class SingleEventBodyRejectionCounter implements RequestBodyRejectionListener {

  private static final String SINGLE_EVENT_URI = "/v1/events";
  // @IngestAccess 검사는 본문 검사 뒤에 돌므로, 수집 권한이 없어 어차피 403일 요청은 여기서 뺀다.
  static final String INGEST_AUTHORITY = "SCOPE_ingest";

  private final EventIngestMetrics eventIngestMetrics;

  SingleEventBodyRejectionCounter(EventIngestMetrics eventIngestMetrics) {
    this.eventIngestMetrics = eventIngestMetrics;
  }

  @Override
  public void onRejected(HttpServletRequest request, ErrorCode errorCode) {
    if (isSingleEventIngest(request) && hasIngestAuthority()) {
      eventIngestMetrics.recordRejected(errorCode);
    }
  }

  private static boolean isSingleEventIngest(HttpServletRequest request) {
    return HttpMethod.POST.matches(request.getMethod())
        && SINGLE_EVENT_URI.equals(
            request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE));
  }

  private static boolean hasIngestAuthority() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    return authentication != null
        && authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch(INGEST_AUTHORITY::equals);
  }
}
