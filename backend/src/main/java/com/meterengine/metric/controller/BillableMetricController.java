package com.meterengine.metric.controller;

import com.meterengine.global.error.ErrorResponse;
import com.meterengine.global.security.AdminAccess;
import com.meterengine.global.security.CurrentOrganizationId;
import com.meterengine.global.security.EnvironmentReady;
import com.meterengine.global.security.ReadAccess;
import com.meterengine.global.validation.StorableText;
import com.meterengine.global.validation.VersionPrecondition;
import com.meterengine.idempotency.service.IdempotencyService;
import com.meterengine.metric.dto.BillableMetricResponse;
import com.meterengine.metric.dto.CreateBillableMetricRequest;
import com.meterengine.metric.dto.ListBillableMetricsResponse;
import com.meterengine.metric.dto.UpdateBillableMetricRequest;
import com.meterengine.metric.service.BillableMetricService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@EnvironmentReady
@RequestMapping("/v1/billable-metrics")
public class BillableMetricController {

  private final BillableMetricService billableMetricService;
  private final IdempotencyService idempotencyService;

  BillableMetricController(
      BillableMetricService billableMetricService, IdempotencyService idempotencyService) {
    this.billableMetricService = billableMetricService;
    this.idempotencyService = idempotencyService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @AdminAccess
  @Operation(
      summary = "집계 미터 등록",
      description =
          """
          미터를 등록한다. type이 미터의 event_type과 같은 사용량 이벤트가 이 미터의 집계 대상이다.
          aggregation은 sum, count, count_distinct, min, max, avg, first, last, median, percentile을 받으며 대소문자를 구분하지 않는다.
          count는 전체 이벤트 수이며 target_property를 지정하지 않는다. count_distinct는 대상 속성의 숫자, 문자열, 불리언 고유값 수다.
          나머지는 숫자 속성만 집계한다. first와 last는 occurred_at, transaction_id 순서다. median은 50백분위, percentile은 0~100의 percentile이 필수다.
          집계량을 unit_size(기본 1)로 나눈 뒤 rounding_mode(none, ceil, floor, round)를 rounding_precision(0~12, 기본 0)에 적용한다.
          round는 사사오입이며 none은 유한소수를 그대로 보존하고 순환소수는 유효숫자와 소수 자릿수를 각각 최소 18자리로 보존한다. code는 도입사 안에서 유일하고 등록 뒤 바꿀 수 없다.
          같은 이벤트와 속성에 여러 미터를 등록할 수 있다. 각 미터에 단가를 설정하면 각각 청구한다.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "등록된 미터"),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description =
            """
            code=validation_error: code, name, event_type, aggregation 중 빈 필드가 있거나, 문자열 필드에 NUL 문자나 짝이 없는 UTF-16 서로게이트가 있다.
            code=invalid_billable_metric: 집계 방식과 대상 속성, 단위, 반올림, 백분위 설정이 맞지 않거나 dimension_properties에 중복이나 빈 키가 있다. 어느 필드가 왜 거절됐는지는 errors에 있다.
            """),
    @ApiResponse(
        responseCode = "409",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description =
            """
            code=billable_metric_already_exists: 같은 code의 미터가 이미 있다.
            """)
  })
  public ResponseEntity<BillableMetricResponse> createBillableMetric(
      @CurrentOrganizationId UUID organizationId,
      @Valid @RequestBody CreateBillableMetricRequest request,
      @RequestHeader(name = "Idempotency-Key", required = false) @StorableText
          String idempotencyKey) {
    return idempotencyService.execute(
        organizationId,
        "metric.create",
        idempotencyKey,
        request,
        BillableMetricResponse.class,
        () ->
            ResponseEntity.status(HttpStatus.CREATED)
                .body(billableMetricService.create(organizationId, request)));
  }

  @PutMapping("/{code}")
  @AdminAccess
  @Operation(
      summary = "집계 미터 수정",
      description =
          """
          미터를 요청 값으로 덮어쓰고 바뀐 미터를 돌려준다. code는 바꿀 수 없다.
          현재 event_type의 이벤트가 한 건이라도 있으면 이름만 바꿀 수 있다. 집계, 대상 속성, 차원, 단위, 반올림, 백분위 변경은 409다.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "바뀐 미터"),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description =
            """
            code=validation_error: name, event_type, aggregation 중 빈 필드가 있거나, 문자열 필드나 경로의 code에 NUL 문자나 짝이 없는 UTF-16 서로게이트가 있다.
            code=invalid_billable_metric: 집계 방식과 대상 속성, 단위, 반올림, 백분위 설정이 맞지 않거나 dimension_properties에 중복이나 빈 키가 있다. 어느 필드가 왜 거절됐는지는 errors에 있다.
            """),
    @ApiResponse(
        responseCode = "404",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=billable_metric_not_found: 그런 code의 미터가 없다"),
    @ApiResponse(
        responseCode = "409",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description =
            """
            code=billable_metric_has_events: 이벤트가 있어 계산 정의를 바꿀 수 없다.
            code=resource_archived: 보관한 미터다. 복원한 뒤 고친다.
            """)
  })
  public BillableMetricResponse updateBillableMetric(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(description = "고칠 미터의 code.") @PathVariable @StorableText String code,
      @Valid @RequestBody UpdateBillableMetricRequest request,
      @RequestHeader(name = "If-Match", required = false) @StorableText String ifMatch) {
    return billableMetricService.update(
        organizationId, code, request, VersionPrecondition.parse(ifMatch));
  }

  @PatchMapping("/{code}")
  @AdminAccess
  @Operation(
      summary = "버전을 확인한 미터 수정",
      description =
          "조회한 version을 큰따옴표로 감싼 If-Match 헤더가 필수다. 다른 요청이 먼저 수정했으면 409다. PUT과 같은 전체 필드를 보낸다.")
  public BillableMetricResponse updateConditionalBillableMetric(
      @CurrentOrganizationId UUID organizationId,
      @PathVariable @StorableText String code,
      @Valid @RequestBody UpdateBillableMetricRequest request,
      @RequestHeader("If-Match") @StorableText String ifMatch) {
    return billableMetricService.update(
        organizationId, code, request, VersionPrecondition.parse(ifMatch));
  }

  @GetMapping
  @ReadAccess
  @Operation(summary = "미터 목록 조회")
  public ListBillableMetricsResponse listBillableMetrics(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(
              description = "보관 상태. 생략하면 active",
              schema =
                  @Schema(
                      allowableValues = {"active", "archived", "all"},
                      defaultValue = "active"))
          @org.springframework.web.bind.annotation.RequestParam(defaultValue = "active")
          @StorableText
          String status) {
    return billableMetricService.list(organizationId, status);
  }

  @GetMapping("/{code}")
  @ReadAccess
  @Operation(summary = "미터 상세 조회")
  public ResponseEntity<BillableMetricResponse> getBillableMetric(
      @CurrentOrganizationId UUID organizationId, @PathVariable @StorableText String code) {
    BillableMetricResponse metric = billableMetricService.get(organizationId, code);
    return ResponseEntity.ok().eTag(Long.toString(metric.version())).body(metric);
  }

  @PostMapping("/{code}/restorations")
  @AdminAccess
  @Operation(summary = "미터 복원", description = "다음 달부터 집계를 재개한다. 보관한 기간을 소급 집계하지 않는다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "복원한 미터. 보관하지 않은 미터면 그대로 돌려준다"),
    @ApiResponse(
        responseCode = "404",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=billable_metric_not_found: 그런 code의 미터가 없다")
  })
  public BillableMetricResponse restoreBillableMetric(
      @CurrentOrganizationId UUID organizationId, @PathVariable @StorableText String code) {
    return billableMetricService.restore(organizationId, code);
  }

  @DeleteMapping("/{code}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @AdminAccess
  @Operation(
      summary = "미터 보관",
      description = "현재 월까지 집계를 유지하고 다음 달부터 중단한다. 이벤트와 단가는 유지한다. 반복 요청은 204다.")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "보관했다. 본문 없음"),
    @ApiResponse(
        responseCode = "404",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=billable_metric_not_found: 그런 code의 미터가 없다")
  })
  public void deleteBillableMetric(
      @CurrentOrganizationId UUID organizationId, @PathVariable @StorableText String code) {
    billableMetricService.delete(organizationId, code);
  }
}
