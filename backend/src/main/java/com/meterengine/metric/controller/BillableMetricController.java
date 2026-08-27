package com.meterengine.metric.controller;

import com.meterengine.ProblemResponse;
import com.meterengine.metric.dto.BillableMetricListResponse;
import com.meterengine.metric.dto.BillableMetricResponse;
import com.meterengine.metric.dto.SaveBillableMetricRequest;
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
@RequestMapping("/v1/metrics")
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
          집계 미터를 등록한다. event_type이 같은 사용량 이벤트가 이 미터의 집계 대상이 된다.
          집계 함수는 SUM만 받는다. SUM은 이벤트 properties에서 target_property 키의 값을 합산하므로
          target_property가 필수다.
          코드는 도입사 안에서 유일하고, 등록 뒤 바꿀 수 없다.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "등록된 미터. 반영 결과를 확인하는 창구다"),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemResponse.class)),
        description =
            """
            code=validation_error: code, name, event_type, aggregation 중 빈 필드가 있거나, X-Organization-Id가 없거나 UUID가 아니다.
            code=invalid_billable_metric: aggregation이 SUM이 아니거나, SUM인데 target_property가 없다. 사유는 detail에 있다.
            code=unknown_organization: X-Organization-Id가 등록된 도입사가 아니다.
            """),
    @ApiResponse(
        responseCode = "409",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemResponse.class)),
        description = "code=metric_already_exists: 같은 코드의 미터가 이미 있다. 다른 코드를 써야 한다")
  })
  public BillableMetricResponse registerMetric(
      @Parameter(description = "도입사 ID. Bearer 인증으로 대체될 임시 헤더다.")
          @RequestHeader("X-Organization-Id")
          UUID organizationId,
      @Valid @RequestBody SaveBillableMetricRequest request) {
    return billableMetricService.register(organizationId, request);
  }

  @GetMapping
  @Operation(
      summary = "미터 목록 조회",
      description =
          """
          이 도입사의 미터를 code 오름차순으로 전부 돌려준다.
          미터가 없거나 등록되지 않은 도입사면 metrics가 빈 배열이다.
          페이지를 나누지 않는다. 이 도입사의 전부가 응답의 정의다.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "미터 목록. 미터가 없으면 metrics가 빈 배열이다"),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemResponse.class)),
        description = "code=validation_error: X-Organization-Id가 없거나 UUID가 아니다")
  })
  public BillableMetricListResponse listMetrics(
      @Parameter(description = "도입사 ID. Bearer 인증으로 대체될 임시 헤더다.")
          @RequestHeader("X-Organization-Id")
          UUID organizationId) {
    return billableMetricService.list(organizationId);
  }

  @PutMapping("/{code}")
  @Operation(
      summary = "미터 수정",
      description =
          """
          미터를 덮어쓰기로 고치고 갱신된 미터를 돌려준다. 단건 조회가 없으므로 이 응답이 반영 결과를 확인하는 창구다.
          code는 URL이 정하고 본문에서는 받지 않는다. 등록 뒤 바꿀 수 없어서다. 코드를 바꾸려면 지우고 다시 등록한다.
          집계 함수는 등록과 같은 규칙으로 SUM만 받고, SUM이면 target_property가 필수다.
          이벤트가 잡히는 집계 기준(event_type, target_property)은 바꿀 수 없다. 과거 사용량이 다시 해석되기 때문이다.
          바꾸려는 집계 기준에 이미 이벤트가 잡혀 있어도 같은 이유로 거절한다.
          이름만 고치는 것은 이벤트가 있어도 된다.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "갱신된 미터"),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemResponse.class)),
        description =
            """
            code=validation_error: name, event_type, aggregation 중 빈 필드가 있거나, X-Organization-Id가 없거나 UUID가 아니다.
            code=invalid_billable_metric: aggregation이 SUM이 아니거나, SUM인데 target_property가 없다. 사유는 detail에 있다.
            """),
    @ApiResponse(
        responseCode = "404",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemResponse.class)),
        description = "code=metric_not_found: 그런 미터가 없거나 다른 도입사 소속이다"),
    @ApiResponse(
        responseCode = "409",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemResponse.class)),
        description = "code=metric_basis_has_events: 지금 기준이나 바꾸려는 기준에 이벤트가 잡혀 있다. 이름은 고칠 수 있다")
  })
  public BillableMetricResponse updateMetric(
      @Parameter(description = "도입사 ID. Bearer 인증으로 대체될 임시 헤더다.")
          @RequestHeader("X-Organization-Id")
          UUID organizationId,
      @Parameter(description = "고칠 미터의 code.") @PathVariable String code,
      @Valid @RequestBody UpdateBillableMetricRequest request) {
    return billableMetricService.update(organizationId, code, request);
  }
}
