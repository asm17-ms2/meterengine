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
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Clock;
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
@Tag(name = "invoice-controller")
public class DraftInvoiceController {

  private final DraftInvoiceService draftInvoiceService;
  private final Clock clock;

  DraftInvoiceController(DraftInvoiceService draftInvoiceService, Clock clock) {
    this.draftInvoiceService = draftInvoiceService;
    this.clock = clock;
  }

  @GetMapping
  @ReadAccess
  @Operation(
      summary = "청구 예정액 조회",
      description =
          """
          청구서를 아직 발행하지 않은 고객의 청구 예정액을 과금 미터의 구분 값별 사용량에 그 구분 값의 단가를 곱해 계산해 돌려줍니다.
          달 하나의 모든 고객을 한 번에 보려면 이 경로를 씁니다. 검색어, 여러 조건, 정렬, 페이지가 필요하면 GET /v1/invoices/draft/search를 씁니다.
          그 달 청구서가 발행된 고객은 나오지 않고 total_amount는 이 달 청구서를 아직 발행하지 않은 고객의 합입니다. 발행된 금액은 청구서 목록 조회로 봅니다.
          조회할 때마다 계산하며 저장하지 않습니다. 기간은 KST 기준의 달입니다.
          그 달 이벤트 가운데 수신 시각(received_at)이 집계 마감 시각(다음 달 2일 00:00 KST)보다 이른 것만 집계합니다. 마감 뒤에 받은 그 달 이벤트는 저장되지만 집계와 청구에 들지 않습니다.
          금액은 원 단위 정수입니다. 고객, 과금 미터, 구분 값마다 생기는 청구 항목마다 10원 미만을 버린 뒤 더합니다. 10원이 안 되는 청구 항목은 0원입니다.
          청구 항목은 사용량이 있는 구분 값 가운데 같은 dimension_values의 단가가 있는 것만 나옵니다. 요금제가 걸린 고객은 사용량이 있으면 기본료와 최소 사용료 조정 항목도 나옵니다. 이벤트가 없는 고객은 lines가 빈 배열입니다.
          단가가 없는 구분 값은 청구 항목과 금액에서 빠지고, 수량이 0이 아니면 그 고객의 unpriced_usages에 과금 미터 코드, target_property, 구분 값, 수량으로 나옵니다. 순서는 청구 항목과 같습니다.
          고객은 이름 오름차순, 청구 항목은 과금 미터 코드 오름차순, 같은 과금 미터 안에서는 dimension_values 오름차순입니다. 페이지를 나누지 않습니다.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description =
            "고객별 청구 예정액입니다. 단가가 있는 사용량이 없고 요금제도 적용되지 않는 고객은 lines가 빈 배열입니다."
                + " 요금제가 있는 고객은 단가 없는 사용량만 있어도 기본료와 최소 사용료 조정 항목이 나옵니다."
                + " 모든 고객의 청구서가 발행됐으면 customers가 빈 배열이고 total_amount가 0입니다."),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=validation_error: month가 yyyy-MM이 아니거나 0001-01부터 9999-12까지의 범위 밖입니다.")
  })
  public DraftInvoiceResponse previewDraftInvoice(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(description = "집계할 달(yyyy-MM, KST)입니다. 생략하면 이번 달입니다.", example = "2026-08")
          @RequestParam(required = false)
          @DateTimeFormat(pattern = "yyyy-MM")
          @FourDigitYear
          YearMonth month) {
    YearMonth targetMonth = month == null ? BillableMetricUsageService.currentMonth(clock) : month;
    return draftInvoiceService.preview(organizationId, targetMonth);
  }
}
