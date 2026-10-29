package com.meterengine.event.controller;

import com.meterengine.event.service.EventCsvService;
import com.meterengine.event.service.EventSearch;
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
      summary = "사용량 이벤트 CSV 내보내기",
      description =
          "이벤트 목록과 같은 월 또는 자유 기간, 고객, 유형, 검색, 속성 조건과 정렬을 적용한다. page와 size는 목록 링크 호환을 위해 받지만 내보내기에는 적용하지 않는다. 조건 전체가 10000행을 넘거나 원본 properties 합계가 32MB를 넘으면 400 validation_error로 거절한다. UTF-8 BOM과 CRLF를 사용하며 문자열의 스프레드시트 수식을 이스케이프한다. include_properties=true를 선택한 경우에만 원본 properties JSON 열을 추가하며 숫자 자릿수를 보존한다.")
  @ApiResponse(
      responseCode = "200",
      description = "선택한 조건 전체의 이벤트 CSV. 빈 결과도 같은 열 제목을 반환한다.",
      content = @Content(mediaType = "text/csv", schema = @Schema(type = "string")))
  public ResponseEntity<byte[]> exportEvents(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(description = "목록 호환용이며 CSV 행 범위에는 적용하지 않는다.")
          @RequestParam(defaultValue = "0")
          @Min(0) int page,
      @Parameter(description = "목록 호환용이며 CSV 행 범위에는 적용하지 않는다.")
          @RequestParam(defaultValue = "20")
          @Min(1) @Max(100) int size,
      @RequestParam(name = "customer_id", required = false) UUID customerId,
      @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") @FourDigitYear
          YearMonth month,
      @RequestParam(required = false) @StorableText String type,
      @RequestParam(required = false) @StorableText @Size(max = 64) String from,
      @RequestParam(required = false) @StorableText @Size(max = 64) String to,
      @RequestParam(name = "q", required = false) @StorableText @Size(max = 512) String query,
      @RequestParam(required = false) @StorableText @Size(max = 40) String sort,
      @RequestParam(required = false) @StorableText @Size(max = 4) String order,
      @RequestParam(required = false) @StorableText @Size(max = 4096) String properties,
      @RequestParam(name = "include_properties", defaultValue = "false") boolean includeProperties,
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
