package com.meterengine.invoice.controller;

import com.meterengine.global.error.ErrorResponse;
import com.meterengine.global.security.AdminAccess;
import com.meterengine.global.security.CurrentOrganizationId;
import com.meterengine.global.security.EnvironmentReady;
import com.meterengine.global.security.ReadAccess;
import com.meterengine.global.validation.FourDigitYear;
import com.meterengine.invoice.dto.FinalizeInvoicesResponse;
import com.meterengine.invoice.dto.InvoiceFinalizationResult;
import com.meterengine.invoice.dto.InvoiceResponse;
import com.meterengine.invoice.dto.ListInvoicesResponse;
import com.meterengine.invoice.service.InvoiceFinalizationService;
import com.meterengine.invoice.service.InvoiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.time.YearMonth;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@EnvironmentReady
@RequestMapping("/v1/invoices")
public class InvoiceController {

  private final InvoiceService invoiceService;
  private final InvoiceFinalizationService invoiceFinalizationService;

  InvoiceController(
      InvoiceService invoiceService, InvoiceFinalizationService invoiceFinalizationService) {
    this.invoiceService = invoiceService;
    this.invoiceFinalizationService = invoiceFinalizationService;
  }

  @GetMapping
  @ReadAccess
  @Operation(
      summary = "청구서 목록 조회",
      description =
          """
          확정된 청구서를 고객과 달마다 하나씩 돌려줍니다. 확정 전의 금액은 청구 예정액 조회로 봅니다.
          고객과 달로만 거르면 이 경로를 씁니다. 검색어, 여러 조건, 정렬, 페이지가 필요하면 GET /v1/invoices/search를 씁니다.
          customer_id와 month를 함께 주면 둘 다 만족하는 청구서만 나옵니다. month를 생략하면 모든 달입니다.
          달 내림차순이고, 같은 달은 고객 이름 오름차순, 이름이 같으면 고객 ID 순입니다.
          supply_amount, tax_amount, total_amount는 확정할 때 저장한 공급가액, 세액, 합계(공급가액 + 세액)이고 원 단위 정수입니다.
          lines는 확정할 때 저장한 구분 값별 수량, 단가, 금액이고 미터 코드 오름차순, 같은 미터 안에서는 dimension_values 오름차순입니다. 조회할 때 다시 계산하지 않아 뒤에 단가를 고쳐도 바뀌지 않습니다. 청구 항목 금액의 합이 supply_amount입니다.
          cutoff_at은 확정이 읽은 이벤트의 수신 시각(received_at) 상한입니다. 그 달에 발생했고 수신 시각이 이 시각보다 이른 이벤트만 금액에 들었습니다.
          cutoff_at과 finalized_at은 KST(+09:00)로 나옵니다. customer_name은 확정할 때 저장한 고객 이름이라 뒤에 이름을 고쳐도 바뀌지 않습니다. 페이지를 나누지 않습니다.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "청구서 목록입니다. 조건에 맞는 것이 없으면 invoices가 빈 배열입니다."),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description =
            "code=validation_error: customer_id가 UUID가 아니거나, month가 yyyy-MM이 아니거나 0001-01부터"
                + " 9999-12까지의 범위 밖입니다."),
    @ApiResponse(
        responseCode = "404",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=customer_not_found: customer_id가 가리키는 고객이 없습니다.")
  })
  public ListInvoicesResponse listInvoices(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(description = "이 고객의 청구서만 돌려줍니다. 생략하면 모든 고객이 대상입니다.")
          @RequestParam(name = "customer_id", required = false)
          UUID customerId,
      @Parameter(description = "청구서의 달(yyyy-MM, KST)입니다. 생략하면 모든 달입니다.", example = "2026-08")
          @RequestParam(required = false)
          @DateTimeFormat(pattern = "yyyy-MM")
          @FourDigitYear
          YearMonth month) {
    return invoiceService.list(organizationId, customerId, month);
  }

  @GetMapping("/{id}")
  @ReadAccess
  @Operation(
      summary = "청구서 조회",
      description =
          """
          확정된 청구서 하나를 돌려줍니다. 응답은 청구서 목록 조회에 나오는 같은 청구서와 같습니다.
          cutoff_at과 finalized_at은 KST(+09:00)로 나옵니다.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "청구서"),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=validation_error: 경로의 id가 UUID가 아닙니다."),
    @ApiResponse(
        responseCode = "404",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=invoice_not_found: 그런 청구서가 없습니다.")
  })
  public InvoiceResponse getInvoice(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(description = "청구서 ID(UUID)") @PathVariable UUID id) {
    return invoiceService.get(organizationId, id);
  }

  @PostMapping
  @AdminAccess
  @Operation(
      summary = "청구서 일괄 확정",
      description =
          """
          그 달 청구 예정액이 0원이 아닌 고객을 한 번에 확정합니다. 세액은 공급가액의 10%입니다.
          다음 달 1일 14:00 KST가 지나면 남은 고객은 자동으로 확정됩니다. 그 전에 확정하거나 자동 확정이 보류한 고객을 확정할 때 이 경로를 씁니다.
          그 달에 발생했고 집계 마감 시각(다음 달 1일 13:00 KST) 전에 받은 이벤트로 확정하므로, 마감이 지난 달의 확정 금액과 청구 항목은 그 시점의 청구 예정액과 같습니다.
          마감 시각 전에 확정하면 확정 시각까지 받은 이벤트로 확정하고 그 시각을 청구서의 cutoff_at에 남깁니다.
          수량이 0인 청구 항목은 청구서에 싣지 않습니다. 확정한 고객은 그 달 청구 예정액에서 빠집니다.
          그 달이 이미 확정된 고객은 건너뜁니다. 그래서 같은 달로 다시 부르거나 동시에 불러도 청구서가 겹치지 않습니다.
          고객마다 따로 확정합니다. 한 고객의 확정이 실패해도 다른 고객의 확정은 남고, 실패한 고객은 failed_count에 세며 다시 부르면 다시 시도합니다.
          확정은 되돌릴 수 없고, 확정한 뒤 그 달에 들어온 그 고객의 이벤트는 청구되지 않습니다.
          finalized_count는 이번 호출로 새로 확정한 청구서 수입니다. 확정된 내용은 청구서 목록 조회로 봅니다.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "확정한 달, 새로 확정한 청구서 수, 확정하지 못한 고객 수입니다. 확정할 고객이 없으면 두 수가 모두 0입니다."),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description =
            "code=validation_error: month가 없거나 yyyy-MM이 아니거나 0001-01부터 9999-12까지의 범위 밖입니다.")
  })
  public FinalizeInvoicesResponse finalizeInvoices(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(description = "확정할 달(yyyy-MM, KST)", example = "2026-08")
          @RequestParam
          @DateTimeFormat(pattern = "yyyy-MM")
          @FourDigitYear
          YearMonth month) {
    InvoiceFinalizationResult result = invoiceFinalizationService.finalize(organizationId, month);
    return FinalizeInvoicesResponse.from(
        month.toString(),
        result.invoices().stream()
            .map(invoice -> invoiceService.get(organizationId, invoice.getId()))
            .toList(),
        result.failedCount());
  }
}
