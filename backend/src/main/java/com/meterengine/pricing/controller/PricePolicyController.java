package com.meterengine.pricing.controller;

import com.meterengine.global.error.ErrorResponse;
import com.meterengine.pricing.dto.CreatePricePolicyRequest;
import com.meterengine.pricing.dto.PricePolicyResponse;
import com.meterengine.pricing.service.PricePolicyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PricePolicyController {

  private final PricePolicyService pricePolicyService;

  PricePolicyController(PricePolicyService pricePolicyService) {
    this.pricePolicyService = pricePolicyService;
  }

  @PostMapping("/v1/billable-metrics/{code}/price-policy")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "가격 정책 등록",
      description =
          """
          미터의 가격 정책을 등록한다. 가격 정책은 단가를 가르는 이벤트 속성 키를 선언한 것이다.
          속성에 따라 단가가 갈리지 않으면 dimension_properties를 빈 배열로 보낸다.
          단가는 이 API로 등록하지 않는다. 단가가 없는 미터는 청구 예정액에 라인이 나오지 않는다.
          미터 하나에 가격 정책은 하나다. 이미 있으면 409다.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "등록된 가격 정책"),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description =
            """
            code=validation_error: dimension_properties가 없거나, X-Organization-Id가 없거나 UUID가 아니다.
            code=invalid_price_policy: dimension_properties에 중복된 키나 빈 키가 있다. 어느 필드가 왜 거절됐는지는 errors에 있다.
            """),
    @ApiResponse(
        responseCode = "404",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=billable_metric_not_found: 그런 미터가 없다"),
    @ApiResponse(
        responseCode = "409",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=price_policy_already_exists: 이 미터에 가격 정책이 이미 있다")
  })
  public PricePolicyResponse createPricePolicy(
      @Parameter(description = "도입사 ID. 인증이 붙기 전까지 쓰는 임시 헤더다.") @RequestHeader("X-Organization-Id")
          UUID organizationId,
      @Parameter(description = "가격 정책을 등록할 미터의 code.") @PathVariable String code,
      @Valid @RequestBody CreatePricePolicyRequest request) {
    return pricePolicyService.create(organizationId, code, request);
  }
}
