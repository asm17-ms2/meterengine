package com.meterengine.event.controller;

import com.meterengine.event.dto.IngestEventRequest;
import com.meterengine.event.dto.IngestEventResponse;
import com.meterengine.event.dto.ListEventsResponse;
import com.meterengine.event.service.EventIngestor;
import com.meterengine.event.service.EventQueryService;
import com.meterengine.event.service.EventSearch;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

  static final String CUSTOMER_DESCRIPTION = "이 고객의 이벤트만 봅니다. 생략하면 모든 고객이 대상입니다.";
  static final String MONTH_DESCRIPTION =
      "조회할 달(yyyy-MM, KST)입니다. 생략하면 이번 달입니다. from, to와 함께 쓸 수 없습니다.";
  static final String TYPE_DESCRIPTION = "이 유형의 이벤트만 봅니다.";
  static final String FROM_DESCRIPTION =
      "기간 조회의 시작 시각이며 이 시각을 포함합니다. 날짜(yyyy-MM-dd)나 ISO 8601 시각으로 주고, 시간대가 없으면 KST입니다."
          + " to와 함께 줘야 하며 month와 같이 쓸 수 없습니다.";
  static final String TO_DESCRIPTION =
      "기간 조회의 종료 시각이며 이 시각은 포함하지 않습니다. 날짜만 주면 그 날짜의 하루 전체를 포함합니다. 형식은 from과 같습니다.";
  static final String QUERY_DESCRIPTION =
      "transaction_id, 고객 이름, 고객 외부 ID에서 이 문자열을 대소문자 구분 없이 부분 검색합니다.";
  static final String SORT_DESCRIPTION =
      "정렬 기준입니다. occurred_at(기본), received_at, transaction_id, customer_name, type 가운데 하나입니다.";
  static final String ORDER_DESCRIPTION = "정렬 방향입니다. desc(기본) 또는 asc입니다.";
  static final String PROPERTIES_DESCRIPTION =
      "JSON 객체 문자열입니다. properties에 이 객체의 키와 값이 모두 들어 있는 이벤트만 봅니다(예: {\"region\":\"seoul\"}).";

  private final EventIngestor eventIngestor;
  private final EventQueryService eventQueryService;

  EventController(EventIngestor eventIngestor, EventQueryService eventQueryService) {
    this.eventIngestor = eventIngestor;
    this.eventQueryService = eventQueryService;
  }

  @PostMapping
  @IngestAccess
  @Operation(
      summary = "사용량 이벤트 수집",
      description =
          """
          사용량 이벤트 한 건을 받습니다.
          이벤트를 받으면 202로 답하고 저장은 뒤에서 합니다. 요청 안에서 저장까지 마치면 200으로 답하기도 하며, 이 경우도 정상입니다. 200과 202를 모두 성공으로 처리하세요.
          transaction_id가 같은 이벤트를 다시 보내면 저장하지 않고 처음 저장한 이벤트를 유지합니다.
          200으로 답할 때는 duplicate가 true입니다. 응답을 받지 못했으면 같은 transaction_id로 다시 보내면 됩니다.
          properties는 키나 문자열 값에 NUL 문자나 짝이 없는 UTF-16 서로게이트가 있거나 숫자가 소수점 앞 131072자리나 소수점 뒤 16383자리를 넘으면 거절하고, 그 밖의 내용은 검증하지 않습니다.
          키 순서와 공백은 보존하지 않고, 숫자는 정수와 소수 모두 자릿수를 자르지 않습니다.
          timestamp는 이벤트가 발생한 시각이고 조회 응답에서는 occurred_at으로 나옵니다.
          received_at은 수신 시각이며 요청에 실어도 무시합니다.
          timestamp가 수신 시각보다 35일 넘게 과거이면 받지 않습니다. 1일은 24시간이며, 정확히 35일 전인 이벤트는 받습니다.
          200으로 답할 때는 이미 저장된 transaction_id면 timestamp가 받는 기간 밖이어도 거절하지 않고 duplicate=true로 답합니다.
          202로 답할 때는 고객 확인과 중복 판정을 저장할 때 하므로 404와 409를 내지 않습니다. 고객이 없거나 보관한 고객의 이벤트는 수집 거절에 기록됩니다.
          202로 답할 때는 받는 기간 밖의 timestamp를 이미 저장된 transaction_id여도 400 event_too_old로 거절하고, 이 거절은 수집 거절에 기록되지 않습니다.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "202", description = "요청을 받았습니다. 저장은 뒤에서 하며 응답에 duplicate가 없습니다."),
    @ApiResponse(
        responseCode = "200",
        description =
            "받아서 저장까지 마쳤습니다. 이번에 저장했으면 duplicate=false, 이미 저장된 transaction_id면 duplicate=true입니다."),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description =
            """
            code=malformed_request_body: 본문을 JSON으로 읽을 수 없거나 값의 타입이 맞지 않습니다. timestamp에 오프셋이 없거나 customer_id가 UUID가 아니어도 이 code입니다.
            code=validation_error: 필수 필드가 없거나 transaction_id가 255자를 넘거나, 문자열(properties 안쪽 포함)에 NUL 문자나 짝이 없는 UTF-16 서로게이트가 있거나, properties의 숫자가 소수점 앞 131072자리나 소수점 뒤 16383자리를 넘거나, timestamp가 저장할 수 없는 범위의 시각입니다. 어느 필드가 왜 거절됐는지는 errors에 있습니다.
            code=event_too_old: timestamp가 수신 시각보다 35일 넘게 과거입니다. 같은 이벤트를 다시 보내도 받지 않습니다. errors[0].field는 timestamp입니다. event_too_old 거절은 이벤트 수집이 202로 답할 때는 수집 거절에 기록되지 않습니다.
            """),
    @ApiResponse(
        responseCode = "404",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description =
            "code=customer_not_found: customer_id가 가리키는 고객이 없습니다. 이벤트 수집이 202로 답할 때는 이 응답 대신 저장할 때 수집 거절에 기록합니다."),
    @ApiResponse(
        responseCode = "409",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description =
            "code=customer_archived: customer_id가 가리키는 고객을 보관했습니다. 이미 저장된 transaction_id면 거절하지 않고 200 duplicate=true로 답합니다. 이벤트 수집이 202로 답할 때는 이 응답 대신 저장할 때 수집 거절에 기록합니다."),
    @ApiResponse(
        responseCode = "413",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=request_body_too_large: 요청 본문이 64KB를 넘습니다."),
    @ApiResponse(
        responseCode = "503",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description =
            "code=event_ingest_unavailable: 이벤트를 받지 못했습니다. 같은 transaction_id로 다시 보내면 됩니다.")
  })
  public ResponseEntity<IngestEventResponse> ingestEvent(
      @CurrentOrganizationId UUID organizationId, @Valid @RequestBody IngestEventRequest request) {
    IngestEventResponse response = eventIngestor.ingest(organizationId, request);
    HttpStatus status = response.storedInRequest() ? HttpStatus.OK : HttpStatus.ACCEPTED;
    return ResponseEntity.status(status).body(response);
  }

  @GetMapping
  @ReadAccess
  @Operation(
      summary = "수집 이벤트 목록 조회",
      description =
          """
          지정한 달의 이벤트를 한 페이지씩 돌려줍니다.
          기본은 occurred_at 최신순이고, occurred_at이 같으면 transaction_id 내림차순입니다. sort와 order로 정렬 기준과 방향을 바꾸면 값이 같은 이벤트는 transaction_id를 같은 방향으로 정렬합니다.
          기간은 KST 기준의 달입니다. occurred_at 2026-08-31T23:59:59+09:00 이벤트는 8월에,
          2026-09-01T00:00:00+09:00 이벤트는 9월에 듭니다. 사용량 조회와 기준이 같습니다.
          customer_id, month, type을 함께 주면 모두 만족하는 이벤트만 나오고, total은 조건을 적용한 뒤의 건수입니다.
          from과 to를 함께 주면 월 대신 자유 기간을 조회합니다. 시작은 포함하고 종료 시각은 제외합니다. 날짜만 준 to는 그 날짜의 하루 전체를 포함합니다. 시간대 없는 날짜와 시각은 KST입니다.
          q는 transaction_id, 고객 이름과 외부 ID의 부분 검색입니다. properties는 그 JSON 객체의 키와 값을 모두 가진 이벤트만 고릅니다.
          from과 to는 함께 주어야 하고 month와 같이 쓸 수 없습니다. 청구서 검색은 한쪽만 주거나 month와 함께 줄 수 있어 규칙이 다릅니다.
          모르는 조회 조건은 무시합니다. 내보내기, 월 목록, 과금에서 빠진 이벤트 검색과 수집 거절 검색은 모르는 조회 조건을 400으로 거절합니다.
          properties는 저장된 값 그대로입니다. 키 순서와 공백은 보낸 것과 다를 수 있고, 숫자 자릿수는 그대로입니다.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "한 페이지 분량의 이벤트입니다. 조건에 맞는 것이 없으면 events가 빈 배열입니다."),
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
            있습니다. from과 to 가운데 하나만 줬거나 month와 함께 줬거나 읽을 수 없는 시각이거나, sort, order, properties 값이 틀려도 같습니다.
            """),
    @ApiResponse(
        responseCode = "404",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=customer_not_found: customer_id가 가리키는 고객이 없습니다.")
  })
  public ListEventsResponse listEvents(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(description = "0부터 세는 페이지 번호.") @RequestParam(defaultValue = "0") @Min(0) int page,
      @Parameter(description = "한 페이지에 담을 개수.") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
      @Parameter(description = "이 고객의 이벤트만 돌려줍니다. 생략하면 모든 고객이 대상입니다.")
          @RequestParam(name = "customer_id", required = false)
          UUID customerId,
      @Parameter(
              description = "조회할 달(yyyy-MM, KST)입니다. 생략하면 이번 달입니다. from, to와 함께 쓸 수 없습니다.",
              example = "2026-08")
          @RequestParam(required = false)
          @DateTimeFormat(pattern = "yyyy-MM")
          @FourDigitYear
          YearMonth month,
      @Parameter(description = "이 유형의 이벤트만 돌려줍니다. 어느 미터의 event_type도 아닌 값이어도 저장된 이벤트는 조회됩니다.")
          @RequestParam(required = false)
          @StorableText
          String type,
      @Parameter(description = FROM_DESCRIPTION)
          @RequestParam(required = false)
          @StorableText
          @Size(max = 64) String from,
      @Parameter(description = TO_DESCRIPTION)
          @RequestParam(required = false)
          @StorableText
          @Size(max = 64) String to,
      @Parameter(description = QUERY_DESCRIPTION)
          @RequestParam(name = "q", required = false)
          @StorableText
          @Size(max = 512) String query,
      @Parameter(description = SORT_DESCRIPTION)
          @RequestParam(required = false)
          @StorableText
          @Size(max = 40) String sort,
      @Parameter(description = ORDER_DESCRIPTION)
          @RequestParam(required = false)
          @StorableText
          @Size(max = 4) String order,
      @Parameter(description = PROPERTIES_DESCRIPTION)
          @RequestParam(required = false)
          @StorableText
          @Size(max = 4096) String properties) {
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
      summary = "수집 이벤트 유형 목록 조회",
      description =
          "미터 정의와 관계없이 조회 기간에 실제로 수집된 유형을 이름순으로 돌려줍니다. 기간은 month나 from과 to로 정합니다. 모르는 조회 조건은 무시합니다.")
  public Map<String, List<String>> listEventTypes(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(description = CUSTOMER_DESCRIPTION)
          @RequestParam(name = "customer_id", required = false)
          UUID customerId,
      @Parameter(description = MONTH_DESCRIPTION)
          @RequestParam(required = false)
          @DateTimeFormat(pattern = "yyyy-MM")
          @FourDigitYear
          YearMonth month,
      @Parameter(description = FROM_DESCRIPTION)
          @RequestParam(required = false)
          @StorableText
          @Size(max = 64) String from,
      @Parameter(description = TO_DESCRIPTION)
          @RequestParam(required = false)
          @StorableText
          @Size(max = 64) String to,
      @Parameter(description = QUERY_DESCRIPTION)
          @RequestParam(name = "q", required = false)
          @StorableText
          @Size(max = 512) String query,
      @Parameter(description = PROPERTIES_DESCRIPTION)
          @RequestParam(required = false)
          @StorableText
          @Size(max = 4096) String properties) {
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
      summary = "수집 이벤트 월 목록 조회",
      description =
          "현재 업무 환경에 저장된 이벤트의 occurred_at을 KST 기준 월로 묶어 중복 없이 최신 월부터 돌려줍니다. 이벤트 목록의 고객, 유형, 검색 조건과 관계없이 업무 환경 전체의 월입니다."
              + " GET /v1/usage/months와 같은 월을 돌려주며, 이 경로는 이벤트 목록(GET /v1/events)의 month를 고를 때 씁니다. 모르는 조회 조건은 400 validation_error로 거절합니다.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "이벤트가 있는 월(yyyy-MM, KST)을 최신 월부터 담은 months입니다."),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=validation_error: 모르는 조회 조건이 있습니다.")
  })
  public Map<String, List<String>> listEventMonths(
      @CurrentOrganizationId UUID organizationId, HttpServletRequest request) {
    UnknownQueryParameterValidator.validate(request.getParameterMap(), Set.of());
    return Map.of("months", eventQueryService.months(organizationId));
  }

  @GetMapping("/lookup")
  @ReadAccess
  @Operation(
      summary = "수집 이벤트 단건 조회",
      description =
          "transaction_id로 이벤트 한 건을 돌려줍니다. 달이나 페이지와 관계없이 찾고, 없으면 404 event_not_found입니다. 모르는 조회 조건은 무시합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "transaction_id가 같은 이벤트입니다."),
    @ApiResponse(
        responseCode = "404",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=event_not_found: 이 transaction_id의 이벤트가 없습니다.")
  })
  public ListEventsResponse.EventResponse getEvent(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(description = "찾을 이벤트의 transaction_id입니다. 보낸 값과 정확히 같아야 합니다.")
          @RequestParam(name = "transaction_id")
          @StorableText
          @Size(min = 1, max = 1024) String transactionId) {
    return eventQueryService.find(organizationId, transactionId);
  }
}
