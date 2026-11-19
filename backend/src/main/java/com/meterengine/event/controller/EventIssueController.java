package com.meterengine.event.controller;

import com.meterengine.event.dto.EventIssuesResponse;
import com.meterengine.event.service.EventIssueService;
import com.meterengine.global.error.ErrorResponse;
import com.meterengine.global.security.CurrentOrganizationId;
import com.meterengine.global.security.EnvironmentReady;
import com.meterengine.global.security.ReadAccess;
import com.meterengine.global.validation.FourDigitYear;
import com.meterengine.global.validation.StorableText;
import com.meterengine.global.validation.UnknownQueryParameterValidator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
      summary = "과금에서 빠진 이벤트 조회",
      description =
          "저장된 이벤트를 지금 과금 미터 정의와 단가로 다시 검사해, 연결할 과금 미터가 없거나, 수량을 읽을 수 없거나, 적용 단가가 없어 과금에서 빠진 이벤트를 돌려줍니다."
              + " 대상은 조회한 달에 발생했고 occurred_at이 cutoff_at보다 이른 이벤트이며, cutoff_at은 지금 시각과 그 달의 끝 가운데 이른 쪽입니다."
              + " 수신 시각(received_at)은 보지 않으므로 집계 마감 뒤에 받아 청구에서 빠진 지난달 이벤트를 따로 가려내지 않습니다."
              + " total과 summary는 고객, 검색어, 사유 조건을 적용한 문제 행 수이며, 한 이벤트가 여러 과금 미터에서 문제가 되면 여러 번 셉니다."
              + " warnings는 이 조건과 관계없이 그 달 전체를 셉니다. 수집을 거절한 이벤트는 저장되지 않아 들지 않습니다."
              + " 원본 properties는 돌려주지 않습니다. 모르는 조회 조건은 400 validation_error로 거절합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "한 페이지 분량의 과금에서 빠진 이벤트입니다."),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=validation_error: month, reason, page, size 값이 틀렸거나 모르는 조회 조건이 있습니다."),
    @ApiResponse(
        responseCode = "404",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=customer_not_found: customer_id가 가리키는 고객이 없습니다.")
  })
  public EventIssuesResponse searchEventIssues(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(description = "조회할 달(yyyy-MM, KST)입니다. 생략하면 이번 달입니다.")
          @RequestParam(required = false)
          @DateTimeFormat(pattern = "yyyy-MM")
          @FourDigitYear
          YearMonth month,
      @Parameter(description = "이 고객의 이벤트만 봅니다. 없는 고객이면 404 customer_not_found입니다.")
          @RequestParam(name = "customer_id", required = false)
          UUID customerId,
      @Parameter(
              description =
                  "이 문제 종류만 봅니다. unknown_event_type(연결할 과금 미터 없음), invalid_target_property(수량을 읽을 수 없음), unpriced_usage(적용 단가 없음) 가운데 하나입니다.")
          @RequestParam(required = false)
          @Pattern(regexp = "unknown_event_type|invalid_target_property|unpriced_usage") @StorableText
          String reason,
      @Parameter(description = EventController.QUERY_DESCRIPTION)
          @RequestParam(required = false)
          @Size(max = 255) @StorableText
          String q,
      @Parameter(description = "0부터 세는 페이지 번호") @RequestParam(defaultValue = "0") @Min(0) int page,
      @Parameter(description = "한 페이지에 담을 개수") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
      HttpServletRequest request) {
    UnknownQueryParameterValidator.validate(
        request.getParameterMap(), Set.of("month", "customer_id", "reason", "q", "page", "size"));
    return eventIssueService.search(organizationId, month, customerId, reason, q, page, size);
  }
}
