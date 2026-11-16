package com.meterengine.event.controller;

import com.meterengine.event.service.EventCsvService;
import com.meterengine.event.service.EventSearch;
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
import jakarta.validation.constraints.Size;
import java.time.YearMonth;
import java.util.Set;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@EnvironmentReady
public class EventExportController {
  private final EventCsvService eventCsvService;

  EventExportController(EventCsvService eventCsvService) {
    this.eventCsvService = eventCsvService;
  }

  @GetMapping("/v1/events/export")
  @ReadAccess
  @Operation(
      summary = "수집 이벤트 CSV 내보내기",
      description =
          "이벤트 목록과 같은 월 또는 자유 기간, 고객, 유형, 검색어, 속성 조건과 정렬을 적용합니다. page와 size는 받지만 내보내기에는 쓰지 않습니다."
              + " 조건 전체가 10,000행을 넘거나, include_properties=true일 때 원본 properties 합계가 32MB를 넘으면 400 validation_error로 거절합니다."
              + " UTF-8 BOM과 CRLF를 쓰며, 수식으로 읽힐 문자열은 앞에 작은따옴표를 붙입니다."
              + " include_properties=true일 때만 원본 properties JSON 열을 더하며 숫자 자릿수를 그대로 둡니다. 모르는 조회 조건은 400 validation_error로 거절합니다.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "고른 조건 전체의 이벤트 CSV입니다. 결과가 없어도 같은 열 제목을 돌려줍니다.",
        content = @Content(mediaType = "text/csv", schema = @Schema(type = "string"))),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description =
            "code=validation_error: 조회 조건 값이 틀렸거나 모르는 조회 조건이 있거나, 조건 전체가 10,000행을 넘거나, include_properties=true일 때 원본 properties 합계가 32MB를 넘습니다."),
    @ApiResponse(
        responseCode = "404",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=customer_not_found: customer_id가 가리키는 고객이 없습니다.")
  })
  public ResponseEntity<byte[]> exportEvents(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(description = "받지만 내보내기에는 쓰지 않습니다. CSV에는 조건 전체의 행이 들어갑니다.")
          @RequestParam(defaultValue = "0")
          @Min(0) int page,
      @Parameter(description = "받지만 내보내기에는 쓰지 않습니다. CSV에는 조건 전체의 행이 들어갑니다.")
          @RequestParam(defaultValue = "20")
          @Min(1) @Max(100) int size,
      @Parameter(description = EventController.CUSTOMER_DESCRIPTION)
          @RequestParam(name = "customer_id", required = false)
          UUID customerId,
      @Parameter(description = EventController.MONTH_DESCRIPTION)
          @RequestParam(required = false)
          @DateTimeFormat(pattern = "yyyy-MM")
          @FourDigitYear
          YearMonth month,
      @Parameter(description = EventController.TYPE_DESCRIPTION)
          @RequestParam(required = false)
          @StorableText
          String type,
      @Parameter(description = EventController.FROM_DESCRIPTION)
          @RequestParam(required = false)
          @StorableText
          @Size(max = 64) String from,
      @Parameter(description = EventController.TO_DESCRIPTION)
          @RequestParam(required = false)
          @StorableText
          @Size(max = 64) String to,
      @Parameter(description = EventController.QUERY_DESCRIPTION)
          @RequestParam(name = "q", required = false)
          @StorableText
          @Size(max = 512) String query,
      @Parameter(description = EventController.SORT_DESCRIPTION)
          @RequestParam(required = false)
          @StorableText
          @Size(max = 40) String sort,
      @Parameter(description = EventController.ORDER_DESCRIPTION)
          @RequestParam(required = false)
          @StorableText
          @Size(max = 4) String order,
      @Parameter(description = EventController.PROPERTIES_DESCRIPTION)
          @RequestParam(required = false)
          @StorableText
          @Size(max = 4096) String properties,
      @Parameter(description = "true면 원본 properties JSON 열을 더합니다. 기본은 false입니다.")
          @RequestParam(name = "include_properties", defaultValue = "false")
          boolean includeProperties,
      HttpServletRequest request) {
    UnknownQueryParameterValidator.validate(
        request.getParameterMap(),
        Set.of(
            "page",
            "size",
            "customer_id",
            "month",
            "type",
            "from",
            "to",
            "q",
            "sort",
            "order",
            "properties",
            "include_properties"));
    return eventCsvService.export(
        organizationId,
        customerId,
        EventSearch.resolve(month, from, to, query, sort, order, properties),
        type,
        includeProperties);
  }
}
