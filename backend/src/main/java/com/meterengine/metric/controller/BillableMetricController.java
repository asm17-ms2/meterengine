package com.meterengine.metric.controller;

import com.meterengine.global.error.ErrorResponse;
import com.meterengine.global.validation.StorableText;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/billable-metrics")
public class BillableMetricController {

  private final BillableMetricService billableMetricService;

  BillableMetricController(BillableMetricService billableMetricService) {
    this.billableMetricService = billableMetricService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "집계 미터 등록",
      description =
          """
          미터를 등록한다. type이 미터의 event_type과 같은 사용량 이벤트가 이 미터의 집계 대상이다.
          aggregation은 sum만 받는다. sum은 이벤트 properties에서 target_property 키의 값을 합산하므로
          target_property가 필수다.
          code는 도입사 안에서 유일해야 하고 등록 뒤 바꿀 수 없다.
          event_type과 target_property의 조합도 도입사 안에서 유일해야 한다.
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
            code=validation_error: code, name, event_type, aggregation 중 빈 필드가 있거나, 문자열 필드에 NUL 문자나 짝이 없는 UTF-16 서로게이트가 있거나, X-Organization-Id가 없거나 UUID가 아니다.
            code=invalid_billable_metric: aggregation이 sum이 아니거나, sum인데 target_property가 없다. 어느 필드가 왜 거절됐는지는 errors에 있다.
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
            code=billable_metric_event_type_target_property_already_exists: event_type과 target_property가 같은 미터가 이미 있다.
            """)
  })
  public BillableMetricResponse createBillableMetric(
      @Parameter(description = "도입사 ID. 인증이 붙기 전까지 쓰는 임시 헤더다.") @RequestHeader("X-Organization-Id")
          UUID organizationId,
      @Valid @RequestBody CreateBillableMetricRequest request) {
    return billableMetricService.create(organizationId, request);
  }

  @PutMapping("/{code}")
  @Operation(
      summary = "집계 미터 수정",
      description =
          """
          미터를 요청 값으로 덮어쓰고 바뀐 미터를 돌려준다. code는 바꿀 수 없다.
          event_type이나 target_property를 바꾸면 사용량은 새 기준으로 다시 집계된다.
          옛 기준으로 집계된 이벤트가 한 건이라도 있으면 event_type과 target_property는 바꿀 수 없고 409다.
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
            code=validation_error: name, event_type, aggregation 중 빈 필드가 있거나, 문자열 필드나 경로의 code에 NUL 문자나 짝이 없는 UTF-16 서로게이트가 있거나, X-Organization-Id가 없거나 UUID가 아니다.
            code=invalid_billable_metric: aggregation이 sum이 아니거나, sum인데 target_property가 없다. 어느 필드가 왜 거절됐는지는 errors에 있다.
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
            code=billable_metric_has_events: 집계된 이벤트가 있어 event_type과 target_property를 바꿀 수 없다.
            code=billable_metric_event_type_target_property_already_exists: 바꾸려는 event_type과 target_property의 미터가 이미 있다.
            """)
  })
  public BillableMetricResponse updateBillableMetric(
      @Parameter(description = "도입사 ID. 인증이 붙기 전까지 쓰는 임시 헤더다.") @RequestHeader("X-Organization-Id")
          UUID organizationId,
      @Parameter(description = "고칠 미터의 code.") @PathVariable @StorableText String code,
      @Valid @RequestBody UpdateBillableMetricRequest request) {
    return billableMetricService.update(organizationId, code, request);
  }

  @DeleteMapping("/{code}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      summary = "집계 미터 삭제",
      description =
          """
          미터를 지운다. 지운 미터는 되돌릴 수 없다.
          집계된 이벤트가 한 건이라도 있거나 가격 정책이 붙은 미터는 지울 수 없고 409다.
          이미 지운 미터를 다시 지우면 404다.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "지웠다. 본문 없음"),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description =
            "code=validation_error: 경로의 code에 NUL 문자나 짝이 없는 UTF-16 서로게이트가 있거나, X-Organization-Id가 없거나 UUID가 아니다."),
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
            code=billable_metric_has_events: 집계된 이벤트가 있어 지울 수 없다.
            code=billable_metric_has_price_policy: 가격 정책이 붙어 있어 지울 수 없다.
            """)
  })
  public void deleteBillableMetric(
      @Parameter(description = "도입사 ID. 인증이 붙기 전까지 쓰는 임시 헤더다.") @RequestHeader("X-Organization-Id")
          UUID organizationId,
      @Parameter(description = "지울 미터의 code.") @PathVariable @StorableText String code) {
    billableMetricService.delete(organizationId, code);
  }

  @GetMapping
  @Operation(
      summary = "미터 목록 조회",
      description =
          """
          등록한 미터를 code 오름차순으로 전부 돌려준다.
          X-Organization-Id가 등록되지 않은 도입사여도 오류가 아니라 빈 배열이다.
          페이지를 나누지 않는다.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "미터 목록. 미터가 없으면 billable_metrics가 빈 배열이다"),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=validation_error: X-Organization-Id가 없거나 UUID가 아니다")
  })
  public ListBillableMetricsResponse listBillableMetrics(
      @Parameter(description = "도입사 ID. 인증이 붙기 전까지 쓰는 임시 헤더다.") @RequestHeader("X-Organization-Id")
          UUID organizationId) {
    return billableMetricService.list(organizationId);
  }
}
