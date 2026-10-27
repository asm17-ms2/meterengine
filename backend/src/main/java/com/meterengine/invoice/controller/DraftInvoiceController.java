package com.meterengine.invoice.controller;

import com.meterengine.global.error.ErrorResponse;
import com.meterengine.global.security.CurrentOrganizationId;
import com.meterengine.global.security.EnvironmentReady;
import com.meterengine.global.security.ReadAccess;
import com.meterengine.global.validation.FourDigitYear;
import com.meterengine.invoice.dto.DraftInvoiceResponse;
import com.meterengine.invoice.service.DraftInvoiceService;
import com.meterengine.metric.service.BillableMetricUsageService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@EnvironmentReady
@RequestMapping("/v1/invoices/draft")
public class DraftInvoiceController {

  private final DraftInvoiceService draftInvoiceService;

  DraftInvoiceController(DraftInvoiceService draftInvoiceService) {
    this.draftInvoiceService = draftInvoiceService;
  }

  @GetMapping
  @ReadAccess
  @Operation(
      summary = "청구 예정액 조회",
      description =
          """
          아직 확정하지 않은 고객의 청구 예정액을 미터의 속성 값 조합별 사용량 x 그 조합의 단가로 계산해 돌려준다.
          그 달이 확정된 고객은 나오지 않고 total_amount는 남은 고객의 합이다. 확정된 금액은 인보이스 목록 조회로 본다.
          조회할 때마다 계산하며 저장하지 않는다. 기간은 KST 기준의 달이다.
          금액은 원 단위 정수다. 라인(고객 x 미터 x 조합)마다 10원 미만을 버린 뒤 합산한다. 10원이 안 되는 라인은 0원이다.
          라인은 사용량이 있는 조합 가운데 같은 dimension_values의 단가가 있는 것만 나온다. 이벤트가 없는 고객은 lines가 빈 배열이다.
          단가가 없는 조합은 라인과 금액에서 빠지고, 수량이 0이 아니면 그 고객의 unpriced_usages에 미터 code, target_property, 조합, 수량으로 나온다. 순서는 라인과 같다.
          고객은 이름 오름차순, 라인은 미터 code 오름차순, 같은 미터 안에서는 dimension_values 오름차순이다.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description =
            "고객별 청구 예정액. 단가가 있는 조합의 사용량이 없는 고객은 lines가 빈 배열이다. 모든 고객이 확정됐으면 customers가 빈 배열이고"
                + " total_amount가 0이다"),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=validation_error: month가 yyyy-MM이 아니거나 0001-01부터 9999-12까지의 범위 밖이다")
  })
  public DraftInvoiceResponse previewDraftInvoice(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(description = "집계할 달(yyyy-MM, KST). 생략하면 이번 달이다.", example = "2026-08")
          @RequestParam(required = false)
          @DateTimeFormat(pattern = "yyyy-MM")
          @FourDigitYear
          YearMonth month) {
    YearMonth targetMonth = month == null ? BillableMetricUsageService.currentMonth() : month;
    return draftInvoiceService.preview(organizationId, targetMonth);
  }
}
