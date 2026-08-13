package com.meterengine.usage;

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
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사용량 이벤트 수집 (MS2-130)과 조회 (MS2-131).
 *
 * <p><b>X-Organization-Id는 임시물이다.</b> 지금은 요청이 도입사를 자칭하기만 하면 통과하므로 누구나 아무 도입사를 사칭할 수 있다. MS2-126이
 * Bearer API 키 인증을 붙이면서 이 파라미터를 인증 주체에서 꺼내는 형태로 바꾼다. 그때 헤더 누락은 400이 아니라 401이 된다.
 *
 * <p>필터를 미리 만들어 두지 않은 이유: MS2-126은 Spring Security로 들어올 예정이라 컨트롤러가 SecurityContext에서 도입사를 꺼내게 된다.
 * 어느 쪽이든 이 시그니처는 바뀌므로, 지금 필터를 두면 나중에 버릴 코드만 늘어난다.
 *
 * <p><b>조회를 새 클래스로 빼지 않은 이유.</b> {@link UsageEventExceptionHandler}가 {@code assignableTypes}로 이
 * 컨트롤러에만 걸려 있어서, 조회를 다른 클래스로 옮기면 오류가 {@code code} 붙은 problem+json이 아니라 프레임워크 기본형으로 나간다. FE 공통 오류
 * 컴포넌트가 그 {@code code}로 문구를 고르므로 형식이 갈라지면 안 된다. {@code /v1/events}는 하나의 리소스라 POST와 GET을 한 컨트롤러에 두는
 * 것이 표준적이기도 하다.
 */
@RestController
@RequestMapping("/v1/events")
public class UsageEventController {

  private final UsageEventIngestService ingestService;
  private final UsageEventQueryService queryService;

  UsageEventController(UsageEventIngestService ingestService, UsageEventQueryService queryService) {
    this.ingestService = ingestService;
    this.queryService = queryService;
  }

  @PostMapping
  @Operation(
      summary = "사용량 이벤트 수집",
      description =
          """
          같은 (도입사, transaction_id)의 재전송은 저장하지 않고 최초 저장본을 유지한다(first-write-wins).
          재전송도 200이며 duplicate가 true로 내려온다.
          properties의 내용은 검증하지 않는다. 저장 타입이 jsonb라 키 순서와 공백은 보존되지 않지만,
          숫자는 정수/소수 모두 자릿수가 잘리지 않는다.
          timestamp는 이벤트 발생 시각이며 occurred_at으로 저장된다.
          received_at은 서버가 찍으며 요청에 담긴 값은 무시된다.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "저장했거나(duplicate=false) 이미 저장돼 있다(true)"),
    @ApiResponse(
        responseCode = "400",
        description =
            """
            code=validation_error: 형식 검증 실패. errors에 필드명과 사유가 들어 있다.
            code=customer_not_found: (도입사, customer_id) 조합을 찾을 수 없다.
            code=invalid_event: DB가 담을 수 없는 값이다. 같은 본문으로 재시도해도 성공하지 않는다.
            """)
  })
  public IngestEventResponse ingest(
      @Parameter(description = "도입사 ID. MS2-126의 Bearer 인증으로 대체될 임시 헤더다.")
          @RequestHeader("X-Organization-Id")
          UUID organizationId,
      @Valid @RequestBody IngestEventRequest request) {
    return ingestService.ingest(organizationId, request);
  }

  @GetMapping
  @Operation(
      summary = "사용량 이벤트 조회",
      description =
          """
          지정한 달의 이벤트를 occurred_at 최신순으로 한 페이지씩 돌려준다. 같은 시각이 여럿이면
          transaction_id 내림차순으로 순서가 고정돼, 같은 요청을 여러 번 보내도 결과가 같다.
          기간은 KST 기준의 달이다. occurred_at 2026-08-31T23:59:59+09:00 이벤트는 8월에,
          2026-09-01T00:00:00+09:00 이벤트는 9월에 귀속된다 (사용량 조회와 같은 판정).
          customer_id, month, event_type을 함께 주면 AND로 걸리고 total도 필터를 적용한 뒤의 건수다.
          properties는 저장된 값을 그대로 낸다. 서버가 키를 고르거나 값을 해석하지 않는다.
          저장 타입이 jsonb라 키 순서와 공백은 수집 때와 달라질 수 있고, 숫자 자릿수는 그대로다.

          FE 규약 둘은 서버가 강제할 수 없어 여기 적어 둔다.
          page는 0부터다(화면의 1페이지 = page=0). 필터를 바꾸면 page를 0으로 되돌린다.
          안 그러면 3페이지를 보다가 조건을 좁혔을 때 그만큼 페이지가 없어 빈 화면이 뜬다.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "한 페이지 분량의 이벤트. 조건에 맞는 것이 없으면 events가 빈 배열이다"),
    @ApiResponse(
        responseCode = "400",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)),
        description =
            """
            code=validation_error: page가 음수거나 size가 1~100 밖이거나, customer_id가 UUID가
            아니거나 month가 yyyy-MM이 아니거나, X-Organization-Id가 없거나 UUID가 아니다.
            code=customer_not_found: (도입사, customer_id) 조합을 찾을 수 없다. 미등록과 다른 도입사
            소속은 구별되지 않는다.
            """)
  })
  public EventPageResponse query(
      @Parameter(description = "도입사 ID. MS2-126의 Bearer 인증으로 대체될 임시 헤더다.")
          @RequestHeader("X-Organization-Id")
          UUID organizationId,
      @Parameter(description = "0부터 세는 페이지 번호. 화면의 1페이지가 0이다.")
          @RequestParam(defaultValue = "0")
          @Min(0) int page,
      @Parameter(description = "한 페이지에 담을 개수.") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
      @Parameter(description = "고객을 좁힌다. 생략하면 도입사의 모든 고객이 대상이다.")
          @RequestParam(name = "customer_id", required = false)
          UUID customerId,
      @Parameter(description = "조회할 달(yyyy-MM, KST). 생략하면 이번 달이다.", example = "2026-08")
          @RequestParam(required = false)
          @DateTimeFormat(pattern = "yyyy-MM")
          YearMonth month,
      @Parameter(description = "이벤트 종류를 좁힌다. 미터에 없는 값도 저장돼 있으면 그대로 조회된다.")
          @RequestParam(name = "event_type", required = false)
          String eventType) {
    YearMonth target = month == null ? UsageAggregationService.currentMonth() : month;
    return queryService.query(organizationId, customerId, target, eventType, page, size);
  }
}
