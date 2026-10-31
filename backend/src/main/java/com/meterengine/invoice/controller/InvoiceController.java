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
      summary = "인보이스 목록 조회",
      description =
          """
          확정된 인보이스를 (고객, 달)마다 한 줄씩 돌려준다. 확정 전의 금액은 청구 예정액 조회로 본다.
          customer_id와 month를 함께 주면 둘 다 만족하는 인보이스만 나온다. month를 생략하면 모든 달이다.
          달 내림차순이고, 같은 달은 고객 이름 오름차순, 이름이 같으면 고객 id 순이다.
          supply_amount, tax_amount, total_amount는 확정할 때 저장한 공급가액, 세액, 합계(공급가액 + 세액)이고 원 단위 정수다.
          lines는 확정할 때 저장한 조합별 수량, 단가, 금액이고 미터 code 오름차순, 같은 미터 안에서는 dimension_values 오름차순이다. 조회할 때 다시 계산하지 않아 뒤에 단가를 고쳐도 바뀌지 않는다. 라인 금액의 합이 supply_amount다.
          cutoff_at은 확정이 읽은 이벤트의 받은 시각 상한이다. 그 달에 발생했고 이 시각 전에 받은 이벤트만 금액에 들었다.
          cutoff_at과 finalized_at은 KST(+09:00)로 나온다. customer_name은 확정할 때 저장한 고객 이름이라 뒤에 이름을 고쳐도 바뀌지 않는다. 페이지를 나누지 않는다.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "인보이스 목록. 조건에 맞는 것이 없으면 invoices가 빈 배열이다"),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description =
            "code=validation_error: customer_id가 UUID가 아니거나, month가 yyyy-MM이 아니거나 0001-01부터"
                + " 9999-12까지의 범위 밖이다"),
    @ApiResponse(
        responseCode = "404",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=customer_not_found: customer_id가 가리키는 고객이 없다")
  })
  public ListInvoicesResponse listInvoices(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(description = "고객을 좁힌다. 생략하면 모든 고객이 대상이다.")
          @RequestParam(name = "customer_id", required = false)
          UUID customerId,
      @Parameter(description = "인보이스의 달(yyyy-MM, KST). 생략하면 모든 달이다.", example = "2026-08")
          @RequestParam(required = false)
          @DateTimeFormat(pattern = "yyyy-MM")
          @FourDigitYear
          YearMonth month) {
    return invoiceService.list(organizationId, customerId, month);
  }

  @GetMapping("/{id}")
  @ReadAccess
  @Operation(
      summary = "인보이스 조회",
      description =
          """
          확정된 인보이스 하나를 돌려준다. 응답은 인보이스 목록 조회의 같은 인보이스 원소와 같다.
          cutoff_at과 finalized_at은 KST(+09:00)로 나온다.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "인보이스"),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=validation_error: 경로의 id가 UUID가 아니다"),
    @ApiResponse(
        responseCode = "404",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=invoice_not_found: 그런 인보이스가 없다")
  })
  public InvoiceResponse getInvoice(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(description = "인보이스의 id.") @PathVariable UUID id) {
    return invoiceService.get(organizationId, id);
  }

  @PostMapping
  @AdminAccess
  @Operation(
      summary = "인보이스 일괄 확정",
      description =
          """
          그 달 청구 예정액이 0원이 아닌 고객을 한 번에 확정한다. 세액은 공급가액의 10%다.
          그 달에 발생했고 집계 마감 시각(다음 달 1일 13:00 KST) 전에 받은 이벤트로 확정해서, 마감이 지난 달의 확정 금액과 라인은 그 시점의 청구 예정액과 같다.
          마감 시각 전에 확정하면 확정 시각까지 받은 이벤트로 확정하고 그 시각을 인보이스의 cutoff_at에 남긴다.
          수량이 0인 라인은 인보이스에 싣지 않는다. 확정한 고객은 그 달 청구 예정액에서 빠진다.
          확정한 고객마다 같은 트랜잭션에서 보낼 청구 메일을 남긴다. 고객에 청구 이메일이 없으면 받는 주소 없이 남긴다.
          그 달이 이미 확정된 고객은 건너뛴다. 그래서 같은 달로 다시 부르거나 동시에 불러도 인보이스가 겹치지 않는다.
          고객마다 따로 커밋한다. 한 고객의 확정이 실패해도 다른 고객의 확정은 남고, 실패한 고객은 failed_count에 세며 다시 부르면 다시 시도한다.
          확정은 되돌릴 수 없고, 확정한 뒤 그 달에 들어온 그 고객의 이벤트는 청구되지 않는다.
          finalized_count는 이번 호출로 새로 확정한 인보이스 수다. 확정된 내용은 인보이스 목록 조회로 본다.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "확정한 달, 새로 확정한 인보이스 수, 확정하지 못한 고객 수. 확정할 고객이 없으면 둘 다 0이다"),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=validation_error: month가 없거나 yyyy-MM이 아니거나 0001-01부터 9999-12까지의 범위 밖이다")
  })
  public FinalizeInvoicesResponse finalizeInvoices(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(description = "확정할 달(yyyy-MM, KST).", example = "2026-08")
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
