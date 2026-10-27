package com.meterengine.event.controller;

import com.meterengine.event.dto.EventRejectionsResponse;
import com.meterengine.event.service.EventRejectionQueryService;
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
public class EventRejectionController {
  private final EventRejectionQueryService eventRejectionQueryService;

  public EventRejectionController(EventRejectionQueryService eventRejectionQueryService) {
    this.eventRejectionQueryService = eventRejectionQueryService;
  }

  @GetMapping("/v1/event-rejections/search")
  @ReadAccess
  @Operation(
      summary = "수집 업무 거절 메타데이터 조회",
      description =
          "month는 거절을 기록한 달이다. 신규 기록 시작 이후 파싱된 단건 업무 거절과 배치 rejected 행만 포함한다. 최상위 JSON 파싱/입력 검증, 인증/한도 거절과 과거 거절은 포함하지 않는다. 원본 properties, 오류 원문과 행위자 ID는 반환하지 않는다. 페이지는 0부터 시작한다.")
  public EventRejectionsResponse searchEventRejections(
      @CurrentOrganizationId UUID organizationId,
      @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") @FourDigitYear
          YearMonth month,
      @RequestParam(name = "customer_id", required = false) UUID customerId,
      @RequestParam(name = "error_code", required = false)
          @Pattern(regexp = "customer_not_found|customer_archived|event_too_old|validation_error") @StorableText
          String errorCode,
      @RequestParam(required = false) @Size(max = 255) @StorableText String q,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
      HttpServletRequest request) {
    UnknownQueryParameterValidator.validate(
        request.getParameterMap(),
        Set.of("month", "customer_id", "error_code", "q", "page", "size"));
    return eventRejectionQueryService.search(
        organizationId, month, customerId, errorCode, q, page, size);
  }
}
