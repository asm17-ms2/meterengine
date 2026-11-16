package com.meterengine.pricing.controller;

import com.meterengine.global.error.ErrorResponse;
import com.meterengine.global.security.AdminAccess;
import com.meterengine.global.security.CurrentOrganizationId;
import com.meterengine.global.security.EnvironmentReady;
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
@EnvironmentReady
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
          미터에 구분 값별 단가 버전을 등록합니다. dimension_values의 키 집합은 미터의 dimension_properties와 같아야 하고,
          구분 키가 없는 미터는 빈 객체 하나만 받습니다. 청구는 구분 값이 정확히 같은 단가만 쓰고 다른 단가로 대신하지 않습니다.
          같은 구분 값, 고객 범위, 시작 월의 버전이 있으면 그 버전을 고칩니다. 이번 달보다 앞에서 시작하는 버전은 등록하거나 고칠 수 없습니다.
          종료 월은 포함하지 않으며 고객 단가가 공통 단가보다 먼저 적용됩니다.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "저장한 단가 버전입니다. 기존 버전을 고친 경우에도 201입니다."),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description =
            """
            code=validation_error: dimension_values가 없거나, model과 unit_price, tiers가 맞지 않거나, 단가가 음수거나, effective_until이 시작 월보다 뒤가 아니거나, 경로의 code에 NUL 문자나 짝이 없는 UTF-16 서로게이트가 있습니다.
            code=invalid_price_rate: dimension_values의 키 집합이 미터의 dimension_properties와 다르거나, 값이 문자열, 숫자, 불리언이 아닙니다. 어느 필드가 왜 거절됐는지는 errors에 있습니다.
            """),
    @ApiResponse(
        responseCode = "404",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description =
            """
            code=billable_metric_not_found: 그런 code의 미터가 없습니다.
            code=customer_not_found: customer_id가 가리키는 고객이 없습니다.
            """),
    @ApiResponse(
        responseCode = "409",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description =
            """
            code=resource_archived: 보관한 미터입니다. 미터를 복원한 뒤 등록합니다.
            code=price_rate_history_immutable: effective_from이 이번 달보다 앞입니다.
            code=customer_archived: customer_id가 가리키는 고객을 보관했습니다.
            code=archive_restoration_conflict: 같은 구분 값, 고객 범위, 시작 월의 버전을 보관했지만 그 버전이 이번 달에는 아직 적용됩니다.
            """)
  })
  public PriceRateResponse createPriceRate(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(description = "단가를 등록할 미터의 미터 코드") @PathVariable @StorableText String code,
      @Valid @RequestBody CreatePriceRateRequest request) {
    return priceRateService.create(organizationId, code, request);
  }

  @DeleteMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @AdminAccess
  @Operation(
      summary = "구분 값 단가 보관",
      description =
          """
          미터에서 구분 값 하나의 공통 단가(customer_id 없음) 가운데 이번 달(KST)에 적용 중인
          버전을 보관하고 다음 달부터 멈춥니다. 이번 달 계산과 버전 이력, 미래 예약은 그대로 두고 고객 단가도
          건드리지 않습니다. dimension_values는 JSON 객체 문자열이고, 구분 키가 없는 미터는 빈 객체 {}를 보냅니다.
          같은 구분 값인지는 키 순서와 관계없이 봅니다.
          버전 ID 없이 구분 값으로 이번 달 공통 단가를 멈출 때 이 경로를 씁니다. 고객 단가나 아직 시작하지 않은 버전처럼
          버전 하나를 골라 보관하려면 DELETE /v1/billable-metrics/{code}/price-rates/{id}를 씁니다.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "이번 달 적용 중인 버전을 보관했습니다. 본문 없음"),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description =
            "code=validation_error: dimension_values가 없거나 JSON 객체가 아니거나, 경로의 code에 NUL 문자나 짝이 없는"
                + " UTF-16 서로게이트가 있습니다."),
    @ApiResponse(
        responseCode = "404",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=price_rate_not_found: 그 미터에 이번 달 적용 중인 그 구분 값의 공통 단가가 없습니다.")
  })
  public void deletePriceRate(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(description = "단가를 보관할 미터의 미터 코드") @PathVariable @StorableText String code,
      @Parameter(description = "보관할 공통 단가의 구분 값을 담은 JSON 객체 문자열")
          @RequestParam("dimension_values")
          @StorableText
          String dimensionValues) {
    priceRateService.delete(organizationId, code, dimensionValues);
  }

  @GetMapping
  @ReadAccess
  @Operation(
      summary = "미터 단가 이력 조회",
      description =
          "미터의 단가 버전을 시작 월 내림차순으로 돌려줍니다. customer_id를 주면 그 고객의 고객 단가만, 생략하면 공통 단가만 돌려줍니다. 페이지를 나누지 않습니다. 한 달에 적용되는 단가만 미터마다 보려면 GET /v1/billable-metric-prices를 씁니다.")
  public ListPriceRatesResponse listPriceRates(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(description = "단가 이력을 볼 미터의 미터 코드") @PathVariable @StorableText String code,
      @Parameter(description = "이 고객의 고객 단가만 돌려줍니다. 생략하면 공통 단가만 돌려줍니다.")
          @RequestParam(name = "customer_id", required = false)
          UUID customerId,
      @Parameter(
              description =
                  "보관 상태입니다. active는 보관하지 않은 버전, archived는 보관한 버전, all은 둘 다입니다. 생략하면 active입니다.",
              schema =
                  @Schema(
                      allowableValues = {"active", "archived", "all"},
                      defaultValue = "active"))
          @RequestParam(defaultValue = "active")
          @StorableText
          String status) {
    return priceRateService.list(organizationId, code, customerId, status);
  }

  @DeleteMapping("/{id}")
  @AdminAccess
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      summary = "단가 버전 보관",
      description =
          """
          단가 버전 하나를 보관합니다. 이번 달까지 적용하고 다음 달부터 멈추며, 아직 시작하지 않은 버전은 시작 전에 취소됩니다. 버전 이력은 남깁니다. 이미 보관한 버전에 다시 보내도 204입니다.
          고객 단가나 아직 시작하지 않은 버전처럼 버전 하나를 골라 보관할 때 이 경로를 씁니다. 구분 값으로 이번 달 공통 단가를 멈추려면 DELETE /v1/billable-metrics/{code}/price-rates?dimension_values=...를 씁니다.
          """)
  public void deletePriceRateVersion(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(description = "단가 버전이 속한 미터의 미터 코드") @PathVariable @StorableText String code,
      @Parameter(description = "보관할 단가 버전 ID(UUID)") @PathVariable UUID id) {
    priceRateService.delete(organizationId, code, id);
  }

  @PostMapping("/{id}/restorations")
  @AdminAccess
  @Operation(
      summary = "단가 버전 복원",
      description =
          "보관 효력이 아직 시작되지 않았고 같은 구분 값, 고객 범위, 시작 월의 보관하지 않은 버전이 없을 때만 복원합니다. 그 밖에는 409 archive_restoration_conflict이며, 이때는 새 버전을 등록합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "복원한 단가 버전입니다. 보관하지 않은 버전이면 그대로 돌려줍니다."),
    @ApiResponse(
        responseCode = "404",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=price_rate_not_found: 그 미터에 그런 id의 단가 버전이 없습니다."),
    @ApiResponse(
        responseCode = "409",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description =
            "code=archive_restoration_conflict: 같은 구분 값, 고객 범위, 시작 월의 보관하지 않은 버전이 있거나, 보관 효력이 이번 달이나 그 전에 시작됐습니다.")
  })
  public PriceRateResponse restorePriceRate(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(description = "단가 버전이 속한 미터의 미터 코드") @PathVariable @StorableText String code,
      @Parameter(description = "복원할 단가 버전 ID(UUID)") @PathVariable UUID id) {
    return priceRateService.restore(organizationId, code, id);
  }
}
