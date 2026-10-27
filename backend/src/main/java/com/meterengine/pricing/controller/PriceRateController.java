package com.meterengine.pricing.controller;

import com.meterengine.global.error.ErrorResponse;
import com.meterengine.global.security.AdminAccess;
import com.meterengine.global.security.CurrentOrganizationId;
import com.meterengine.global.security.ReadAccess;
import com.meterengine.global.validation.StorableText;
import com.meterengine.pricing.dto.CreatePriceRateRequest;
import com.meterengine.pricing.dto.ListPriceRatesResponse;
import com.meterengine.pricing.dto.PriceRateResponse;
import com.meterengine.pricing.service.PriceRateService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/billable-metrics/{code}/price-rates")
public class PriceRateController {

  private final PriceRateService priceRateService;

  PriceRateController(PriceRateService priceRateService) {
    this.priceRateService = priceRateService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @AdminAccess
  @Operation(
      summary = "단가 등록",
      description =
          """
          미터에 속성 값 조합별 단가를 넣는다. dimension_values의 키 집합은 미터의 dimension_properties와 같아야 하고,
          키가 없는 미터는 빈 객체 하나만 받는다. 청구는 조합이 정확히 같은 단가만 쓰고 폴백은 없다.
          같은 시작 월과 고객 범위의 버전은 갱신한다. 지난달 이전 버전은 바꿀 수 없다.
          종료 월은 포함하지 않으며 고객 단가가 공통 단가보다 우선한다.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "저장된 단가. 덮어쓴 경우에도 201이다"),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description =
            """
            code=validation_error: dimension_values나 unit_price가 없거나, unit_price가 음수거나, 경로의 code에 NUL 문자나 짝이 없는 UTF-16 서로게이트가 있다.
            code=invalid_price_rate: dimension_values의 키 집합이 미터의 dimension_properties와 다르거나, 값이 문자열, 숫자, 불리언이 아니다. 어느 필드가 왜 거절됐는지는 errors에 있다.
            """),
    @ApiResponse(
        responseCode = "404",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=billable_metric_not_found: 그런 code의 미터가 없다")
  })
  public PriceRateResponse createPriceRate(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(description = "단가를 넣을 미터의 code.") @PathVariable @StorableText String code,
      @Valid @RequestBody CreatePriceRateRequest request) {
    return priceRateService.create(organizationId, code, request);
  }

  @DeleteMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @AdminAccess
  @Operation(
      summary = "단가 삭제",
      description =
          """
          미터에서 속성 값 조합 하나의 단가를 지운다. dimension_values는 JSON 객체 문자열이고,
          빈 객체 {}는 기본 단가다. 같은 조합인지는 키 순서와 상관없이 본다.
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
            "code=validation_error: dimension_values가 없거나 JSON 객체가 아니거나, 경로의 code에 NUL 문자나 짝이 없는"
                + " UTF-16 서로게이트가 있다."),
    @ApiResponse(
        responseCode = "404",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=price_rate_not_found: 그 미터에 그 조합의 단가가 없다")
  })
  public void deletePriceRate(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(description = "단가를 지울 미터의 code.") @PathVariable @StorableText String code,
      @Parameter(description = "지울 조합. JSON 객체 문자열이다.")
          @RequestParam("dimension_values")
          @StorableText
          String dimensionValues) {
    priceRateService.delete(organizationId, code, dimensionValues);
  }

  @GetMapping
  @ReadAccess
  @Operation(summary = "미터 단가 이력 조회")
  public ListPriceRatesResponse listPriceRates(
      @CurrentOrganizationId UUID organizationId,
      @PathVariable @StorableText String code,
      @RequestParam(name = "customer_id", required = false) UUID customerId) {
    return priceRateService.list(organizationId, code, customerId);
  }

  @DeleteMapping("/{id}")
  @AdminAccess
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "단가 버전 삭제", description = "현재 월 또는 미래 월에 시작하는 버전만 삭제한다. 지난달 이전 단가는 보존한다.")
  public void deletePriceRateVersion(
      @CurrentOrganizationId UUID organizationId,
      @PathVariable @StorableText String code,
      @PathVariable UUID id) {
    priceRateService.delete(organizationId, code, id);
  }
}
