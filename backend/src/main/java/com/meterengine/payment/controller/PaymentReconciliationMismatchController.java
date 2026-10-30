package com.meterengine.payment.controller;

import com.meterengine.global.error.ErrorResponse;
import com.meterengine.global.security.CurrentOrganizationId;
import com.meterengine.global.security.EnvironmentReady;
import com.meterengine.global.security.ReadAccess;
import com.meterengine.global.validation.FourDigitYear;
import com.meterengine.payment.dto.ListPaymentReconciliationMismatchesResponse;
import com.meterengine.payment.service.PaymentReconciliationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@EnvironmentReady
public class PaymentReconciliationMismatchController {

  private final PaymentReconciliationService paymentReconciliationService;

  PaymentReconciliationMismatchController(
      PaymentReconciliationService paymentReconciliationService) {
    this.paymentReconciliationService = paymentReconciliationService;
  }

  @GetMapping("/v1/payment-reconciliation-mismatches")
  @ReadAccess
  @Operation(
      summary = "결제 대사 불일치 목록",
      description =
          """
          하루치 토스페이먼츠 거래 목록과 MeterEngine의 done 결제 시도, done 취소를 맞춰 보고 남긴 불일치를 돌려준다. 선택한 환경의 기록만 담고, 기존 범위에서는 두 결제 모드를 담는다.
          기간은 불일치를 찾은 시각(detected_at)의 KST 날짜로 거르고, from 날짜 0시부터 to 날짜가 끝날 때까지다.
          detected_at 내림차순이다. transacted_at과 detected_at은 KST(+09:00)로 나온다. 페이지를 나누지 않는다.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "불일치 목록. 기간 안에 불일치가 없으면 payment_reconciliation_mismatches가 빈 배열이다"),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description =
            "code=validation_error: from이나 to가 없거나 yyyy-MM-dd가 아니거나 0001-01-01부터 9999-12-31까지의 범위"
                + " 밖이거나, from이 to보다 늦다")
  })
  public ListPaymentReconciliationMismatchesResponse listPaymentReconciliationMismatches(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(description = "기간의 첫날(yyyy-MM-dd, KST)", example = "2026-10-01")
          @RequestParam
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          @FourDigitYear
          LocalDate from,
      @Parameter(description = "기간의 마지막 날(yyyy-MM-dd, KST). 이날 전부를 담는다", example = "2026-10-31")
          @RequestParam
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          @FourDigitYear
          LocalDate to) {
    return paymentReconciliationService.list(organizationId, from, to);
  }
}
