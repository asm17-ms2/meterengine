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
import io.swagger.v3.oas.annotations.headers.Header;
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
  private static final String INVALID_UPDATE =
      """
      code=validation_error: name, event_type, aggregation 가운데 빈 필드가 있거나, 문자열 필드나 경로의 code에 NUL 문자나 짝이 없는 UTF-16 서로게이트가 있습니다. If-Match가 큰따옴표로 감싼 version이 아니어도 이 코드이며 errors의 field는 If-Match입니다. PATCH에서 If-Match를 빼도 같습니다.
      code=invalid_billable_metric: 집계 함수와 대상 속성, 단위, 끝수 처리, 백분위 설정이 맞지 않거나 dimension_properties에 중복이나 빈 키가 있습니다. 어느 필드가 왜 거절됐는지는 errors에 있습니다.
      """;
  private static final String UPDATE_CONFLICT =
      """
      code=billable_metric_has_events: 이벤트가 있어 계산 정의를 바꿀 수 없습니다.
      code=resource_archived: 보관한 과금 미터입니다. 복원한 뒤 고칩니다.
      code=resource_version_conflict: If-Match의 version이 지금 version과 다릅니다. 다시 조회한 뒤 고칩니다.
      """;

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
      summary = "과금 미터 등록",
      description =
          """
          과금 미터를 등록합니다. type이 과금 미터의 event_type과 같은 사용량 이벤트가 이 과금 미터의 집계 대상입니다.
          aggregation(집계 함수)은 sum, count, count_distinct, min, max, avg, first, last, median, percentile을 받으며 대소문자를 구분하지 않습니다.
          count는 전체 이벤트 수이며 target_property를 지정하지 않습니다. count_distinct는 대상 속성의 숫자, 문자열, 불리언 고유값 수입니다.
          나머지는 숫자 속성만 집계합니다. first와 last는 occurred_at, transaction_id 순서입니다. median은 50백분위이고, percentile 집계 함수는 percentile 값(0~100)이 필수입니다.
          집계량을 unit_size(기본 1)로 나눈 뒤 rounding_mode(none, ceil, floor, round)의 끝수 처리를 rounding_precision(0~12, 기본 0) 자리에 적용합니다.
          round는 반올림(사사오입)입니다. none은 유한소수를 그대로 두고, 순환소수는 유효숫자와 소수 자릿수를 각각 최소 18자리로 둡니다.
          code(과금 미터 코드)는 같은 업무 환경 안에서 유일해야 하고 등록 뒤 바꿀 수 없습니다. search, options, export는 조회 경로와 겹쳐 과금 미터 코드로 쓸 수 없습니다.
          같은 이벤트와 속성에 여러 과금 미터를 등록할 수 있습니다. 각 과금 미터에 단가를 등록하면 각각 청구합니다.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "등록된 과금 미터",
        headers =
            @Header(
                name = "Idempotency-Replayed",
                description = "같은 Idempotency-Key의 최초 응답을 다시 돌려줄 때만 true로 붙습니다.",
                schema =
                    @Schema(
                        type = "string",
                        allowableValues = {"true"}))),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description =
            """
            code=validation_error: code, name, event_type, aggregation 가운데 빈 필드가 있거나, 문자열 필드에 NUL 문자나 짝이 없는 UTF-16 서로게이트가 있습니다. Idempotency-Key가 비었거나 255자를 넘어도 이 코드입니다.
            code=invalid_billable_metric: 집계 함수와 대상 속성, 단위, 끝수 처리, 백분위 설정이 맞지 않거나 dimension_properties에 중복이나 빈 키가 있거나, code가 조회 경로와 겹치는 search, options, export입니다. 어느 필드가 왜 거절됐는지는 errors에 있습니다.
            """),
    @ApiResponse(
        responseCode = "409",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description =
            """
            code=billable_metric_already_exists: 현재 업무 환경에 같은 code의 과금 미터가 이미 있습니다.
            code=idempotency_conflict: 같은 Idempotency-Key를 다른 요청 본문에 썼습니다.
            """)
  })
  public ResponseEntity<BillableMetricResponse> createBillableMetric(
      @CurrentOrganizationId UUID organizationId,
      @Valid @RequestBody CreateBillableMetricRequest request,
      @Parameter(
              description =
                  "1~255자 재시도 키입니다. 같은 조직과 업무 환경에서 같은 키와 같은 본문을 다시 보내면 최초 응답을 상태 코드까지 그대로 돌려줍니다. 최초 응답을 기한 없이 보관하므로 그 뒤에 고치거나 보관해도 다시 보낸 요청의 응답은 바뀌지 않습니다.")
          @RequestHeader(name = "Idempotency-Key", required = false)
          @StorableText
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
      summary = "과금 미터 수정",
      description =
          """
          과금 미터를 요청 값으로 덮어쓰고 바뀐 과금 미터를 돌려줍니다. code는 바꿀 수 없습니다.
          지금 event_type의 이벤트가 한 건이라도 있으면 이름만 바꿀 수 있습니다. 이때 집계 함수, 대상 속성, 구분 키, 단위, 끝수 처리, 백분위를 바꾸면 409입니다.
          If-Match는 선택입니다. 보내면 version을 확인하고, 빼면 확인 없이 덮어씁니다. 다른 요청이 고친 값을 덮어쓰지 않으려면 If-Match를 보내거나 PATCH /v1/billable-metrics/{code}를 씁니다.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "바뀐 과금 미터"),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = INVALID_UPDATE),
    @ApiResponse(
        responseCode = "404",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=billable_metric_not_found: 그런 code의 과금 미터가 없습니다."),
    @ApiResponse(
        responseCode = "409",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = UPDATE_CONFLICT)
  })
  public BillableMetricResponse updateBillableMetric(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(description = "고칠 과금 미터의 과금 미터 코드") @PathVariable @StorableText String code,
      @Valid @RequestBody UpdateBillableMetricRequest request,
      @Parameter(
              description =
                  "선택 입력입니다. 과금 미터 조회 응답의 ETag처럼 version을 큰따옴표로 감싼 값(예: \"3\")입니다. 보내면 지금 version과 다를 때 409로 거절하고, 빼면 version을 확인하지 않습니다.")
          @RequestHeader(name = "If-Match", required = false)
          @StorableText
          String ifMatch) {
    return billableMetricService.update(
        organizationId, code, request, VersionPrecondition.parse(ifMatch));
  }

  @PatchMapping("/{code}")
  @AdminAccess
  @Operation(
      summary = "버전을 확인한 과금 미터 수정",
      description =
          "과금 미터를 보낸 값으로 통째로 바꿉니다. PUT과 같지만 If-Match가 반드시 있어야 합니다. 조회한 version을 큰따옴표로 감싸 If-Match 헤더에 보냅니다."
              + " 그 사이 다른 요청이 과금 미터를 고쳐 version이 다르면 409입니다."
              + " 다른 요청이 고친 값을 덮어쓰지 않으려면 이 경로를 쓰고, version 확인 없이 덮어써도 되면 PUT /v1/billable-metrics/{code}를 씁니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "바뀐 과금 미터"),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = INVALID_UPDATE),
    @ApiResponse(
        responseCode = "404",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=billable_metric_not_found: 그런 code의 과금 미터가 없습니다."),
    @ApiResponse(
        responseCode = "409",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = UPDATE_CONFLICT)
  })
  public BillableMetricResponse updateConditionalBillableMetric(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(description = "고칠 과금 미터의 과금 미터 코드") @PathVariable @StorableText String code,
      @Valid @RequestBody UpdateBillableMetricRequest request,
      @Parameter(
              description =
                  "과금 미터 조회 응답의 ETag처럼 version을 큰따옴표로 감싼 값(예: \"3\")입니다. 지금 version과 다르면 409로 거절합니다.")
          @RequestHeader("If-Match")
          @StorableText
          String ifMatch) {
    return billableMetricService.update(
        organizationId, code, request, VersionPrecondition.parse(ifMatch));
  }

  @GetMapping
  @ReadAccess
  @Operation(
      summary = "과금 미터 목록 조회",
      description =
          "현재 업무 환경의 과금 미터를 과금 미터 코드 오름차순으로 모두 돌려줍니다. 페이지를 나누지 않습니다. 보관 상태로만 거르면 이 경로를 씁니다. 검색어, 여러 조건, 정렬, 페이지가 필요하면 GET /v1/billable-metrics/search를 씁니다.")
  public ListBillableMetricsResponse listBillableMetrics(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(
              description =
                  "보관 상태입니다. active는 보관하지 않은 과금 미터, archived는 보관한 과금 미터, all은 둘 다입니다. 생략하면 active입니다.",
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
  @Operation(
      summary = "과금 미터 조회",
      description =
          "과금 미터 하나를 돌려줍니다. 보관한 과금 미터도 나옵니다. 응답의 ETag 헤더에 version이 큰따옴표로 감싸여 실리므로, 그 값을 PATCH의 If-Match에 그대로 씁니다.")
  public ResponseEntity<BillableMetricResponse> getBillableMetric(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(description = "조회할 과금 미터의 과금 미터 코드") @PathVariable @StorableText String code) {
    BillableMetricResponse metric = billableMetricService.get(organizationId, code);
    return ResponseEntity.ok().eTag(Long.toString(metric.version())).body(metric);
  }

  @PostMapping("/{code}/restorations")
  @AdminAccess
  @Operation(summary = "과금 미터 복원", description = "다음 달부터 다시 집계합니다. 보관한 기간을 거슬러 집계하지 않습니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "복원한 과금 미터입니다. 보관하지 않은 과금 미터면 그대로 돌려줍니다."),
    @ApiResponse(
        responseCode = "404",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=billable_metric_not_found: 그런 code의 과금 미터가 없습니다.")
  })
  public BillableMetricResponse restoreBillableMetric(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(description = "복원할 과금 미터의 과금 미터 코드") @PathVariable @StorableText String code) {
    return billableMetricService.restore(organizationId, code);
  }

  @DeleteMapping("/{code}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @AdminAccess
  @Operation(
      summary = "과금 미터 보관",
      description = "이번 달까지 집계하고 다음 달부터 멈춥니다. 이벤트와 단가는 그대로 둡니다. 이미 보관한 과금 미터에 다시 보내도 204입니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "보관했습니다. 본문 없음"),
    @ApiResponse(
        responseCode = "404",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=billable_metric_not_found: 그런 code의 과금 미터가 없습니다.")
  })
  public void deleteBillableMetric(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(description = "보관할 과금 미터의 과금 미터 코드") @PathVariable @StorableText String code) {
    billableMetricService.delete(organizationId, code);
  }
}
