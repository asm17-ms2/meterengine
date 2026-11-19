package com.meterengine.invoiceview.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.invoice.dto.InvoiceResponse.InvoiceLineResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;

public record PublicInvoiceResponse(
    @Schema(description = "청구하는 조직의 이름") @JsonProperty("organization_name") String organizationName,
    @Schema(description = "발행할 때 저장한 고객 이름") @JsonProperty("customer_name") String customerName,
    @Schema(description = "청구서 번호입니다. ME-YYYYMM-8자리 숫자 꼴이고, 세금계산서나 영수증 번호가 아닙니다.")
        @JsonProperty("invoice_number")
        String invoiceNumber,
    @Schema(description = "청구 월(yyyy-MM, KST)") String month,
    @Schema(description = "청구서를 발행한 시각(KST, +09:00)입니다. 고객 화면에는 '발행 시각'으로 나옵니다.")
        @JsonProperty("finalized_at")
        OffsetDateTime finalizedAt,
    @Schema(description = "공급가액(원)") @JsonProperty("supply_amount") long supplyAmount,
    @Schema(description = "부가세(원)") @JsonProperty("tax_amount") long taxAmount,
    @Schema(description = "부가세 포함 금액(원)") @JsonProperty("total_amount") long totalAmount,
    @Schema(description = "청구 항목입니다. 발행할 때의 기록이라 이후 요금을 바꾸거나 결제를 취소해도 달라지지 않습니다.")
        List<InvoiceLineResponse> lines,
    @Schema(description = "링크의 결제 모드로 본 결제 내역") PublicInvoicePayment payment,
    @Schema(description = "고객이 이 링크로 실패한 결제를 다시 시도할 수 있는지와 그 이유") @JsonProperty("payment_retry")
        PublicInvoicePaymentRetry paymentRetry,
    @Schema(description = "링크가 만료되는 시각(KST, +09:00)") @JsonProperty("expires_at")
        OffsetDateTime expiresAt) {
  public record PublicInvoicePaymentRetry(
      @Schema(description = "지금 다시 시도할 수 있으면 true") boolean allowed,
      @Schema(
              description =
                  "다시 시도할 수 있으면 available이고, 아니면 막힌 이유입니다."
                      + " link_read_only: 재시도를 허용하지 않은 링크입니다."
                      + " payment_mode_changed: 링크를 발급한 뒤 지금 결제 모드가 바뀌었습니다."
                      + " payment_not_failed: 가장 늦은 결제가 실패가 아니거나 결제할 금액이 없습니다."
                      + " payment_result_unconfirmed: 이전 결제의 결과를 확인하지 못했습니다."
                      + " retry_limit_reached: 시도 수가 상한에 닿았습니다."
                      + " payment_method_missing: 등록 카드가 없습니다."
                      + " credentials_missing: 결제에 쓸 토스페이먼츠 키가 없습니다."
                      + " card_update_required: 카드를 다시 등록해야 하는 실패입니다."
                      + " retry_not_allowed: 지금은 다시 시도하지 않는 실패입니다."
                      + " link_unavailable: 재시도 응답에서만 나오며, 그사이 링크가 만료되거나 폐기되었습니다.")
          String reason,
      @Schema(
              description =
                  "지금 실패한 결제의 재시도 버전입니다. 재시도 요청의 retry_version으로 보냅니다. 다시 시도할 수 없으면 null입니다.",
              nullable = true)
          String version) {}

  public record PublicInvoicePayment(
      @Schema(description = "결제 상태입니다. 값과 뜻은 GET /v1/invoice-payments의 status와 같습니다.")
          String status,
      @Schema(description = "결제 상태를 본 결제 모드(test 또는 live)입니다. 링크를 발급할 때의 결제 모드와 같습니다.") String mode,
      @Schema(description = "부가세 포함 금액(원)") long amount,
      @Schema(
              description = "결제한 금액(취소 제외, 원)입니다. 결제되지 않았으면 0이고, pending이면 확인 전이라 null입니다.",
              nullable = true)
          @JsonProperty("paid_amount")
          Long paidAmount,
      @Schema(description = "취소를 마친 금액의 합(원)입니다. 결제된 청구서가 아니면 0입니다.")
          @JsonProperty("canceled_amount")
          long canceledAmount,
      @Schema(
              description =
                  "남은 결제 금액(원)입니다. pending이면 확인 전이라 null이고, unpaid나 failed면 청구 금액, 그 밖에는 0입니다."
                      + " 취소한 금액은 더하지 않습니다.",
              nullable = true)
          @JsonProperty("outstanding_amount")
          Long outstandingAmount,
      @Schema(description = "최근 결제 처리 시각(KST, +09:00)입니다. 결제 시도가 없거나 pending이면 null입니다.")
          @JsonProperty("completed_at")
          OffsetDateTime completedAt) {}
}
