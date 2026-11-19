package com.meterengine.query.controller;

import com.meterengine.customer.dto.CustomerResponse;
import com.meterengine.global.error.ErrorResponse;
import com.meterengine.global.security.CurrentOrganizationId;
import com.meterengine.global.security.EnvironmentReady;
import com.meterengine.global.security.ReadAccess;
import com.meterengine.global.validation.StorableJson;
import com.meterengine.global.validation.UnknownQueryParameterValidator;
import com.meterengine.invoice.dto.DraftInvoiceResponse.DraftInvoiceCustomer;
import com.meterengine.metric.dto.BillableMetricResponse;
import com.meterengine.query.dto.InvoiceSearchResponse;
import com.meterengine.query.dto.OptionResponse;
import com.meterengine.query.dto.PriceSearchResponse;
import com.meterengine.query.dto.SearchPageResponse;
import com.meterengine.query.dto.UsageCustomerResponse;
import com.meterengine.query.service.SearchCsvService;
import com.meterengine.query.service.SearchParameters;
import com.meterengine.query.service.WorkspaceQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@EnvironmentReady
public class WorkspaceQueryController {
  private static final String INVALID_SEARCH =
      "code=validation_error: 받지 않는 쿼리 파라미터가 있거나 값의 형식이나 범위가 틀렸습니다. 어느 값인지는 errors에 있습니다.";
  private static final String INVALID_EXPORT =
      INVALID_SEARCH + " 조건 전체가 10,000행을 넘어도 이 코드이며 errors의 field는 export입니다. 범위를 좁혀 다시 보냅니다.";
  private static final String UNKNOWN_PARAMETER = "code=validation_error: 받지 않는 쿼리 파라미터가 있습니다.";
  private static final String CUSTOMER_NOT_FOUND =
      "code=customer_not_found: customer_id의 고객이 현재 조직과 업무 환경에 없습니다.";
  private static final String SEARCH_PAGE = "조건에 맞는 페이지입니다. total은 조건 전체 건수입니다.";
  private static final String SEARCH_CSV =
      "같은 검색 조건 전체의 CSV입니다. UTF-8 BOM과 CRLF를 쓰고, 수식으로 읽힐 문자열은 앞에 작은따옴표를 붙입니다.";
  private static final String MONTHS = "최신 달부터 늘어놓은 월 목록";

  private static final String Q_CUSTOMERS =
      "검색어입니다. 고객 이름, external_id, billing_email에 이 글자가 들어 있는 고객을 찾고, 대소문자를 가리지 않습니다.";
  private static final String Q_METRICS =
      "검색어입니다. 과금 미터 코드, 과금 미터 이름, 이벤트 유형에 이 글자가 들어 있는 과금 미터를 찾고, 대소문자를 가리지 않습니다.";
  private static final String Q_PRICES =
      "검색어입니다. 과금 미터 코드와 과금 미터 이름에 이 글자가 들어 있는 과금 미터를 찾고, 대소문자를 가리지 않습니다.";
  private static final String Q_CUSTOMER_ROWS =
      "검색어입니다. 고객 이름, external_id, 고객 ID에 이 글자가 들어 있는 고객을 찾고, 대소문자를 가리지 않습니다.";
  private static final String Q_INVOICES =
      "검색어입니다. 청구서 번호, 고객 이름, 고객 ID에 이 글자가 들어 있는 청구서를 찾고, 대소문자를 가리지 않습니다.";
  private static final String SORT_CUSTOMERS =
      "정렬 기준입니다. name, external_id, created_at, archived_at, id, billing_email 가운데 하나이고 생략하면"
          + " name입니다. 값이 같으면 id 순입니다.";
  private static final String SORT_METRICS =
      "정렬 기준입니다. code, name, event_type, aggregation, archived_at 가운데 하나이고 생략하면 code입니다."
          + " 값이 같으면 과금 미터 코드 순입니다.";
  private static final String SORT_PRICES =
      "정렬 기준입니다. code, name, rate_count, archived_at 가운데 하나이고 생략하면 code입니다."
          + " billable_metric_code와 billable_metric_name도 받으며 각각 code, name과 같습니다.";
  private static final String SORT_USAGE =
      "정렬 기준입니다. customer_name, customer_id, line_count(집계 항목 수) 가운데 하나이고 생략하면"
          + " customer_name입니다.";
  private static final String SORT_DRAFTS =
      "정렬 기준입니다. customer_name, customer_id, amount, line_count(청구 항목 수) 가운데 하나이고 생략하면"
          + " customer_name입니다.";
  private static final String SORT_INVOICES =
      "정렬 기준입니다. month, customer_name, total_amount, finalized_at, invoice_number, status(결제 상태)"
          + " 가운데 하나이고 생략하면 month입니다. 값이 같으면 고객 이름 순입니다.";
  private static final String ORDER = "정렬 방향입니다. asc(오름차순)나 desc(내림차순)이고 생략하면 asc입니다.";
  private static final String ORDER_INVOICES = "정렬 방향입니다. asc(오름차순)나 desc(내림차순)이고 생략하면 desc입니다.";
  private static final String PAGE = "페이지 번호입니다. 1부터 시작하고 생략하면 1입니다.";
  private static final String SIZE = "한 페이지에 담을 건수입니다. 생략하면 25이고 최대 200입니다.";
  private static final String STATUS_CUSTOMERS =
      "고객의 보관 상태입니다. active, archived, all 가운데 하나이고 생략하면 active입니다.";
  private static final String STATUS_METRICS =
      "과금 미터의 보관 상태입니다. active, archived, all 가운데 하나이고 생략하면 active입니다.";
  private static final String STATUS_USAGE =
      "고객의 보관 상태입니다. active, archived, all 가운데 하나이고 생략하면 all입니다.";
  private static final String STATUS_INVOICES =
      "결제 상태입니다. not_required, unpaid, pending, paid, failed, partially_canceled, canceled, all 가운데"
          + " 하나이고, 생략하거나 all이면 거르지 않습니다.";
  private static final String EXTERNAL_ID = "external_id가 이 값과 같은 고객만 돌려줍니다.";
  private static final String CUSTOMER_ID_FILTER = "이 고객 ID(UUID)의 고객만 돌려줍니다.";
  private static final String CUSTOMER_ID_PRICES =
      "이 고객의 고객 단가만 돌려줍니다. 생략하면 공통 단가만 돌려줍니다. 현재 업무 환경에 없는 고객이면 404입니다.";
  private static final String CUSTOMER_ID_ROWS =
      "이 고객 ID(UUID)의 고객만 돌려줍니다. 현재 업무 환경에 없는 고객이면 404입니다.";
  private static final String CUSTOMER_ID_INVOICES =
      "이 고객 ID(UUID)의 청구서만 돌려줍니다. 현재 업무 환경에 없는 고객이면 404입니다.";
  private static final String METRIC_CODE_FILTER = "과금 미터 코드가 이 값과 같은 과금 미터만 돌려줍니다.";
  private static final String METRIC_CODE_USAGE = "이 과금 미터 코드의 사용량만 담습니다.";
  private static final String METRIC_CODE_DRAFTS =
      "이 과금 미터 코드의 청구 항목만 담고, amount는 그 청구 항목만 더한 금액입니다.";
  private static final String METRIC_CODE_INVOICES =
      "이 과금 미터 코드의 청구 항목이 있는 청구서만 돌려줍니다. 금액은 청구서 전체 금액 그대로입니다.";
  private static final String MONTH_PRICES = "단가를 볼 달(yyyy-MM, KST)입니다. 생략하면 이번 달입니다.";
  private static final String MONTH_USAGE = "집계할 달(yyyy-MM, KST)입니다. 생략하면 이번 달입니다.";
  private static final String MONTH_DRAFTS = "청구 예정액을 계산할 달(yyyy-MM, KST)입니다. 생략하면 이번 달입니다.";
  private static final String MONTH_INVOICES = "청구서의 달(yyyy-MM, KST)입니다. 생략하면 모든 달입니다.";
  private static final String MIN_DRAFTS = "고객별 청구 예정액(amount, 원)이 이 값 이상인 고객만 돌려줍니다.";
  private static final String MAX_DRAFTS = "고객별 청구 예정액(amount, 원)이 이 값 이하인 고객만 돌려줍니다.";
  private static final String MIN_INVOICES = "청구서의 부가세 포함 금액(total_amount, 원)이 이 값 이상인 청구서만 돌려줍니다.";
  private static final String MAX_INVOICES = "청구서의 부가세 포함 금액(total_amount, 원)이 이 값 이하인 청구서만 돌려줍니다.";
  private static final String FROM =
      "발행 시각(finalized_at)의 시작입니다. 날짜(yyyy-MM-dd)를 주면 그날 0시(KST)부터, ISO 8601 시각을 주면 그"
          + " 시각부터입니다. 시간대를 빼면 KST로 읽습니다.";
  private static final String TO =
      "발행 시각(finalized_at)의 끝입니다. 날짜(yyyy-MM-dd)를 주면 그날까지 포함하고, ISO 8601 시각을 주면 그 시각"
          + " 전까지입니다. 시간대를 빼면 KST로 읽으며, from보다 뒤여야 합니다.";
  private static final String MODE =
      "결제 모드입니다. test나 live이고, 생략하면 현재 업무 환경의 결제 모드를 씁니다. 업무 환경과 다른 값이면"
          + " 403(code=environment_mismatch)입니다. 기존 데이터에서는 생략하면 결제 설정의 모드를 씁니다.";

  private final WorkspaceQueryService workspaceQueryService;
  private final SearchCsvService searchCsvService;

  WorkspaceQueryController(
      WorkspaceQueryService workspaceQueryService, SearchCsvService searchCsvService) {
    this.workspaceQueryService = workspaceQueryService;
    this.searchCsvService = searchCsvService;
  }

  @GetMapping("/v1/customers/search")
  @ReadAccess
  @EnvironmentReady
  @Operation(
      summary = "고객 검색",
      description =
          "검색어와 여러 조건으로 고객을 거르고 정렬해 한 페이지씩 돌려줍니다. external_id나 status만으로 거르고 전부"
              + " 받으려면 GET /v1/customers가 간단합니다. total은 조건에 맞는 전체 건수이고, total_unfiltered는 현재"
              + " 조직과 업무 환경의 전체 고객 수입니다. summary는 active_count(사용 중인 고객 수)와"
              + " archived_count(보관한 고객 수)이며 조건과 관계없이 업무 환경 전체를 셉니다.")
  @Parameters({
    @Parameter(
        name = "q",
        description = Q_CUSTOMERS,
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "sort",
        description = SORT_CUSTOMERS,
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "order",
        description = ORDER,
        in = ParameterIn.QUERY,
        schema =
            @Schema(
                type = "string",
                allowableValues = {"asc", "desc"})),
    @Parameter(
        name = "page",
        description = PAGE,
        in = ParameterIn.QUERY,
        schema =
            @Schema(type = "integer", minimum = "1", maximum = "2147483647", defaultValue = "1")),
    @Parameter(
        name = "size",
        description = SIZE,
        in = ParameterIn.QUERY,
        schema = @Schema(type = "integer", minimum = "1", maximum = "200", defaultValue = "25")),
    @Parameter(
        name = "status",
        description = STATUS_CUSTOMERS,
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "external_id",
        description = EXTERNAL_ID,
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "customer_id",
        description = CUSTOMER_ID_FILTER,
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", format = "uuid"))
  })
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = SEARCH_PAGE),
    @ApiResponse(
        responseCode = "400",
        description = INVALID_SEARCH,
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)))
  })
  public SearchPageResponse<CustomerResponse> searchCustomers(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(hidden = true) @RequestParam @StorableJson Map<String, String> parameters) {
    return workspaceQueryService.search(
        organizationId, SearchParameters.parse(parameters, "customers"));
  }

  @GetMapping("/v1/billable-metrics/search")
  @ReadAccess
  @Operation(
      summary = "과금 미터 검색",
      description =
          "검색어와 여러 조건으로 과금 미터를 거르고 정렬해 한 페이지씩 돌려줍니다. status만으로 거르고 전부 받으려면 GET"
              + " /v1/billable-metrics가 간단합니다. total_unfiltered는 현재 업무 환경의 전체 과금 미터 수입니다. summary는"
              + " active_count(사용 중인 과금 미터 수)와 archived_count(보관한 과금 미터 수)이며 조건과 관계없이 업무 환경 전체를"
              + " 셉니다.")
  @Parameters({
    @Parameter(
        name = "q",
        description = Q_METRICS,
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "sort",
        description = SORT_METRICS,
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "order",
        description = ORDER,
        in = ParameterIn.QUERY,
        schema =
            @Schema(
                type = "string",
                allowableValues = {"asc", "desc"})),
    @Parameter(
        name = "page",
        description = PAGE,
        in = ParameterIn.QUERY,
        schema =
            @Schema(type = "integer", minimum = "1", maximum = "2147483647", defaultValue = "1")),
    @Parameter(
        name = "size",
        description = SIZE,
        in = ParameterIn.QUERY,
        schema = @Schema(type = "integer", minimum = "1", maximum = "200", defaultValue = "25")),
    @Parameter(
        name = "status",
        description = STATUS_METRICS,
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "billable_metric_code",
        description = METRIC_CODE_FILTER,
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000))
  })
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = SEARCH_PAGE),
    @ApiResponse(
        responseCode = "400",
        description = INVALID_SEARCH,
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)))
  })
  public SearchPageResponse<BillableMetricResponse> searchBillableMetrics(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(hidden = true) @RequestParam @StorableJson Map<String, String> parameters) {
    return workspaceQueryService.metrics(
        organizationId, SearchParameters.parse(parameters, "metrics"));
  }

  @GetMapping("/v1/billable-metric-prices/search")
  @ReadAccess
  @Operation(
      summary = "단가 검색",
      description =
          "검색어와 여러 조건으로 과금 미터별 단가를 거르고 정렬해 한 페이지씩 돌려줍니다. month와 customer_id만으로 거르고"
              + " 전부 받으려면 GET /v1/billable-metric-prices가 간단합니다. 항목마다 과금 미터 이름, 그 달에 적용하는 구분 값별"
              + " 단가(price_rates), 단가 수(rate_count)를 담습니다. total_unfiltered는 그 달의 전체 과금 미터 수입니다."
              + " summary는 month, metric_count(조건에 맞는 과금 미터 수), no_rate_count(그 가운데 단가가 없는 과금 미터 수)입니다.")
  @Parameters({
    @Parameter(
        name = "q",
        description = Q_PRICES,
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "sort",
        description = SORT_PRICES,
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "order",
        description = ORDER,
        in = ParameterIn.QUERY,
        schema =
            @Schema(
                type = "string",
                allowableValues = {"asc", "desc"})),
    @Parameter(
        name = "page",
        description = PAGE,
        in = ParameterIn.QUERY,
        schema =
            @Schema(type = "integer", minimum = "1", maximum = "2147483647", defaultValue = "1")),
    @Parameter(
        name = "size",
        description = SIZE,
        in = ParameterIn.QUERY,
        schema = @Schema(type = "integer", minimum = "1", maximum = "200", defaultValue = "25")),
    @Parameter(
        name = "status",
        description = STATUS_METRICS,
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "month",
        description = MONTH_PRICES,
        in = ParameterIn.QUERY,
        schema =
            @Schema(type = "string", pattern = "^[0-9]{4}-(0[1-9]|1[0-2])$", example = "2026-08")),
    @Parameter(
        name = "customer_id",
        description = CUSTOMER_ID_PRICES,
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", format = "uuid")),
    @Parameter(
        name = "billable_metric_code",
        description = METRIC_CODE_FILTER,
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000))
  })
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = SEARCH_PAGE),
    @ApiResponse(
        responseCode = "400",
        description = INVALID_SEARCH,
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = CUSTOMER_NOT_FOUND,
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)))
  })
  public SearchPageResponse<PriceSearchResponse> searchBillableMetricPrices(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(hidden = true) @RequestParam @StorableJson Map<String, String> parameters) {
    return workspaceQueryService.prices(
        organizationId, SearchParameters.parse(parameters, "prices"));
  }

  @GetMapping("/v1/usage/search")
  @ReadAccess
  @Operation(
      summary = "사용량 검색",
      description =
          "검색어와 여러 조건으로 고객별 사용량을 거르고 정렬해 한 페이지씩 돌려줍니다. month만 정해 모든 고객을 받으려면"
              + " GET /v1/usage가 간단합니다. 한 고객의 모든 과금 미터를 같은 페이지에 담습니다. total_unfiltered는 보관한"
              + " 고객을 포함한 전체 고객 수입니다. summary는 month, customer_count(조건에 맞는 고객 수),"
              + " metric_count(과금 미터 수), line_count(집계 항목 수), nonzero_customer_count(수량이 0이 아닌 과금 미터가 있는"
              + " 고객 수)입니다.")
  @Parameters({
    @Parameter(
        name = "q",
        description = Q_CUSTOMER_ROWS,
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "sort",
        description = SORT_USAGE,
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "order",
        description = ORDER,
        in = ParameterIn.QUERY,
        schema =
            @Schema(
                type = "string",
                allowableValues = {"asc", "desc"})),
    @Parameter(
        name = "page",
        description = PAGE,
        in = ParameterIn.QUERY,
        schema =
            @Schema(type = "integer", minimum = "1", maximum = "2147483647", defaultValue = "1")),
    @Parameter(
        name = "size",
        description = SIZE,
        in = ParameterIn.QUERY,
        schema = @Schema(type = "integer", minimum = "1", maximum = "200", defaultValue = "25")),
    @Parameter(
        name = "status",
        description = STATUS_USAGE,
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "month",
        description = MONTH_USAGE,
        in = ParameterIn.QUERY,
        schema =
            @Schema(type = "string", pattern = "^[0-9]{4}-(0[1-9]|1[0-2])$", example = "2026-08")),
    @Parameter(
        name = "customer_id",
        description = CUSTOMER_ID_ROWS,
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", format = "uuid")),
    @Parameter(
        name = "billable_metric_code",
        description = METRIC_CODE_USAGE,
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000))
  })
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = SEARCH_PAGE),
    @ApiResponse(
        responseCode = "400",
        description = INVALID_SEARCH,
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = CUSTOMER_NOT_FOUND,
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)))
  })
  public SearchPageResponse<UsageCustomerResponse> searchUsage(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(hidden = true) @RequestParam @StorableJson Map<String, String> parameters) {
    return workspaceQueryService.usage(organizationId, SearchParameters.parse(parameters, "usage"));
  }

  @GetMapping("/v1/invoices/draft/search")
  @ReadAccess
  @Operation(
      summary = "청구 예정액 검색",
      description =
          "검색어와 여러 조건으로 고객별 청구 예정액을 거르고 정렬해 한 페이지씩 돌려줍니다. month만 정해 모든 고객을"
              + " 받으려면 GET /v1/invoices/draft가 간단합니다. total_unfiltered는 그 달 청구서를 아직 발행하지 않은 전체 고객"
              + " 수입니다. summary의 amount, customer_count, billable_customer_count, line_count, unpriced_count,"
              + " negative_count는 조건에 맞는 고객 전체의 값이고, 앞에 month_가 붙은 같은 이름의 값은 그 달 전체의"
              + " 값입니다. 청구서 발행은 검색 조건과 관계없이 그 달 전체 고객의 청구서를 발행하므로 finalization_scope는 month입니다."
              + " summary에는 month, calculated_at과, 청구 예정액 조회와 같은 auto_finalize_enabled, auto_finalize_at,"
              + " auto_finalize_running도 담습니다.")
  @Parameters({
    @Parameter(
        name = "q",
        description = Q_CUSTOMER_ROWS,
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "sort",
        description = SORT_DRAFTS,
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "order",
        description = ORDER,
        in = ParameterIn.QUERY,
        schema =
            @Schema(
                type = "string",
                allowableValues = {"asc", "desc"})),
    @Parameter(
        name = "page",
        description = PAGE,
        in = ParameterIn.QUERY,
        schema =
            @Schema(type = "integer", minimum = "1", maximum = "2147483647", defaultValue = "1")),
    @Parameter(
        name = "size",
        description = SIZE,
        in = ParameterIn.QUERY,
        schema = @Schema(type = "integer", minimum = "1", maximum = "200", defaultValue = "25")),
    @Parameter(
        name = "month",
        description = MONTH_DRAFTS,
        in = ParameterIn.QUERY,
        schema =
            @Schema(type = "string", pattern = "^[0-9]{4}-(0[1-9]|1[0-2])$", example = "2026-08")),
    @Parameter(
        name = "customer_id",
        description = CUSTOMER_ID_ROWS,
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", format = "uuid")),
    @Parameter(
        name = "billable_metric_code",
        description = METRIC_CODE_DRAFTS,
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "min_amount",
        description = MIN_DRAFTS,
        in = ParameterIn.QUERY,
        schema = @Schema(type = "integer", format = "int64", minimum = "0")),
    @Parameter(
        name = "max_amount",
        description = MAX_DRAFTS,
        in = ParameterIn.QUERY,
        schema = @Schema(type = "integer", format = "int64", minimum = "0"))
  })
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = SEARCH_PAGE),
    @ApiResponse(
        responseCode = "400",
        description = INVALID_SEARCH,
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = CUSTOMER_NOT_FOUND,
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)))
  })
  public SearchPageResponse<DraftInvoiceCustomer> searchDraftInvoices(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(hidden = true) @RequestParam @StorableJson Map<String, String> parameters) {
    return workspaceQueryService.drafts(
        organizationId, SearchParameters.parse(parameters, "drafts"));
  }

  @GetMapping("/v1/invoices/search")
  @ReadAccess
  @Operation(
      summary = "청구서와 결제 상태 검색",
      description =
          "검색어와 여러 조건으로 발행한 청구서를 거르고 정렬해 한 페이지씩 돌려줍니다. 항목마다 청구서(invoice)와 결제"
              + " 상태(payment)를 담습니다. customer_id와 month만으로 거르고 전부 받으려면 GET /v1/invoices가"
              + " 간단합니다. 정렬 방향은 생략하면 desc입니다. from과 to는 한쪽만 줘도 되고, month와 함께 주면 둘 다"
              + " 만족하는 청구서만 나옵니다. 이벤트 목록 조회와 내보내기는 from과 to를 함께 보내야 합니다."
              + " total_unfiltered는 현재 업무 환경에서 발행한 청구서 전체 수입니다. summary의 금액과 결제 상태별 건수는"
              + " 페이지와 관계없이 조건에 맞는 청구서 전체의 값이고, pending인 청구서가 있으면 outstanding_amount는"
              + " null입니다. summary는 mode, invoice_count, supply_amount, tax_amount, total_amount,"
              + " canceled_amount, has_pending, outstanding_amount, status_counts(결제 상태별 건수)입니다.")
  @Parameters({
    @Parameter(
        name = "q",
        description = Q_INVOICES,
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "sort",
        description = SORT_INVOICES,
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "order",
        description = ORDER_INVOICES,
        in = ParameterIn.QUERY,
        schema =
            @Schema(
                type = "string",
                allowableValues = {"asc", "desc"})),
    @Parameter(
        name = "page",
        description = PAGE,
        in = ParameterIn.QUERY,
        schema =
            @Schema(type = "integer", minimum = "1", maximum = "2147483647", defaultValue = "1")),
    @Parameter(
        name = "size",
        description = SIZE,
        in = ParameterIn.QUERY,
        schema = @Schema(type = "integer", minimum = "1", maximum = "200", defaultValue = "25")),
    @Parameter(
        name = "month",
        description = MONTH_INVOICES,
        in = ParameterIn.QUERY,
        schema =
            @Schema(type = "string", pattern = "^[0-9]{4}-(0[1-9]|1[0-2])$", example = "2026-08")),
    @Parameter(
        name = "customer_id",
        description = CUSTOMER_ID_INVOICES,
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", format = "uuid")),
    @Parameter(
        name = "billable_metric_code",
        description = METRIC_CODE_INVOICES,
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "from",
        description = FROM,
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "to",
        description = TO,
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "status",
        description = STATUS_INVOICES,
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "mode",
        description = MODE,
        in = ParameterIn.QUERY,
        schema =
            @Schema(
                type = "string",
                allowableValues = {"test", "live"})),
    @Parameter(
        name = "min_amount",
        description = MIN_INVOICES,
        in = ParameterIn.QUERY,
        schema = @Schema(type = "integer", format = "int64", minimum = "0")),
    @Parameter(
        name = "max_amount",
        description = MAX_INVOICES,
        in = ParameterIn.QUERY,
        schema = @Schema(type = "integer", format = "int64", minimum = "0"))
  })
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = SEARCH_PAGE),
    @ApiResponse(
        responseCode = "400",
        description = INVALID_SEARCH,
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = CUSTOMER_NOT_FOUND,
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)))
  })
  public SearchPageResponse<InvoiceSearchResponse> searchInvoices(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(hidden = true) @RequestParam @StorableJson Map<String, String> parameters) {
    return workspaceQueryService.invoices(organizationId, invoiceParameters(parameters));
  }

  @GetMapping("/v1/customers/options")
  @ReadAccess
  @EnvironmentReady
  @Operation(
      summary = "고객 선택지 검색",
      description =
          "고객 검색(GET /v1/customers/search)과 같은 조건을 받아 선택 상자에 쓸 value(고객 ID), label(고객 이름),"
              + " archived와 페이지 정보를 돌려줍니다. 보관한 고객도 고를 수 있게 하려면 status=all을 보냅니다.")
  @Parameters({
    @Parameter(
        name = "q",
        description = Q_CUSTOMERS,
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "sort",
        description = SORT_CUSTOMERS,
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "order",
        description = ORDER,
        in = ParameterIn.QUERY,
        schema =
            @Schema(
                type = "string",
                allowableValues = {"asc", "desc"})),
    @Parameter(
        name = "page",
        description = PAGE,
        in = ParameterIn.QUERY,
        schema =
            @Schema(type = "integer", minimum = "1", maximum = "2147483647", defaultValue = "1")),
    @Parameter(
        name = "size",
        description = SIZE,
        in = ParameterIn.QUERY,
        schema = @Schema(type = "integer", minimum = "1", maximum = "200", defaultValue = "25")),
    @Parameter(
        name = "status",
        description = STATUS_CUSTOMERS,
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "external_id",
        description = EXTERNAL_ID,
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "customer_id",
        description = CUSTOMER_ID_FILTER,
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", format = "uuid"))
  })
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "조건에 맞는 선택지 페이지"),
    @ApiResponse(
        responseCode = "400",
        description = INVALID_SEARCH,
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)))
  })
  public SearchPageResponse<OptionResponse> suggestCustomers(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(hidden = true) @RequestParam @StorableJson Map<String, String> parameters) {
    return workspaceQueryService.suggest(
        organizationId, SearchParameters.parse(parameters, "customers"), "customers");
  }

  @GetMapping("/v1/billable-metrics/options")
  @ReadAccess
  @Operation(
      summary = "과금 미터 선택지 검색",
      description =
          "과금 미터 검색(GET /v1/billable-metrics/search)과 같은 조건을 받아 선택 상자에 쓸 value(과금 미터 코드),"
              + " label(과금 미터 이름), archived와 페이지 정보를 돌려줍니다. 보관한 과금 미터도 고를 수 있게 하려면 status=all을"
              + " 보냅니다.")
  @Parameters({
    @Parameter(
        name = "q",
        description = Q_METRICS,
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "sort",
        description = SORT_METRICS,
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "order",
        description = ORDER,
        in = ParameterIn.QUERY,
        schema =
            @Schema(
                type = "string",
                allowableValues = {"asc", "desc"})),
    @Parameter(
        name = "page",
        description = PAGE,
        in = ParameterIn.QUERY,
        schema =
            @Schema(type = "integer", minimum = "1", maximum = "2147483647", defaultValue = "1")),
    @Parameter(
        name = "size",
        description = SIZE,
        in = ParameterIn.QUERY,
        schema = @Schema(type = "integer", minimum = "1", maximum = "200", defaultValue = "25")),
    @Parameter(
        name = "status",
        description = STATUS_METRICS,
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "billable_metric_code",
        description = METRIC_CODE_FILTER,
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000))
  })
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "조건에 맞는 선택지 페이지"),
    @ApiResponse(
        responseCode = "400",
        description = INVALID_SEARCH,
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)))
  })
  public SearchPageResponse<OptionResponse> suggestBillableMetrics(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(hidden = true) @RequestParam @StorableJson Map<String, String> parameters) {
    return workspaceQueryService.suggest(
        organizationId, SearchParameters.parse(parameters, "metrics"), "metrics");
  }

  @GetMapping("/v1/usage/months")
  @ReadAccess
  @Operation(
      summary = "사용량을 볼 수 있는 월 목록 조회",
      description =
          "GET /v1/usage와 GET /v1/usage/search의 month로 고를 달을 돌려줍니다. 현재 업무 환경에 저장한 이벤트가 발생한"
              + " 달(occurred_at, KST)을 중복 없이 최신 달부터 늘어놓으며, GET /v1/events/months와 같은 목록입니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = MONTHS),
    @ApiResponse(
        responseCode = "400",
        description = UNKNOWN_PARAMETER,
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)))
  })
  public Map<String, List<String>> listUsageMonths(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(hidden = true) @RequestParam @StorableJson Map<String, String> parameters) {
    UnknownQueryParameterValidator.validate(parameters, Set.of());
    return workspaceQueryService.list(organizationId, "usage");
  }

  @GetMapping("/v1/invoices/months")
  @ReadAccess
  @Operation(
      summary = "발행한 청구서가 있는 월 목록 조회",
      description = "현재 업무 환경에 발행한 청구서가 있는 청구 달(yyyy-MM)을 중복 없이 최신 달부터 돌려줍니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = MONTHS),
    @ApiResponse(
        responseCode = "400",
        description = UNKNOWN_PARAMETER,
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)))
  })
  public Map<String, List<String>> listInvoiceMonths(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(hidden = true) @RequestParam @StorableJson Map<String, String> parameters) {
    UnknownQueryParameterValidator.validate(parameters, Set.of());
    return workspaceQueryService.list(organizationId, "invoices");
  }

  @GetMapping("/v1/billable-metric-prices/months")
  @ReadAccess
  @Operation(
      summary = "단가 버전이 시작된 월 목록 조회",
      description = "현재 업무 환경의 단가가 적용되기 시작한 달(yyyy-MM)을 중복 없이 최신 달부터 돌려줍니다. 고객 단가와 보관한 단가도 셉니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = MONTHS),
    @ApiResponse(
        responseCode = "400",
        description = UNKNOWN_PARAMETER,
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)))
  })
  public Map<String, List<String>> listPriceMonths(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(hidden = true) @RequestParam @StorableJson Map<String, String> parameters) {
    UnknownQueryParameterValidator.validate(parameters, Set.of());
    return workspaceQueryService.list(organizationId, "billable-metric-prices");
  }

  @GetMapping(value = "/v1/customers/export", produces = "text/csv")
  @ReadAccess
  @EnvironmentReady
  @Operation(
      summary = "고객 내보내기",
      description =
          "GET /v1/customers/search와 같은 조건에 맞는 결과를 페이지를 나누지 않고 모두 내보냅니다. 최대 10,000행이며 넘으면"
              + " 400입니다.")
  @Parameters({
    @Parameter(
        name = "q",
        description = Q_CUSTOMERS,
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "sort",
        description = SORT_CUSTOMERS,
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "order",
        description = ORDER,
        in = ParameterIn.QUERY,
        schema =
            @Schema(
                type = "string",
                allowableValues = {"asc", "desc"})),
    @Parameter(
        name = "status",
        description = STATUS_CUSTOMERS,
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "external_id",
        description = EXTERNAL_ID,
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "customer_id",
        description = CUSTOMER_ID_FILTER,
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", format = "uuid"))
  })
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = SEARCH_CSV,
        content = @Content(mediaType = "text/csv", schema = @Schema(type = "string"))),
    @ApiResponse(
        responseCode = "400",
        description = INVALID_EXPORT,
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)))
  })
  public ResponseEntity<byte[]> exportCustomers(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(hidden = true) @RequestParam @StorableJson Map<String, String> parameters) {
    return searchCsvService.export(
        "customers",
        workspaceQueryService.search(
            organizationId, SearchParameters.parse(parameters, "customers").forExport()),
        CustomerResponse.class);
  }

  @GetMapping(value = "/v1/billable-metrics/export", produces = "text/csv")
  @ReadAccess
  @Operation(
      summary = "과금 미터 내보내기",
      description =
          "GET /v1/billable-metrics/search와 같은 조건에 맞는 결과를 페이지를 나누지 않고 모두 내보냅니다. 최대 10,000행이며"
              + " 넘으면 400입니다.")
  @Parameters({
    @Parameter(
        name = "q",
        description = Q_METRICS,
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "sort",
        description = SORT_METRICS,
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "order",
        description = ORDER,
        in = ParameterIn.QUERY,
        schema =
            @Schema(
                type = "string",
                allowableValues = {"asc", "desc"})),
    @Parameter(
        name = "status",
        description = STATUS_METRICS,
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "billable_metric_code",
        description = METRIC_CODE_FILTER,
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000))
  })
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = SEARCH_CSV,
        content = @Content(mediaType = "text/csv", schema = @Schema(type = "string"))),
    @ApiResponse(
        responseCode = "400",
        description = INVALID_EXPORT,
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)))
  })
  public ResponseEntity<byte[]> exportMetrics(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(hidden = true) @RequestParam @StorableJson Map<String, String> parameters) {
    return searchCsvService.export(
        "billable-metrics",
        workspaceQueryService.metrics(
            organizationId, SearchParameters.parse(parameters, "metrics").forExport()),
        BillableMetricResponse.class);
  }

  @GetMapping(value = "/v1/billable-metric-prices/export", produces = "text/csv")
  @ReadAccess
  @Operation(
      summary = "단가 내보내기",
      description =
          "GET /v1/billable-metric-prices/search와 같은 조건에 맞는 결과를 페이지를 나누지 않고 모두 내보냅니다. 최대"
              + " 10,000행이며 넘으면 400입니다.")
  @Parameters({
    @Parameter(
        name = "q",
        description = Q_PRICES,
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "sort",
        description = SORT_PRICES,
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "order",
        description = ORDER,
        in = ParameterIn.QUERY,
        schema =
            @Schema(
                type = "string",
                allowableValues = {"asc", "desc"})),
    @Parameter(
        name = "status",
        description = STATUS_METRICS,
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "month",
        description = MONTH_PRICES,
        in = ParameterIn.QUERY,
        schema =
            @Schema(type = "string", pattern = "^[0-9]{4}-(0[1-9]|1[0-2])$", example = "2026-08")),
    @Parameter(
        name = "customer_id",
        description = CUSTOMER_ID_PRICES,
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", format = "uuid")),
    @Parameter(
        name = "billable_metric_code",
        description = METRIC_CODE_FILTER,
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000))
  })
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = SEARCH_CSV,
        content = @Content(mediaType = "text/csv", schema = @Schema(type = "string"))),
    @ApiResponse(
        responseCode = "400",
        description = INVALID_EXPORT,
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = CUSTOMER_NOT_FOUND,
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)))
  })
  public ResponseEntity<byte[]> exportPrices(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(hidden = true) @RequestParam @StorableJson Map<String, String> parameters) {
    return searchCsvService.export(
        "billable-metric-prices",
        workspaceQueryService.prices(
            organizationId, SearchParameters.parse(parameters, "prices").forExport()),
        PriceSearchResponse.class);
  }

  @GetMapping(value = "/v1/usage/export", produces = "text/csv")
  @ReadAccess
  @Operation(
      summary = "사용량 내보내기",
      description =
          "GET /v1/usage/search와 같은 조건에 맞는 결과를 페이지를 나누지 않고 모두 내보냅니다. 최대 10,000행이며 넘으면 400입니다.")
  @Parameters({
    @Parameter(
        name = "q",
        description = Q_CUSTOMER_ROWS,
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "sort",
        description = SORT_USAGE,
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "order",
        description = ORDER,
        in = ParameterIn.QUERY,
        schema =
            @Schema(
                type = "string",
                allowableValues = {"asc", "desc"})),
    @Parameter(
        name = "status",
        description = STATUS_USAGE,
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "month",
        description = MONTH_USAGE,
        in = ParameterIn.QUERY,
        schema =
            @Schema(type = "string", pattern = "^[0-9]{4}-(0[1-9]|1[0-2])$", example = "2026-08")),
    @Parameter(
        name = "customer_id",
        description = CUSTOMER_ID_ROWS,
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", format = "uuid")),
    @Parameter(
        name = "billable_metric_code",
        description = METRIC_CODE_USAGE,
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000))
  })
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = SEARCH_CSV,
        content = @Content(mediaType = "text/csv", schema = @Schema(type = "string"))),
    @ApiResponse(
        responseCode = "400",
        description = INVALID_EXPORT,
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = CUSTOMER_NOT_FOUND,
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)))
  })
  public ResponseEntity<byte[]> exportUsage(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(hidden = true) @RequestParam @StorableJson Map<String, String> parameters) {
    return searchCsvService.export(
        "usage",
        workspaceQueryService.usage(
            organizationId, SearchParameters.parse(parameters, "usage").forExport()),
        UsageCustomerResponse.class);
  }

  @GetMapping(value = "/v1/invoices/draft/export", produces = "text/csv")
  @ReadAccess
  @Operation(
      summary = "청구 예정액 내보내기",
      description =
          "GET /v1/invoices/draft/search와 같은 조건에 맞는 결과를 페이지를 나누지 않고 모두 내보냅니다. 최대 10,000행이며"
              + " 넘으면 400입니다.")
  @Parameters({
    @Parameter(
        name = "q",
        description = Q_CUSTOMER_ROWS,
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "sort",
        description = SORT_DRAFTS,
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "order",
        description = ORDER,
        in = ParameterIn.QUERY,
        schema =
            @Schema(
                type = "string",
                allowableValues = {"asc", "desc"})),
    @Parameter(
        name = "month",
        description = MONTH_DRAFTS,
        in = ParameterIn.QUERY,
        schema =
            @Schema(type = "string", pattern = "^[0-9]{4}-(0[1-9]|1[0-2])$", example = "2026-08")),
    @Parameter(
        name = "customer_id",
        description = CUSTOMER_ID_ROWS,
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", format = "uuid")),
    @Parameter(
        name = "billable_metric_code",
        description = METRIC_CODE_DRAFTS,
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "min_amount",
        description = MIN_DRAFTS,
        in = ParameterIn.QUERY,
        schema = @Schema(type = "integer", format = "int64", minimum = "0")),
    @Parameter(
        name = "max_amount",
        description = MAX_DRAFTS,
        in = ParameterIn.QUERY,
        schema = @Schema(type = "integer", format = "int64", minimum = "0"))
  })
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = SEARCH_CSV,
        content = @Content(mediaType = "text/csv", schema = @Schema(type = "string"))),
    @ApiResponse(
        responseCode = "400",
        description = INVALID_EXPORT,
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = CUSTOMER_NOT_FOUND,
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)))
  })
  public ResponseEntity<byte[]> exportDrafts(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(hidden = true) @RequestParam @StorableJson Map<String, String> parameters) {
    return searchCsvService.export(
        "draft-invoices",
        workspaceQueryService.drafts(
            organizationId, SearchParameters.parse(parameters, "drafts").forExport()),
        DraftInvoiceCustomer.class);
  }

  @GetMapping(value = "/v1/invoices/export", produces = "text/csv")
  @ReadAccess
  @Operation(
      summary = "청구서 내보내기",
      description =
          "GET /v1/invoices/search와 같은 조건에 맞는 결과를 페이지를 나누지 않고 모두 내보냅니다. 정렬 방향은 생략하면"
              + " desc입니다. 최대 10,000행이며 넘으면 400입니다.")
  @Parameters({
    @Parameter(
        name = "q",
        description = Q_INVOICES,
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "sort",
        description = SORT_INVOICES,
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "order",
        description = ORDER_INVOICES,
        in = ParameterIn.QUERY,
        schema =
            @Schema(
                type = "string",
                allowableValues = {"asc", "desc"})),
    @Parameter(
        name = "month",
        description = MONTH_INVOICES,
        in = ParameterIn.QUERY,
        schema =
            @Schema(type = "string", pattern = "^[0-9]{4}-(0[1-9]|1[0-2])$", example = "2026-08")),
    @Parameter(
        name = "customer_id",
        description = CUSTOMER_ID_INVOICES,
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", format = "uuid")),
    @Parameter(
        name = "billable_metric_code",
        description = METRIC_CODE_INVOICES,
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "from",
        description = FROM,
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "to",
        description = TO,
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "status",
        description = STATUS_INVOICES,
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "mode",
        description = MODE,
        in = ParameterIn.QUERY,
        schema =
            @Schema(
                type = "string",
                allowableValues = {"test", "live"})),
    @Parameter(
        name = "min_amount",
        description = MIN_INVOICES,
        in = ParameterIn.QUERY,
        schema = @Schema(type = "integer", format = "int64", minimum = "0")),
    @Parameter(
        name = "max_amount",
        description = MAX_INVOICES,
        in = ParameterIn.QUERY,
        schema = @Schema(type = "integer", format = "int64", minimum = "0"))
  })
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = SEARCH_CSV,
        content = @Content(mediaType = "text/csv", schema = @Schema(type = "string"))),
    @ApiResponse(
        responseCode = "400",
        description = INVALID_EXPORT,
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = CUSTOMER_NOT_FOUND,
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)))
  })
  public ResponseEntity<byte[]> exportInvoices(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(hidden = true) @RequestParam @StorableJson Map<String, String> parameters) {
    return searchCsvService.export(
        "invoices",
        workspaceQueryService.invoices(organizationId, invoiceParameters(parameters).forExport()),
        InvoiceSearchResponse.class);
  }

  private static SearchParameters invoiceParameters(Map<String, String> values) {
    Map<String, String> parameters = new LinkedHashMap<>(values);
    parameters.putIfAbsent("order", "desc");
    return SearchParameters.parse(parameters, "invoices");
  }
}
