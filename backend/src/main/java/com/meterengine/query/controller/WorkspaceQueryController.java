package com.meterengine.query.controller;

import com.meterengine.customer.dto.CustomerResponse;
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
import io.swagger.v3.oas.annotations.media.Schema;
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
      summary = "고객 검색과 서버 페이지 조회",
      description =
          "q, external_id, customer_id, status(active/archived/all),"
              + " sort(name/external_id/created_at/archived_at/id/billing_email), order, page,"
              + " size를 받는다. total은 조건 전체, total_unfiltered는 현재 조직과 환경의 전체 건수다. 페이지는 1부터, size 기본"
              + " 25/최대 200이다.")
  @Parameters({
    @Parameter(
        name = "q",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "sort",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "order",
        in = ParameterIn.QUERY,
        schema =
            @Schema(
                type = "string",
                allowableValues = {"asc", "desc"})),
    @Parameter(
        name = "page",
        in = ParameterIn.QUERY,
        schema =
            @Schema(type = "integer", minimum = "1", maximum = "2147483647", defaultValue = "1")),
    @Parameter(
        name = "size",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "integer", minimum = "1", maximum = "200", defaultValue = "25")),
    @Parameter(
        name = "status",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "external_id",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "customer_id",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", format = "uuid"))
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
      summary = "미터 검색과 서버 페이지 조회",
      description =
          "q, billable_metric_code, status(active/archived/all),"
              + " sort(code/name/event_type/aggregation/archived_at), order, page, size를 받는다.")
  @Parameters({
    @Parameter(
        name = "q",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "sort",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "order",
        in = ParameterIn.QUERY,
        schema =
            @Schema(
                type = "string",
                allowableValues = {"asc", "desc"})),
    @Parameter(
        name = "page",
        in = ParameterIn.QUERY,
        schema =
            @Schema(type = "integer", minimum = "1", maximum = "2147483647", defaultValue = "1")),
    @Parameter(
        name = "size",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "integer", minimum = "1", maximum = "200", defaultValue = "25")),
    @Parameter(
        name = "status",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "billable_metric_code",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000))
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
      summary = "월별 가격 검색과 서버 페이지 조회",
      description =
          "month, customer_id, billable_metric_code, q, status(미터 보관 상태),"
              + " sort(code/name/rate_count/archived_at), order, page, size를 받는다. 미터 이름과 단가 수, 조건"
              + " 전체 no_rate_count를 포함한다.")
  @Parameters({
    @Parameter(
        name = "q",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "sort",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "order",
        in = ParameterIn.QUERY,
        schema =
            @Schema(
                type = "string",
                allowableValues = {"asc", "desc"})),
    @Parameter(
        name = "page",
        in = ParameterIn.QUERY,
        schema =
            @Schema(type = "integer", minimum = "1", maximum = "2147483647", defaultValue = "1")),
    @Parameter(
        name = "size",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "integer", minimum = "1", maximum = "200", defaultValue = "25")),
    @Parameter(
        name = "status",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "month",
        in = ParameterIn.QUERY,
        schema =
            @Schema(type = "string", pattern = "^[0-9]{4}-(0[1-9]|1[0-2])$", example = "2026-08")),
    @Parameter(
        name = "customer_id",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", format = "uuid")),
    @Parameter(
        name = "billable_metric_code",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000))
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
      summary = "고객 단위 사용량 검색과 서버 페이지 조회",
      description =
          "month, customer_id, billable_metric_code, q, status(고객 보관 상태, 기본 all),"
              + " sort(customer_name/customer_id/line_count), order, page, size를 받는다. 한 고객의 모든 미터를"
              + " 같은 페이지에 둔다.")
  @Parameters({
    @Parameter(
        name = "q",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "sort",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "order",
        in = ParameterIn.QUERY,
        schema =
            @Schema(
                type = "string",
                allowableValues = {"asc", "desc"})),
    @Parameter(
        name = "page",
        in = ParameterIn.QUERY,
        schema =
            @Schema(type = "integer", minimum = "1", maximum = "2147483647", defaultValue = "1")),
    @Parameter(
        name = "size",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "integer", minimum = "1", maximum = "200", defaultValue = "25")),
    @Parameter(
        name = "status",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "month",
        in = ParameterIn.QUERY,
        schema =
            @Schema(type = "string", pattern = "^[0-9]{4}-(0[1-9]|1[0-2])$", example = "2026-08")),
    @Parameter(
        name = "customer_id",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", format = "uuid")),
    @Parameter(
        name = "billable_metric_code",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000))
  })
  public SearchPageResponse<UsageCustomerResponse> searchUsage(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(hidden = true) @RequestParam @StorableJson Map<String, String> parameters) {
    return workspaceQueryService.usage(organizationId, SearchParameters.parse(parameters, "usage"));
  }

  @GetMapping("/v1/invoices/draft/search")
  @ReadAccess
  @Operation(
      summary = "청구 예정액 검색과 페이지 독립 요약",
      description =
          "month, customer_id, billable_metric_code, q, min_amount, max_amount,"
              + " sort(customer_name/customer_id/amount/line_count), order, page, size를 받는다."
              + " amount는 선택 조건 전체 합계이며 month_amount는 월 전체다. 기존 확정 요청은 검색 조건과 관계없이 월 전체를 확정하므로"
              + " finalization_scope는 month다.")
  @Parameters({
    @Parameter(
        name = "q",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "sort",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "order",
        in = ParameterIn.QUERY,
        schema =
            @Schema(
                type = "string",
                allowableValues = {"asc", "desc"})),
    @Parameter(
        name = "page",
        in = ParameterIn.QUERY,
        schema =
            @Schema(type = "integer", minimum = "1", maximum = "2147483647", defaultValue = "1")),
    @Parameter(
        name = "size",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "integer", minimum = "1", maximum = "200", defaultValue = "25")),
    @Parameter(
        name = "month",
        in = ParameterIn.QUERY,
        schema =
            @Schema(type = "string", pattern = "^[0-9]{4}-(0[1-9]|1[0-2])$", example = "2026-08")),
    @Parameter(
        name = "customer_id",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", format = "uuid")),
    @Parameter(
        name = "billable_metric_code",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "min_amount",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "integer", format = "int64", minimum = "0")),
    @Parameter(
        name = "max_amount",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "integer", format = "int64", minimum = "0"))
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
      summary = "인보이스와 결제 상태 검색",
      description =
          "customer_id, month, from/to(확정 시각, 날짜 종료일 포함/KST), status, mode, min_amount, max_amount,"
              + " billable_metric_code, q,"
              + " sort(month/customer_name/total_amount/finalized_at/invoice_number/status), order,"
              + " page, size를 받는다. mode 기본은 현재 결제 설정이다. 조건 전체 금액과 결제 상태 건수는 페이지와 독립이며 pending이 있으면"
              + " outstanding_amount는 null이다.")
  @Parameters({
    @Parameter(
        name = "q",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "sort",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "order",
        in = ParameterIn.QUERY,
        schema =
            @Schema(
                type = "string",
                allowableValues = {"asc", "desc"})),
    @Parameter(
        name = "page",
        in = ParameterIn.QUERY,
        schema =
            @Schema(type = "integer", minimum = "1", maximum = "2147483647", defaultValue = "1")),
    @Parameter(
        name = "size",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "integer", minimum = "1", maximum = "200", defaultValue = "25")),
    @Parameter(
        name = "month",
        in = ParameterIn.QUERY,
        schema =
            @Schema(type = "string", pattern = "^[0-9]{4}-(0[1-9]|1[0-2])$", example = "2026-08")),
    @Parameter(
        name = "customer_id",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", format = "uuid")),
    @Parameter(
        name = "billable_metric_code",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "from",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "to",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "status",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "mode",
        in = ParameterIn.QUERY,
        schema =
            @Schema(
                type = "string",
                allowableValues = {"test", "live"})),
    @Parameter(
        name = "min_amount",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "integer", format = "int64", minimum = "0")),
    @Parameter(
        name = "max_amount",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "integer", format = "int64", minimum = "0"))
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
          "고객 검색과 같은 조건을 받으며 value/label/archived와 페이지 정보를 반환한다. 보관 고객 선택에는 status=all을 사용한다.")
  @Parameters({
    @Parameter(
        name = "q",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "sort",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "order",
        in = ParameterIn.QUERY,
        schema =
            @Schema(
                type = "string",
                allowableValues = {"asc", "desc"})),
    @Parameter(
        name = "page",
        in = ParameterIn.QUERY,
        schema =
            @Schema(type = "integer", minimum = "1", maximum = "2147483647", defaultValue = "1")),
    @Parameter(
        name = "size",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "integer", minimum = "1", maximum = "200", defaultValue = "25")),
    @Parameter(
        name = "status",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "external_id",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "customer_id",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", format = "uuid"))
  })
  public SearchPageResponse<OptionResponse> suggestCustomers(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(hidden = true) @RequestParam @StorableJson Map<String, String> parameters) {
    return workspaceQueryService.suggest(
        organizationId, SearchParameters.parse(parameters, "customers"), "customers");
  }

  @GetMapping("/v1/billable-metrics/options")
  @ReadAccess
  @Operation(summary = "미터 선택지 검색")
  @Parameters({
    @Parameter(
        name = "q",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "sort",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "order",
        in = ParameterIn.QUERY,
        schema =
            @Schema(
                type = "string",
                allowableValues = {"asc", "desc"})),
    @Parameter(
        name = "page",
        in = ParameterIn.QUERY,
        schema =
            @Schema(type = "integer", minimum = "1", maximum = "2147483647", defaultValue = "1")),
    @Parameter(
        name = "size",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "integer", minimum = "1", maximum = "200", defaultValue = "25")),
    @Parameter(
        name = "status",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "billable_metric_code",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000))
  })
  public SearchPageResponse<OptionResponse> suggestBillableMetrics(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(hidden = true) @RequestParam @StorableJson Map<String, String> parameters) {
    return workspaceQueryService.suggest(
        organizationId, SearchParameters.parse(parameters, "metrics"), "metrics");
  }

  @GetMapping("/v1/usage/months")
  @ReadAccess
  @Operation(summary = "이벤트가 있는 월 목록", description = "KST 발생 시각 기준의 월을 중복 없이 내림차순으로 반환한다.")
  public Map<String, List<String>> listUsageMonths(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(hidden = true) @RequestParam @StorableJson Map<String, String> parameters) {
    UnknownQueryParameterValidator.validate(parameters, Set.of());
    return workspaceQueryService.list(organizationId, "usage");
  }

  @GetMapping("/v1/invoices/months")
  @ReadAccess
  @Operation(summary = "확정 인보이스가 있는 월 목록")
  public Map<String, List<String>> listInvoiceMonths(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(hidden = true) @RequestParam @StorableJson Map<String, String> parameters) {
    UnknownQueryParameterValidator.validate(parameters, Set.of());
    return workspaceQueryService.list(organizationId, "invoices");
  }

  @GetMapping("/v1/billable-metric-prices/months")
  @ReadAccess
  @Operation(summary = "단가 버전이 시작된 월 목록")
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
      summary = "고객 검색 결과 CSV",
      description = "현재 페이지가 아니라 같은 검색 조건의 전체 결과를 내보낸다. 최대 10000행이며 초과하면 범위를 좁히도록 400을 반환한다.")
  @Parameters({
    @Parameter(
        name = "q",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "sort",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "order",
        in = ParameterIn.QUERY,
        schema =
            @Schema(
                type = "string",
                allowableValues = {"asc", "desc"})),
    @Parameter(
        name = "status",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "external_id",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "customer_id",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", format = "uuid"))
  })
  public ResponseEntity<byte[]> exportCustomers(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(hidden = true) @RequestParam @StorableJson Map<String, String> parameters) {
    return searchCsvService.export(
        "customers",
        workspaceQueryService.search(
            organizationId, SearchParameters.parse(parameters, "customers").forExport()));
  }

  @GetMapping(value = "/v1/billable-metrics/export", produces = "text/csv")
  @ReadAccess
  @Operation(summary = "미터 검색 결과 CSV")
  @Parameters({
    @Parameter(
        name = "q",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "sort",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "order",
        in = ParameterIn.QUERY,
        schema =
            @Schema(
                type = "string",
                allowableValues = {"asc", "desc"})),
    @Parameter(
        name = "status",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "billable_metric_code",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000))
  })
  public ResponseEntity<byte[]> exportMetrics(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(hidden = true) @RequestParam @StorableJson Map<String, String> parameters) {
    return searchCsvService.export(
        "billable-metrics",
        workspaceQueryService.metrics(
            organizationId, SearchParameters.parse(parameters, "metrics").forExport()));
  }

  @GetMapping(value = "/v1/billable-metric-prices/export", produces = "text/csv")
  @ReadAccess
  @Operation(summary = "미터별 가격 검색 결과 CSV")
  @Parameters({
    @Parameter(
        name = "q",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "sort",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "order",
        in = ParameterIn.QUERY,
        schema =
            @Schema(
                type = "string",
                allowableValues = {"asc", "desc"})),
    @Parameter(
        name = "status",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "month",
        in = ParameterIn.QUERY,
        schema =
            @Schema(type = "string", pattern = "^[0-9]{4}-(0[1-9]|1[0-2])$", example = "2026-08")),
    @Parameter(
        name = "customer_id",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", format = "uuid")),
    @Parameter(
        name = "billable_metric_code",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000))
  })
  public ResponseEntity<byte[]> exportPrices(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(hidden = true) @RequestParam @StorableJson Map<String, String> parameters) {
    return searchCsvService.export(
        "billable-metric-prices",
        workspaceQueryService.prices(
            organizationId, SearchParameters.parse(parameters, "prices").forExport()));
  }

  @GetMapping(value = "/v1/usage/export", produces = "text/csv")
  @ReadAccess
  @Operation(summary = "고객별 사용량 검색 결과 CSV")
  @Parameters({
    @Parameter(
        name = "q",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "sort",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "order",
        in = ParameterIn.QUERY,
        schema =
            @Schema(
                type = "string",
                allowableValues = {"asc", "desc"})),
    @Parameter(
        name = "status",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "month",
        in = ParameterIn.QUERY,
        schema =
            @Schema(type = "string", pattern = "^[0-9]{4}-(0[1-9]|1[0-2])$", example = "2026-08")),
    @Parameter(
        name = "customer_id",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", format = "uuid")),
    @Parameter(
        name = "billable_metric_code",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000))
  })
  public ResponseEntity<byte[]> exportUsage(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(hidden = true) @RequestParam @StorableJson Map<String, String> parameters) {
    return searchCsvService.export(
        "usage",
        workspaceQueryService.usage(
            organizationId, SearchParameters.parse(parameters, "usage").forExport()));
  }

  @GetMapping(value = "/v1/invoices/draft/export", produces = "text/csv")
  @ReadAccess
  @Operation(summary = "청구 예정액 검색 결과 CSV")
  @Parameters({
    @Parameter(
        name = "q",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "sort",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "order",
        in = ParameterIn.QUERY,
        schema =
            @Schema(
                type = "string",
                allowableValues = {"asc", "desc"})),
    @Parameter(
        name = "month",
        in = ParameterIn.QUERY,
        schema =
            @Schema(type = "string", pattern = "^[0-9]{4}-(0[1-9]|1[0-2])$", example = "2026-08")),
    @Parameter(
        name = "customer_id",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", format = "uuid")),
    @Parameter(
        name = "billable_metric_code",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "min_amount",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "integer", format = "int64", minimum = "0")),
    @Parameter(
        name = "max_amount",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "integer", format = "int64", minimum = "0"))
  })
  public ResponseEntity<byte[]> exportDrafts(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(hidden = true) @RequestParam @StorableJson Map<String, String> parameters) {
    return searchCsvService.export(
        "draft-invoices",
        workspaceQueryService.drafts(
            organizationId, SearchParameters.parse(parameters, "drafts").forExport()));
  }

  @GetMapping(value = "/v1/invoices/export", produces = "text/csv")
  @ReadAccess
  @Operation(summary = "인보이스 검색 결과 CSV")
  @Parameters({
    @Parameter(
        name = "q",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "sort",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "order",
        in = ParameterIn.QUERY,
        schema =
            @Schema(
                type = "string",
                allowableValues = {"asc", "desc"})),
    @Parameter(
        name = "month",
        in = ParameterIn.QUERY,
        schema =
            @Schema(type = "string", pattern = "^[0-9]{4}-(0[1-9]|1[0-2])$", example = "2026-08")),
    @Parameter(
        name = "customer_id",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", format = "uuid")),
    @Parameter(
        name = "billable_metric_code",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "from",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "to",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "status",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", maxLength = 2000)),
    @Parameter(
        name = "mode",
        in = ParameterIn.QUERY,
        schema =
            @Schema(
                type = "string",
                allowableValues = {"test", "live"})),
    @Parameter(
        name = "min_amount",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "integer", format = "int64", minimum = "0")),
    @Parameter(
        name = "max_amount",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "integer", format = "int64", minimum = "0"))
  })
  public ResponseEntity<byte[]> exportInvoices(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(hidden = true) @RequestParam @StorableJson Map<String, String> parameters) {
    return searchCsvService.export(
        "invoices",
        workspaceQueryService.invoices(organizationId, invoiceParameters(parameters).forExport()));
  }

  private static SearchParameters invoiceParameters(Map<String, String> values) {
    Map<String, String> parameters = new LinkedHashMap<>(values);
    parameters.putIfAbsent("order", "desc");
    return SearchParameters.parse(parameters, "invoices");
  }
}
