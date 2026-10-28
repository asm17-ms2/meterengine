package com.meterengine.pricing.controller;

import com.meterengine.global.error.ErrorResponse;
import com.meterengine.global.security.AdminAccess;
import com.meterengine.global.security.CurrentOrganizationId;
import com.meterengine.global.security.ReadAccess;
import com.meterengine.global.validation.FourDigitYear;
import com.meterengine.global.validation.StorableText;
import com.meterengine.pricing.dto.CreatePricePlanRequest;
import com.meterengine.pricing.dto.ListPricePlansResponse;
import com.meterengine.pricing.dto.PricePlanResponse;
import com.meterengine.pricing.service.PricePlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import java.time.YearMonth;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/price-plans")
public class PricePlanController {
  private final PricePlanService pricePlanService;

  PricePlanController(PricePlanService pricePlanService) {
    this.pricePlanService = pricePlanService;
  }

  @PostMapping
  @AdminAccess
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "기본료와 최소 과금 요금제 등록",
      description = "사용료에 최소 과금을 적용한 뒤 기본료를 더한다. 수량이 없는 고객에게는 적용하지 않는다.")
  public PricePlanResponse createPricePlan(
      @CurrentOrganizationId UUID organizationId,
      @Valid @RequestBody CreatePricePlanRequest request) {
    return pricePlanService.create(organizationId, request);
  }

  @GetMapping
  @ReadAccess
  @Operation(
      summary = "요금제 이력 조회",
      description =
          """
          customer_id를 주면 그 고객의 요금제만, 생략하면 도입사 공통 요금제만 돌려준다. 고객 요금제는 customer_id로 따로 조회한다.
          month를 주면 그 달에 적용되는 버전만, 생략하면 모든 버전 이력을 돌려준다. 시작 월 내림차순이다.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "요금제 버전 목록. 조건에 맞는 것이 없으면 price_plans가 빈 배열이다"),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description =
            "code=validation_error: customer_id가 UUID가 아니거나, month가 yyyy-MM이 아니거나 0001-01부터 9999-12까지의 범위 밖이다"),
    @ApiResponse(
        responseCode = "404",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=customer_not_found: customer_id가 가리키는 고객이 이 도입사에 없다")
  })
  public ListPricePlansResponse listPricePlans(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(
              description = "이 달(yyyy-MM, KST)에 적용되는 버전만 고른다. 생략하면 모든 버전 이력이다.",
              example = "2026-08")
          @RequestParam(required = false)
          @FourDigitYear
          @DateTimeFormat(pattern = "yyyy-MM")
          YearMonth month,
      @Parameter(description = "이 고객의 요금제만 고른다. 생략하면 도입사 공통 요금제만 나온다.")
          @RequestParam(name = "customer_id", required = false)
          UUID customerId,
      @RequestParam(defaultValue = "active") @StorableText String status) {
    return pricePlanService.list(organizationId, month, customerId, status);
  }

  @DeleteMapping("/{id}")
  @AdminAccess
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "요금제 버전 보관", description = "현재 월 효력은 유지하고 다음 달부터 중단한다. 버전 이력은 유지한다.")
  public void deletePricePlan(@CurrentOrganizationId UUID organizationId, @PathVariable UUID id) {
    pricePlanService.delete(organizationId, id);
  }

  @PostMapping("/{id}/restorations")
  @AdminAccess
  @Operation(
      summary = "요금제 버전 복원",
      description = "보관 효력이 시작되기 전이고 대체 버전이 없을 때만 복원한다. 그 밖에는 새 버전을 등록한다.")
  public PricePlanResponse restorePricePlan(
      @CurrentOrganizationId UUID organizationId, @PathVariable UUID id) {
    return pricePlanService.restore(organizationId, id);
  }
}
