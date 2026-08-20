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
 * <p>이 API가 생기기 전까지 정책을 넣는 통로는 시드 스크립트뿐이었다. 단가는 정책과 등록 시점이 달라(유효 기간 등 자체 수명 예정) 여기서 받지 않고 MS2-177의
 * 단가 API가 따로 등록한다 (PR 43 리뷰 결정).
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
          미터에 가격 정책(가격을 가르는 속성 키의 선언)을 등록한다.
          무차원 미터면 dimension_properties를 빈 배열로 보낸다.
          단가는 이 API가 받지 않고 별도의 단가 API(MS2-177 예정)가 등록한다.
          단가가 아직 없는 미터는 청구 예정액 계산에서 라인이 제외된다.
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
            code=validation_error: dimension_properties가 없거나, X-Organization-Id가 없거나 UUID가 아니다.
            code=invalid_price_policy: 선언에 중복 키나 빈 키가 있다. 사유는 detail에 있다.
            """),
    @ApiResponse(
        responseCode = "404",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemResponse.class)),
        description = "code=metric_not_found: 그런 미터가 없다. 다른 도입사 소속이어도 같다"),
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
