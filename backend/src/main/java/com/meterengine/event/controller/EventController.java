package com.meterengine.event.controller;

import com.meterengine.event.dto.IngestEventRequest;
import com.meterengine.event.dto.IngestEventResponse;
import com.meterengine.event.dto.ListEventsResponse;
import com.meterengine.event.service.EventIngestionService;
import com.meterengine.event.service.EventQueryService;
import com.meterengine.event.service.EventRejectionService;
import com.meterengine.event.service.EventSearch;
import com.meterengine.global.error.BusinessException;
import com.meterengine.global.error.ErrorResponse;
import com.meterengine.global.security.CurrentOrganizationId;
import com.meterengine.global.security.EnvironmentReady;
import com.meterengine.global.security.IngestAccess;
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
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@EnvironmentReady
@RequestMapping("/v1/events")
public class EventController {

  private final EventIngestionService eventIngestionService;
  private final EventQueryService eventQueryService;
  private final EventRejectionService eventRejectionService;

  EventController(
      EventIngestionService eventIngestionService,
      EventQueryService eventQueryService,
      EventRejectionService eventRejectionService) {
    this.eventIngestionService = eventIngestionService;
    this.eventQueryService = eventQueryService;
    this.eventRejectionService = eventRejectionService;
  }

  @PostMapping
  @IngestAccess
  @Operation(
      summary = "사용량 이벤트 수집",
      description =
          """
          사용량 이벤트 한 건을 저장한다.
          transaction_id가 같은 이벤트를 다시 보내면 저장하지 않고 처음 저장한 이벤트를 유지한다.
          이때도 응답은 200이고 duplicate가 true다. 응답을 받지 못했으면 같은 transaction_id로 다시 보내면 된다.
          properties는 키나 문자열 값에 NUL 문자나 짝이 없는 UTF-16 서로게이트가 있거나 숫자가 소수점 앞 131072자리나 소수점 뒤 16383자리를 넘으면 거절하고, 그 밖의 내용은 검증하지 않는다.
          키 순서와 공백은 보존되지 않고, 숫자는 정수와 소수 모두 자릿수가 잘리지 않는다.
          timestamp는 이벤트가 발생한 시각이고 조회 응답에서는 occurred_at으로 나온다.
          received_at은 서버가 받은 시각이며 요청에 실어도 무시된다.
          timestamp가 서버의 수집 판정 시각보다 35일 넘게 과거이면 받지 않는다. 1일은 24시간이며, 정확히 35일 전인 이벤트는 받는다.
          이미 저장된 transaction_id면 timestamp가 받는 기간 밖이어도 거절하지 않고 duplicate=true로 답한다.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "저장했다(duplicate=false). 이미 저장된 transaction_id면 duplicate=true다"),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description =
            """
            code=validation_error: 필수 필드가 없거나 형식이 틀렸거나, 문자열(properties 안쪽 포함)에 NUL 문자나 짝이 없는 UTF-16 서로게이트가 있거나, properties의 숫자가 소수점 앞 131072자리나 소수점 뒤 16383자리를 넘거나, timestamp가 기원전 4713-01-01T00:00:00Z부터 294276-12-31T23:59:59.999999Z까지의 범위 밖이다. 어느 필드가 왜 거절됐는지는 errors에 있다.
            code=event_too_old: timestamp가 수집 판정 시각보다 35일 넘게 과거이다. 같은 이벤트를 다시 보내도 받지 않는다. errors[0].field는 timestamp다.
            """),
    @ApiResponse(
        responseCode = "404",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=customer_not_found: customer_id가 가리키는 고객이 없다"),
    @ApiResponse(
        responseCode = "409",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description =
            "code=customer_archived: customer_id가 가리키는 고객을 보관했다. 이미 저장된 transaction_id면 거절하지 않고 200 duplicate=true로 답한다")
  })
  public IngestEventResponse ingestEvent(
      @CurrentOrganizationId UUID organizationId, @Valid @RequestBody IngestEventRequest request) {
    try {
      return eventIngestionService.ingest(organizationId, request);
    } catch (BusinessException exception) {
      eventRejectionService.record(organizationId, request, exception);
      throw exception;
    }
  }

  @GetMapping
  @ReadAccess
  @Operation(
      summary = "사용량 이벤트 조회",
      description =
          """
          지정한 달의 이벤트를 occurred_at 최신순으로 한 페이지씩 돌려준다.
          occurred_at이 같으면 transaction_id 내림차순이다.
          기간은 KST 기준의 달이다. occurred_at 2026-08-31T23:59:59+09:00 이벤트는 8월에,
          2026-09-01T00:00:00+09:00 이벤트는 9월에 든다. 사용량 조회와 기준이 같다.
          customer_id, month, type을 함께 주면 모두 만족하는 이벤트만 나오고, total은 조건을 적용한 뒤의 건수다.
          from과 to를 함께 주면 월 대신 자유 기간을 조회한다. 시작은 포함하고 종료 시각은 제외한다. 날짜만 준 to는 그 날짜의 하루 전체를 포함한다. 시간대 없는 날짜와 시각은 KST다.
          q는 transaction_id, 고객 이름과 외부 ID의 부분 검색이다. properties는 JSON 객체의 값이 포함된 이벤트를 찾는다.
          from과 to는 함께 주어야 하고 month와 같이 쓸 수 없다. 인보이스 검색은 한쪽만 주거나 month와 함께 줄 수 있어 규칙이 다르다.
          모르는 조회 조건은 무시한다. 내보내기, 월 목록, 이벤트 문제와 수집 거절 검색은 모르는 조회 조건을 400으로 거절한다.
          properties는 저장된 값 그대로다. 키 순서와 공백은 보낸 것과 다를 수 있고, 숫자 자릿수는 그대로다.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "한 페이지 분량의 이벤트. 조건에 맞는 것이 없으면 events가 빈 배열이다"),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description =
            """
            code=validation_error: page가 음수거나 size가 1~100 밖이거나, customer_id가 UUID가
            아니거나 month가 yyyy-MM이 아니거나 0001-01부터 9999-12까지의 범위 밖이거나, type에 NUL 문자나 짝이 없는 UTF-16 서로게이트가
            있다.
            """),
    @ApiResponse(
        responseCode = "404",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=customer_not_found: customer_id가 가리키는 고객이 없다")
  })
  public ListEventsResponse listEvents(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(description = "0부터 세는 페이지 번호.") @RequestParam(defaultValue = "0") @Min(0) int page,
      @Parameter(description = "한 페이지에 담을 개수.") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
      @Parameter(description = "고객을 좁힌다. 생략하면 모든 고객이 대상이다.")
          @RequestParam(name = "customer_id", required = false)
          UUID customerId,
      @Parameter(description = "조회할 달(yyyy-MM, KST). 생략하면 이번 달이다.", example = "2026-08")
          @RequestParam(required = false)
          @DateTimeFormat(pattern = "yyyy-MM")
          @FourDigitYear
          YearMonth month,
      @Parameter(description = "이벤트 type을 좁힌다. 어느 미터의 event_type도 아닌 값이어도 저장된 이벤트는 조회된다.")
          @RequestParam(required = false)
          @StorableText
          String type,
      @RequestParam(required = false) @StorableText @Size(max = 64) String from,
      @RequestParam(required = false) @StorableText @Size(max = 64) String to,
      @RequestParam(name = "q", required = false) @StorableText @Size(max = 512) String query,
      @RequestParam(required = false) @StorableText @Size(max = 40) String sort,
      @RequestParam(required = false) @StorableText @Size(max = 4) String order,
      @RequestParam(required = false) @StorableText @Size(max = 4096) String properties) {
    return eventQueryService.list(
        organizationId,
        customerId,
        EventSearch.resolve(month, from, to, query, sort, order, properties),
        type,
        page,
        size);
  }

  @GetMapping("/types")
  @ReadAccess
  @Operation(
      summary = "수집된 이벤트 유형 조회",
      description = "미터 정의와 관계없이 조회 기간에 실제로 수집된 유형을 돌려준다. month 또는 from/to를 사용한다. 모르는 조회 조건은 무시한다.")
  public Map<String, List<String>> listEventTypes(
      @CurrentOrganizationId UUID organizationId,
      @RequestParam(name = "customer_id", required = false) UUID customerId,
      @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") @FourDigitYear
          YearMonth month,
      @RequestParam(required = false) @StorableText @Size(max = 64) String from,
      @RequestParam(required = false) @StorableText @Size(max = 64) String to,
      @RequestParam(name = "q", required = false) @StorableText @Size(max = 512) String query,
      @RequestParam(required = false) @StorableText @Size(max = 4096) String properties) {
    return Map.of(
        "types",
        eventQueryService.types(
            organizationId,
            customerId,
            EventSearch.resolve(month, from, to, query, null, null, properties)));
  }

  @GetMapping("/months")
  @ReadAccess
  @Operation(
      summary = "이벤트가 있는 월 조회",
      description =
          "현재 도입사와 업무 환경에 저장된 이벤트의 occurred_at을 KST 월로 구분한다. 중복 없이 최신 월부터 반환하며 목록의 고객, 유형과 검색 조건에 관계없이 환경 전체의 월을 보여 준다. 모르는 조회 조건은 400 validation_error로 거절한다.")
  public Map<String, List<String>> listEventMonths(
      @CurrentOrganizationId UUID organizationId, HttpServletRequest request) {
    UnknownQueryParameterValidator.validate(request.getParameterMap(), Set.of());
    return Map.of("months", eventQueryService.months(organizationId));
  }

  @GetMapping("/lookup")
  @ReadAccess
  @Operation(
      summary = "이벤트 단건 조회",
      description =
          "transaction_id 원문으로 조회한다. 목록의 페이지나 기간에 포함되지 않아도 상세 정보를 열 수 있다. 모르는 조회 조건은 무시한다.")
  public ListEventsResponse.EventResponse getEvent(
      @CurrentOrganizationId UUID organizationId,
      @RequestParam(name = "transaction_id") @StorableText @Size(min = 1, max = 1024) String transactionId) {
    return eventQueryService.find(organizationId, transactionId);
  }
}
