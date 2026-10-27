package com.meterengine.invoiceview.controller;

import com.meterengine.global.security.AdminAccess;
import com.meterengine.global.security.CurrentOrganizationId;
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
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
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
      summary = "고객 인보이스 조회 링크 발급",
      description =
          "로그인 없이 인보이스 하나를 조회하는 링크를 만든다."
              + " token은 발급 응답에만 제공하고 해시만 저장한다. 기본 30일, 최대 90일이며 폐기할 수 있다."
              + " 결제 상태는 발급 당시 test/live 모드로 조회한다. 링크를 가진 사람이 조회할 수 있으므로 고객에게만 전달한다.")
  public InvoiceViewLinkSecretResponse createInvoiceViewLink(
      @CurrentOrganizationId UUID organizationId,
      @PathVariable UUID id,
      @Valid @RequestBody(required = false) CreateInvoiceViewLinkRequest request,
      HttpServletResponse response) {
    privateResponse(response);
    return invoiceViewLinkService.create(
        organizationId,
        id,
        request == null ? null : request.expiresInDays(),
        request != null && request.allowPaymentRetry());
  }

  @GetMapping("/v1/invoices/{id}/view-links")
  @ReadAccess
  @Operation(summary = "고객 인보이스 조회 링크 목록", description = "원문 token을 제외한 발급 이력을 조회한다.")
  public ListInvoiceViewLinksResponse listInvoiceViewLinks(
      @CurrentOrganizationId UUID organizationId,
      @PathVariable UUID id,
      HttpServletResponse response) {
    privateResponse(response);
    return invoiceViewLinkService.list(organizationId, id);
  }

  @PostMapping("/v1/invoices/{id}/view-links/{view_link_id}/revoke")
  @AdminAccess
  @Operation(summary = "고객 인보이스 조회 링크 폐기", description = "폐기한 링크는 다시 사용할 수 없다. 반복 폐기는 같은 결과다.")
  public InvoiceViewLinkResponse revokeInvoiceViewLink(
      @CurrentOrganizationId UUID organizationId,
      @PathVariable UUID id,
      @PathVariable("view_link_id") UUID viewLinkId,
      HttpServletResponse response) {
    privateResponse(response);
    return invoiceViewLinkService.revoke(organizationId, id, viewLinkId);
  }

  @PostMapping("/v1/public/invoice-links/lookup")
  @PublicAccess
  @Operation(
      summary = "고객 인보이스 공개 조회",
      description =
          "본문 token으로 인보이스 하나를 조회한다."
              + " 없거나 만료, 폐기된 링크는 모두 404다. 내부 식별자, 이메일과 PG 거래 키는 제공하지 않는다."
              + " 결제 취소액을 별도로 표시하며 자동 재청구 대기액으로 바꾸지 않는다. pending의 납부액과 대기액은 null이다.")
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
          "도입사가 재시도를 허용한 링크로 실패 결제만 다시 시도한다."
              + " 조회 응답의 retry_version을 보내며 같은 실패 버전은 한 번만 새 결제를 만들 수 있다."
              + " 금액과 등록 카드는 서버의 기록을 사용한다. pending, 승인과 취소, 시도 상한과 카드 오류는 우회하지 않는다.")
  public PublicInvoiceResponse createInvoicePaymentRetry(
      @Valid @RequestBody CreateInvoicePaymentRetryRequest request, HttpServletResponse response) {
    privateResponse(response);
    return invoicePaymentRetryService.retry(request.token(), request.retryVersion());
  }
}
