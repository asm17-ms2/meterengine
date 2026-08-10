package com.meterengine.usage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사용량 이벤트 수집 (MS2-130).
 *
 * <p><b>X-Organization-Id는 임시물이다.</b> 지금은 요청이 도입사를 자칭하기만 하면 통과하므로 누구나 아무 도입사를 사칭할 수 있다. MS2-126이
 * Bearer API 키 인증을 붙이면서 이 파라미터를 인증 주체에서 꺼내는 형태로 바꾼다. 그때 헤더 누락은 400이 아니라 401이 된다.
 *
 * <p>필터를 미리 만들어 두지 않은 이유: MS2-126은 Spring Security로 들어올 예정이라 컨트롤러가 SecurityContext에서 도입사를 꺼내게 된다.
 * 어느 쪽이든 이 시그니처는 바뀌므로, 지금 필터를 두면 나중에 버릴 코드만 늘어난다.
 */
@RestController
@RequestMapping("/v1/events")
public class UsageEventController {

  private final UsageEventIngestService ingestService;

  UsageEventController(UsageEventIngestService ingestService) {
    this.ingestService = ingestService;
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
}
