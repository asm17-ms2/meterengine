package com.meterengine.event.controller;

import com.meterengine.event.dto.EventRejectionsResponse;
import com.meterengine.event.service.EventRejectionQueryService;
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
public class EventRejectionController {
  private final EventRejectionQueryService eventRejectionQueryService;

  public EventRejectionController(EventRejectionQueryService eventRejectionQueryService) {
    this.eventRejectionQueryService = eventRejectionQueryService;
  }

  @GetMapping("/v1/event-rejections/search")
  @ReadAccess
  @Operation(
      summary = "수집 거절 검색",
      description =
          "수집을 거절한 이벤트의 기록을 최신 기록부터 돌려줍니다. month는 거절을 기록한 달(KST)입니다."
              + " 기록을 시작한 뒤(recording_started_at)에 거절한 단건 요청의 customer_not_found, customer_archived, event_too_old와 batch 요청의 rejected 이벤트가 들어 있습니다."
              + " 본문을 JSON으로 읽지 못했거나 형식 검증에서 거절한 단건 요청, 요청 전체를 400으로 거절한 batch 요청, 인증과 요청 한도 거절, 기록을 시작하기 전의 거절은 들지 않습니다."
              + " 이벤트 수집이 202로 답할 때는 요청에서 바로 거절한 event_too_old와 batch 요청의 rejected 원소가 수집 거절에 기록되지 않고, 저장할 때 찾은 customer_not_found, customer_archived만 기록됩니다."
              + " 원본 properties, 오류 원문과 요청 주체 ID는 돌려주지 않습니다. 페이지는 0부터 셉니다. 모르는 조회 조건은 400 validation_error로 거절합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "한 페이지 분량의 수집 거절 기록입니다."),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description =
            "code=validation_error: month, error_code, page, size 값이 틀렸거나 모르는 조회 조건이 있습니다.")
  })
  public EventRejectionsResponse searchEventRejections(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(description = "거절을 기록한 달(yyyy-MM, KST)입니다. 생략하면 이번 달입니다.")
          @RequestParam(required = false)
          @DateTimeFormat(pattern = "yyyy-MM")
          @FourDigitYear
          YearMonth month,
      @Parameter(description = "요청에 담긴 customer_id가 이 값인 기록만 봅니다.")
          @RequestParam(name = "customer_id", required = false)
          UUID customerId,
      @Parameter(
              description =
                  "이 거절 사유만 봅니다. customer_not_found(고객을 찾을 수 없음), customer_archived(보관된 고객), event_too_old(수집 가능 기간 초과), validation_error(요청 값 오류) 가운데 하나입니다.")
          @RequestParam(name = "error_code", required = false)
          @Pattern(regexp = "customer_not_found|customer_archived|event_too_old|validation_error") @StorableText
          String errorCode,
      @Parameter(description = "transaction_id나 type에서 이 문자열을 대소문자 구분 없이 부분 검색합니다.")
          @RequestParam(required = false)
          @Size(max = 255) @StorableText
          String q,
      @Parameter(description = "0부터 세는 페이지 번호") @RequestParam(defaultValue = "0") @Min(0) int page,
      @Parameter(description = "한 페이지에 담을 개수") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
      HttpServletRequest request) {
    UnknownQueryParameterValidator.validate(
        request.getParameterMap(),
        Set.of("month", "customer_id", "error_code", "q", "page", "size"));
    return eventRejectionQueryService.search(
        organizationId, month, customerId, errorCode, q, page, size);
  }
}
