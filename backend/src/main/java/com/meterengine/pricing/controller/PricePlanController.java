package com.meterengine.pricing.controller;

import com.meterengine.global.security.AdminAccess;
import com.meterengine.global.security.CurrentOrganizationId;
import com.meterengine.global.security.ReadAccess;
import com.meterengine.global.validation.FourDigitYear;
import com.meterengine.pricing.dto.CreatePricePlanRequest;
import com.meterengine.pricing.dto.ListPricePlansResponse;
import com.meterengine.pricing.dto.PricePlanResponse;
import com.meterengine.pricing.service.PricePlanService;
import io.swagger.v3.oas.annotations.Operation;
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
  @Operation(summary = "요금제 이력 조회")
  public ListPricePlansResponse listPricePlans(
      @CurrentOrganizationId UUID organizationId,
      @RequestParam(required = false) @FourDigitYear @DateTimeFormat(pattern = "yyyy-MM")
          YearMonth month,
      @RequestParam(name = "customer_id", required = false) UUID customerId) {
    return pricePlanService.list(organizationId, month, customerId);
  }

  @DeleteMapping("/{id}")
  @AdminAccess
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "요금제 버전 삭제")
  public void deletePricePlan(@CurrentOrganizationId UUID organizationId, @PathVariable UUID id) {
    pricePlanService.delete(organizationId, id);
  }
}
