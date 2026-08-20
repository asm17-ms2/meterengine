package com.meterengine.pricing.controller;

import com.meterengine.ProblemResponse;
import com.meterengine.pricing.dto.PricePolicyResponse;
import com.meterengine.pricing.dto.SavePricePolicyRequest;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 미터별 가격 정책 등록 (MS2-157).
 *
 * <p>이 API가 생기기 전까지 가격을 넣는 통로는 시드 스크립트뿐이었다. 청구 예정액이 단가를 전제로 도는데 그 단가를 화면에서 만들 수 없었다.
 *
 * <p><b>경로가 미터의 하위 리소스다.</b> 미터당 정책이 1개라는 것이 price_policy PK의 불변식이라, 정책은 독립 컬렉션이 아니라 미터에 하나 붙는 단수
 * 리소스다.
 *
 * <p><b>X-Organization-Id는 임시물이다.</b> MS2-126이 Bearer 인증으로 바꾼다 (고객 API와 같은 사정).
 *
 * <p><b>조회를 만들지 않는다.</b> 등록 응답이 저장된 정책 전체를 담아 반영 결과를 확인하는 창구가 된다. 조회는 FE 관리 화면(MS2-156)의 필요 형태가 정해진
 * 뒤 MS2-176이 만든다.
 */
@RestController
@RequestMapping("/v1/metrics/{metricCode}/price-policy")
public class PricePolicyController {

  private final PricePolicyService pricePolicyService;

  PricePolicyController(PricePolicyService pricePolicyService) {
    this.pricePolicyService = pricePolicyService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "가격 정책 등록",
      description =
          """
          미터에 가격 정책(가격을 가르는 속성 키의 선언)과 조합별 단가를 한 번에 등록한다.
          rates에는 dimension_values가 빈 객체({})인 기본 단가 행이 반드시 하나 있어야 한다.
          무차원 미터면 dimension_properties를 빈 배열로, rates를 기본 단가 한 행으로 보낸다.
          {} 아닌 행의 dimension_values 키 집합은 dimension_properties와 정확히 일치해야 한다.
          청구 예정액 계산은 조합별 단가가 도입되기 전까지(MS2-178) 기본 단가({}) 행만 사용한다.
          그때까지 조합별 단가는 저장만 되고 금액에 반영되지 않는다.
          미터당 정책은 1개다. 이미 있으면 409이고, 수정과 삭제 API는 아직 없다.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "등록된 정책 전체. 반영 결과를 확인하는 창구다"),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemResponse.class)),
        description =
            """
            code=validation_error: dimension_properties가 없거나, rates가 비었거나, unit_price가 없거나 음수거나,
            X-Organization-Id가 없거나 UUID가 아니다.
            code=invalid_price_policy: 기본 단가({}) 행이 없거나, 조합의 키 집합이 dimension_properties와 다르거나,
            같은 조합이 두 번 왔거나, 선언이나 조합에 빈 키/값이 있다. 사유는 detail에 있다.
            """),
    @ApiResponse(
        responseCode = "404",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemResponse.class)),
        description = "code=metric_not_found: 그런 미터가 없다. 다른 도입사 소속이거나 도입사가 미등록이어도 같다"),
    @ApiResponse(
        responseCode = "409",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemResponse.class)),
        description = "code=price_policy_already_exists: 이 미터에 정책이 이미 있다. 요청을 고쳐서 될 일이 아니다")
  })
  public PricePolicyResponse register(
      @Parameter(description = "도입사 ID. MS2-126의 Bearer 인증으로 대체될 임시 헤더다.")
          @RequestHeader("X-Organization-Id")
          UUID organizationId,
      @Parameter(description = "정책을 붙일 미터의 code.") @PathVariable String metricCode,
      @Valid @RequestBody SavePricePolicyRequest request) {
    return pricePolicyService.register(organizationId, metricCode, request);
  }
}
