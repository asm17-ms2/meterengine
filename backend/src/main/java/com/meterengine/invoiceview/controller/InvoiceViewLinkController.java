package com.meterengine.invoiceview.controller;

import com.meterengine.global.error.ErrorResponse;
import com.meterengine.global.security.AdminAccess;
import com.meterengine.global.security.AuthenticatedPrincipal;
import com.meterengine.global.security.CurrentOrganizationId;
import com.meterengine.global.security.EnvironmentReady;
import com.meterengine.global.security.PublicAccess;
import com.meterengine.global.security.ReadAccess;
import com.meterengine.invoiceview.dto.CreateInvoicePaymentRetryRequest;
import com.meterengine.invoiceview.dto.CreateInvoiceViewLinkRequest;
import com.meterengine.invoiceview.dto.GetPublicInvoiceRequest;
import com.meterengine.invoiceview.dto.InvoiceViewLinkResponse;
import com.meterengine.invoiceview.dto.InvoiceViewLinkSecretResponse;
import com.meterengine.invoiceview.dto.ListInvoiceViewLinksResponse;
import com.meterengine.invoiceview.dto.PublicInvoiceResponse;
import com.meterengine.invoiceview.service.InvoicePaymentRetryService;
import com.meterengine.invoiceview.service.InvoiceViewLinkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@EnvironmentReady
public class InvoiceViewLinkController {
  private final InvoiceViewLinkService invoiceViewLinkService;
  private final InvoicePaymentRetryService invoicePaymentRetryService;

  InvoiceViewLinkController(
      InvoiceViewLinkService invoiceViewLinkService,
      InvoicePaymentRetryService invoicePaymentRetryService) {
    this.invoiceViewLinkService = invoiceViewLinkService;
    this.invoicePaymentRetryService = invoicePaymentRetryService;
  }

  @PostMapping("/v1/invoices/{id}/view-links")
  @AdminAccess
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "청구서 조회 링크 발급",
      description =
          "로그인 없이 발행한 청구서 하나를 보는 링크를 만듭니다."
              + " token은 발급 응답에만 실립니다. MeterEngine은 token 원문을 저장하지 않으므로 다시 볼 수 없습니다."
              + " 유효 기간은 기본 30일, 최대 90일이고 폐기할 수 있습니다."
              + " 링크는 발급할 때의 결제 모드(test 또는 live)로 결제 상태를 보여 줍니다."
              + " 링크를 가진 사람은 누구나 볼 수 있으므로 그 고객에게만 전달합니다."
              + " 재시도를 허용하는 링크를 API 키로 만들 때는 테스트 키인지 라이브 키인지가 지금 결제 모드와 같아야 하고, 다르면 409 payment_mode_mismatch입니다.")
  public InvoiceViewLinkSecretResponse createInvoiceViewLink(
      @CurrentOrganizationId UUID organizationId,
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      @Parameter(description = "링크를 발급할 청구서의 ID") @PathVariable UUID id,
      @Valid @RequestBody(required = false) CreateInvoiceViewLinkRequest request,
      HttpServletResponse response) {
    privateResponse(response);
    return invoiceViewLinkService.create(
        organizationId,
        id,
        request == null ? null : request.expiresInDays(),
        request != null && request.allowPaymentRetry(),
        principal.mode());
  }

  @GetMapping("/v1/invoices/{id}/view-links")
  @ReadAccess
  @Operation(
      summary = "청구서 조회 링크 목록 조회",
      description = "청구서에 발급한 링크를 최근 발급 순으로 돌려줍니다. token은 싣지 않습니다.")
  public ListInvoiceViewLinksResponse listInvoiceViewLinks(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(description = "발급 이력을 볼 청구서의 ID") @PathVariable UUID id,
      HttpServletResponse response) {
    privateResponse(response);
    return invoiceViewLinkService.list(organizationId, id);
  }

  @PostMapping("/v1/invoices/{id}/view-links/{view_link_id}/revoke")
  @AdminAccess
  @Operation(
      summary = "청구서 조회 링크 폐기",
      description = "폐기한 링크는 다시 쓸 수 없습니다. 이미 폐기한 링크를 다시 폐기해도 같은 결과를 돌려줍니다.")
  public InvoiceViewLinkResponse revokeInvoiceViewLink(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(description = "링크를 발급한 청구서의 ID") @PathVariable UUID id,
      @Parameter(description = "폐기할 링크의 ID(발급 응답이나 발급 이력의 id)") @PathVariable("view_link_id")
          UUID viewLinkId,
      HttpServletResponse response) {
    privateResponse(response);
    return invoiceViewLinkService.revoke(organizationId, id, viewLinkId);
  }

  @PostMapping("/v1/public/invoice-links/lookup")
  @PublicAccess
  @Operation(
      summary = "청구서 공개 조회",
      description =
          "본문의 token으로 청구서 하나를 조회합니다."
              + " 없거나 만료되거나 폐기된 링크는 모두 404입니다. 내부 식별자, 이메일, 토스페이먼츠 결제 키는 싣지 않습니다."
              + " 취소한 금액은 따로 싣고 남은 결제 금액에 다시 더하지 않습니다."
              + " 결제가 pending이면 결제한 금액(paid_amount)과 남은 결제 금액(outstanding_amount)은 null입니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "token의 링크로 공개한 청구서입니다."),
    @ApiResponse(
        responseCode = "404",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=invoice_view_link_not_found: 링크가 없거나 만료되거나 폐기됐습니다.")
  })
  public PublicInvoiceResponse getPublicInvoice(
      @Valid @RequestBody GetPublicInvoiceRequest request, HttpServletResponse response) {
    privateResponse(response);
    return invoiceViewLinkService.get(request.token());
  }

  private static void privateResponse(HttpServletResponse response) {
    response.setHeader("Cache-Control", "no-store");
    response.setHeader("Referrer-Policy", "no-referrer");
    response.setHeader("X-Robots-Tag", "noindex, nofollow");
  }

  @PostMapping("/v1/public/invoice-links/payment-retries")
  @PublicAccess
  @Operation(
      summary = "고객의 실패 결제 재시도",
      description =
          "조직이 재시도를 허용한 링크로 실패한 결제만 다시 시도합니다."
              + " 조회 응답의 payment_retry.version을 retry_version으로 보내고, 같은 버전으로는 새 결제를 한 번만 만들 수 있습니다."
              + " 금액과 카드는 MeterEngine에 기록된 청구 금액과 등록 카드를 씁니다."
              + " 결과 확인 중(pending)이거나 승인, 취소된 결제, 시도 상한에 닿은 결제, 카드를 다시 등록해야 하는 실패는 다시 시도하지 않습니다.")
  public PublicInvoiceResponse createInvoicePaymentRetry(
      @Valid @RequestBody CreateInvoicePaymentRetryRequest request, HttpServletResponse response) {
    privateResponse(response);
    return invoicePaymentRetryService.retry(request.token(), request.retryVersion());
  }
}
