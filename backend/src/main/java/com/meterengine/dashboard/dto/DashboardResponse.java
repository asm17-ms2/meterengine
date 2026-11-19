package com.meterengine.dashboard.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.invoice.dto.DraftInvoiceResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record DashboardResponse(
    @Schema(description = "조회한 달(yyyy-MM, KST)") String month,
    @Schema(description = "현황을 계산한 시각(ISO 8601)") @JsonProperty("calculated_at")
        OffsetDateTime calculatedAt,
    @Schema(description = "보관하지 않은 고객 수") @JsonProperty("customer_count") long customerCount,
    @Schema(description = "보관하지 않은 과금 미터 수") @JsonProperty("billable_metric_count")
        long billableMetricCount,
    @Schema(description = "폐기하지 않았고 만료되지 않은 API 키 수") @JsonProperty("active_api_key_count")
        long activeApiKeyCount,
    @Schema(description = "조회한 달에 발생한 이벤트 수(occurred_at 기준, KST)") @JsonProperty("event_count")
        long eventCount,
    @Schema(description = "공급자 정보(사업자등록번호, 대표자명, 사업장 주소)를 모두 저장했으면 true입니다.")
        @JsonProperty("supplier_configured")
        boolean supplierConfigured,
    @Schema(description = "payment_mode에 맞는 토스페이먼츠 키를 저장했으면 true입니다.")
        @JsonProperty("payment_configured")
        boolean paymentConfigured,
    @Schema(description = "현재 업무 환경의 결제 모드(test 또는 live)") @JsonProperty("payment_mode")
        String paymentMode,
    @Schema(description = "청구 예정액(원)입니다. 그 달을 아직 확정하지 않은 고객의 공급가액 합계이며 부가세는 들지 않습니다.")
        @JsonProperty("draft_supply_amount")
        long draftSupplyAmount,
    @Schema(description = "확정 청구액(원)입니다. 그 달 청구서의 부가세 포함 금액 합계입니다.")
        @JsonProperty("finalized_total_amount")
        long finalizedTotalAmount,
    @Schema(
            description =
                "받은 금액(원)입니다. 결제를 마친 청구서(paid, partially_canceled, canceled)의 청구액에서 취소한 금액을 뺀 합계입니다.")
        @JsonProperty("collected_amount")
        long collectedAmount,
    @Schema(description = "그 달 청구서에서 취소를 마친 금액의 합계(원)") @JsonProperty("canceled_amount")
        long canceledAmount,
    @Schema(
            description =
                "받을 금액(원)입니다. 선택한 달 청구서 가운데 결제 전(unpaid), 결과 확인 중(pending), 결제 실패(failed)인 청구서의 청구액 합계입니다."
                    + " 청구서 검색의 outstanding_amount는 결과 확인 중이 있으면 null이고 결제 전과 결제 실패만 더해 뜻이 다릅니다.")
        @JsonProperty("outstanding_amount")
        long outstandingAmount,
    @Schema(description = "그 달 청구서 가운데 결제 상태가 failed인 청구서 수") @JsonProperty("failed_payment_count")
        long failedPaymentCount,
    @Schema(description = "단가 없는 사용량 구분 값 수입니다. 청구 예정액에서 단가가 없어 빠진 고객, 과금 미터, 구분 값 묶음을 셉니다.")
        @JsonProperty("unpriced_usage_count")
        long unpricedUsageCount,
    @Schema(description = "집계값이 음수인 구분 값 수입니다. 청구 예정액에서 집계값이 음수라 빠진 고객, 과금 미터, 구분 값 묶음을 셉니다.")
        @JsonProperty("negative_usage_count")
        long negativeUsageCount,
    @Schema(description = "그 달 청구서별 결제 현황") @JsonProperty("invoice_payments")
        List<DashboardInvoice> invoicePayments,
    @Schema(description = "달과 관계없이 결제 전, 결과 확인 중, 결제 실패인 청구서의 결제 현황")
        @JsonProperty("unresolved_invoice_payments")
        List<DashboardInvoice> unresolvedInvoicePayments,
    @Schema(description = "그 달의 청구 예정액 상세입니다. GET /v1/invoices/draft 응답과 같은 형태입니다.")
        @JsonProperty("draft_invoice")
        DraftInvoiceResponse draftInvoice) {

  public record DashboardInvoice(
      @Schema(description = "청구서 ID(UUID)") @JsonProperty("invoice_id") UUID invoiceId,
      @Schema(description = "고객 ID(UUID)") @JsonProperty("customer_id") UUID customerId,
      @Schema(description = "확정할 때 저장한 고객 이름") @JsonProperty("customer_name") String customerName,
      @Schema(description = "청구 월(yyyy-MM, KST)") String month,
      @Schema(
              description =
                  "결제 상태입니다. not_required, unpaid, pending, paid, failed, partially_canceled, canceled 가운데 하나이며 뜻은 청구서 결제 현황의 status와 같습니다.")
          String status,
      @Schema(description = "status를 계산한 결제 모드(test 또는 live)") String mode,
      @Schema(description = "청구서의 부가세 포함 금액(원)") long amount,
      @Schema(description = "취소를 마친 금액의 합(원)입니다. 결제된 청구서가 아니면 0입니다.")
          @JsonProperty("canceled_amount")
          long canceledAmount,
      @Schema(description = "이 결제 모드로 한 결제 시도 수") @JsonProperty("attempt_count") long attemptCount,
      @Schema(description = "가장 늦게 요청한 시도가 failed일 때 그 시도의 실패 코드입니다. 그 밖에는 null입니다.")
          @JsonProperty("last_failure_code")
          String lastFailureCode,
      @Schema(description = "다음 자동 재시도 예정 시각(KST, +09:00)입니다. 다시 시도할 대상이 아니면 null입니다.")
          @JsonProperty("next_retry_at")
          OffsetDateTime nextRetryAt,
      @Schema(description = "가장 늦게 요청한 시도가 끝난 시각(KST, +09:00)입니다. 시도가 없거나 결과 확인 중이면 null입니다.")
          @JsonProperty("completed_at")
          OffsetDateTime completedAt) {}
}
