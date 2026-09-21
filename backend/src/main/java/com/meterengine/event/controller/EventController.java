package com.meterengine.event.controller;

import com.meterengine.event.dto.IngestEventRequest;
import com.meterengine.event.dto.IngestEventResponse;
import com.meterengine.event.dto.ListEventsResponse;
import com.meterengine.event.service.EventService;
import com.meterengine.global.error.ErrorResponse;
import com.meterengine.metric.service.BillableMetricUsageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.YearMonth;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/events")
public class EventController {

  private final EventService eventService;

  EventController(EventService eventService) {
    this.eventService = eventService;
  }

  @PostMapping
  @Operation(
      summary = "사용량 이벤트 수집",
      description =
          """
          사용량 이벤트 한 건을 저장한다.
          transaction_id가 같은 이벤트를 다시 보내면 저장하지 않고 처음 저장한 이벤트를 유지한다.
          이때도 응답은 200이고 duplicate가 true다. 응답을 받지 못했으면 같은 transaction_id로 다시 보내면 된다.
          properties의 내용은 검증하지 않는다. 키 순서와 공백은 보존되지 않고,
          숫자는 정수와 소수 모두 자릿수가 잘리지 않는다.
          timestamp는 이벤트가 발생한 시각이고 조회 응답에서는 occurred_at으로 나온다.
          received_at은 서버가 받은 시각이며 요청에 실어도 무시된다.
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
            code=validation_error: 필수 필드가 없거나 형식이 틀렸다. 어느 필드가 왜 거절됐는지는 errors에 있다.
            code=invalid_event: 저장할 수 없는 값이 들어 있다. 같은 본문을 다시 보내도 성공하지 않는다.
            """),
    @ApiResponse(
        responseCode = "404",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=customer_not_found: customer_id가 가리키는 고객이 없다")
  })
  public IngestEventResponse ingestEvent(
      @Parameter(description = "도입사 ID. 인증이 붙기 전까지 쓰는 임시 헤더다.") @RequestHeader("X-Organization-Id")
          UUID organizationId,
      @Valid @RequestBody IngestEventRequest request) {
    return eventService.ingest(organizationId, request);
  }

  @GetMapping
  @Operation(
      summary = "사용량 이벤트 조회",
      description =
          """
          지정한 달의 이벤트를 occurred_at 최신순으로 한 페이지씩 돌려준다.
          occurred_at이 같으면 transaction_id 내림차순이다.
          기간은 KST 기준의 달이다. occurred_at 2026-08-31T23:59:59+09:00 이벤트는 8월에,
          2026-09-01T00:00:00+09:00 이벤트는 9월에 든다. 사용량 조회와 기준이 같다.
          customer_id, month, type을 함께 주면 모두 만족하는 이벤트만 나오고, total은 조건을 적용한 뒤의 건수다.
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
            아니거나 month가 yyyy-MM이 아니거나, X-Organization-Id가 없거나 UUID가 아니다.
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
      @Parameter(description = "도입사 ID. 인증이 붙기 전까지 쓰는 임시 헤더다.") @RequestHeader("X-Organization-Id")
          UUID organizationId,
      @Parameter(description = "0부터 세는 페이지 번호.") @RequestParam(defaultValue = "0") @Min(0) int page,
      @Parameter(description = "한 페이지에 담을 개수.") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
      @Parameter(description = "고객을 좁힌다. 생략하면 모든 고객이 대상이다.")
          @RequestParam(name = "customer_id", required = false)
          UUID customerId,
      @Parameter(description = "조회할 달(yyyy-MM, KST). 생략하면 이번 달이다.", example = "2026-08")
          @RequestParam(required = false)
          @DateTimeFormat(pattern = "yyyy-MM")
          YearMonth month,
      @Parameter(description = "이벤트 type을 좁힌다. 어느 미터의 event_type도 아닌 값이어도 저장된 이벤트는 조회된다.")
          @RequestParam(required = false)
          String type) {
    YearMonth target = month == null ? BillableMetricUsageService.currentMonth() : month;
    return eventService.list(organizationId, customerId, target, type, page, size);
  }
}
