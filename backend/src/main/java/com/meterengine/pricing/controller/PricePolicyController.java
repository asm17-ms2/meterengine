package com.meterengine.pricing.controller;

import com.meterengine.ProblemResponse;
import com.meterengine.pricing.dto.PricePolicyListResponse;
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
import org.springframework.web.bind.annotation.GetMapping;
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

  @GetMapping("/v1/price-policies")
  @Operation(
      summary = "가격 정책 목록 조회",
      description =
          """
          이 도입사의 미터를 code 오름차순으로 전부 돌려주고, 미터마다 등록된 가격 정책을 함께 싣는다.
          정책이 아직 없는 미터는 dimension_properties가 null이다. 빈 배열은 정책이 있는 무차원 미터라는 뜻이라 서로 다르다.
          화면이 정책을 붙일 미터를 고르려면 정책 없는 미터도 보여야 해서 함께 싣는다.
          미터가 없거나 등록되지 않은 도입사면 price_policies가 빈 배열이다.
          미터의 이름과 집계 기준은 싣지 않는다. 미터 자체의 조회는 미터 API의 몫이다.
          단가는 무차원 조합('{}')에 붙은 기본 단가 하나만 싣는다. 조합별 단가는 다차원 계산이 붙을 때 함께 연다.
          수량을 싣지 않으므로 이 응답만으로는 금액을 계산할 수 없다.
          페이지를 나누지 않는다. 이 도입사의 전부가 응답의 정의다.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "미터별 가격 정책. 미터가 없으면 price_policies가 빈 배열이다"),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemResponse.class)),
        description = "code=validation_error: X-Organization-Id가 없거나 UUID가 아니다")
  })
  public PricePolicyListResponse listPricePolicies(
      @Parameter(description = "도입사 ID. Bearer 인증이 붙으면 대체될 임시 헤더다.")
          @RequestHeader("X-Organization-Id")
          UUID organizationId) {
    return pricePolicyService.list(organizationId);
  }

  @PostMapping("/v1/billable-metrics/{code}/price-policy")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "가격 정책 등록",
      description =
          """
          미터에 가격 정책(가격을 가르는 속성 키의 선언)을 등록한다.
          무차원 미터면 dimension_properties를 빈 배열로 보낸다.
          단가는 이 API가 받지 않고 별도의 단가 API가 등록한다.
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
      @Parameter(description = "도입사 ID. Bearer 인증이 붙으면 대체될 임시 헤더다.")
          @RequestHeader("X-Organization-Id")
          UUID organizationId,
      @Parameter(description = "정책을 붙일 미터의 code.") @PathVariable String code,
      @Valid @RequestBody SavePricePolicyRequest request) {
    return pricePolicyService.register(organizationId, code, request);
  }
}
