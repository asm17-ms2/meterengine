package com.meterengine.event.controller;

import com.meterengine.event.dto.EventIssuesResponse;
import com.meterengine.event.service.EventIssueService;
import com.meterengine.global.security.CurrentOrganizationId;
import com.meterengine.global.security.EnvironmentReady;
import com.meterengine.global.security.ReadAccess;
import com.meterengine.global.validation.FourDigitYear;
import com.meterengine.global.validation.StorableText;
import com.meterengine.global.validation.UnknownQueryParameterValidator;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.YearMonth;
import java.util.Set;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@EnvironmentReady
public class EventIssueController {
  private final EventIssueService eventIssueService;

  public EventIssueController(EventIssueService eventIssueService) {
    this.eventIssueService = eventIssueService;
  }

  @GetMapping("/v1/event-issues/search")
  @ReadAccess
  @Operation(
      summary = "수집된 이벤트의 매핑 문제 조회",
      description =
          "현재 월 정의와 단가로 cutoff 이전 원본을 진단한다. total과 summary는 고객/검색/사유 필터 전체의 문제 행 수이며 한 이벤트에 여러 미터"
              + " 문제가 있을 수 있다. warnings는 조직/월 전체다. 고객을 찾지 못해 수집이 거절된 이벤트는 저장되지 않아 포함하지 않는다. 원본"
              + " properties는 반환하지 않는다. 페이지는 0부터 시작한다.")
  public EventIssuesResponse searchEventIssues(
      @CurrentOrganizationId UUID organizationId,
      @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") @FourDigitYear
          YearMonth month,
      @RequestParam(name = "customer_id", required = false) UUID customerId,
      @RequestParam(required = false)
          @Pattern(regexp = "unknown_event_type|invalid_target_property|unpriced_usage") @StorableText
          String reason,
      @RequestParam(required = false) @Size(max = 255) @StorableText String q,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
      HttpServletRequest request) {
    UnknownQueryParameterValidator.validate(
        request.getParameterMap(), Set.of("month", "customer_id", "reason", "q", "page", "size"));
    return eventIssueService.search(organizationId, month, customerId, reason, q, page, size);
  }
}
