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
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "저장된 요금제. 같은 고객 범위와 시작 월의 버전을 덮어쓴 경우에도 201이다"),
    @ApiResponse(
        responseCode = "409",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description =
            """
            code=price_plan_history_immutable: effective_from이 이번 달보다 앞이다.
            code=customer_archived: customer_id가 가리키는 고객을 보관했다.
            code=archive_restoration_conflict: 같은 고객 범위와 시작 월의 버전을 보관했지만 그 버전이 이번 달에는 아직 적용된다.
            """)
  })
  public PricePlanResponse createPricePlan(
      @CurrentOrganizationId UUID organizationId,
      @Valid @RequestBody CreatePricePlanRequest request) {
    return pricePlanService.create(organizationId, request);
  }

  @GetMapping
  @ReadAccess
  @Operation(summary = "요금제 이력 조회")
  public ListPricePlansResponse listPricePlans(
      @CurrentOrganizationId UUID organizationId,
      @RequestParam(required = false) @FourDigitYear @DateTimeFormat(pattern = "yyyy-MM")
          YearMonth month,
      @RequestParam(name = "customer_id", required = false) UUID customerId,
      @Parameter(
              description = "보관 상태. 생략하면 active",
              schema =
                  @Schema(
                      allowableValues = {"active", "archived", "all"},
                      defaultValue = "active"))
          @RequestParam(defaultValue = "active")
          @StorableText
          String status) {
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
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "복원한 요금제 버전. 보관하지 않은 버전이면 그대로 돌려준다"),
    @ApiResponse(
        responseCode = "404",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=price_plan_not_found: 그런 id의 요금제 버전이 없다"),
    @ApiResponse(
        responseCode = "409",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description =
            "code=archive_restoration_conflict: 같은 고객 범위와 시작 월의 보관하지 않은 버전이 있거나, 보관 효력이 이번 달이나 그 전에 시작됐다")
  })
  public PricePlanResponse restorePricePlan(
      @CurrentOrganizationId UUID organizationId, @PathVariable UUID id) {
    return pricePlanService.restore(organizationId, id);
  }
}
