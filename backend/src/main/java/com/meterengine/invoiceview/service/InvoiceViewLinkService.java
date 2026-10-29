package com.meterengine.invoiceview.service;

import com.meterengine.audit.service.AuditActor;
import com.meterengine.audit.service.AuditLogService;
import com.meterengine.global.error.ConflictException;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.NotFoundException;
import com.meterengine.global.security.BusinessEnvironment;
import com.meterengine.global.security.BusinessEnvironmentContext;
import com.meterengine.global.security.SecureTokens;
import com.meterengine.invoice.dto.InvoiceResponse;
import com.meterengine.invoice.repository.InvoiceRepository;
import com.meterengine.invoice.service.InvoiceService;
import com.meterengine.invoiceview.dto.InvoiceViewLinkResponse;
import com.meterengine.invoiceview.dto.InvoiceViewLinkSecretResponse;
import com.meterengine.invoiceview.dto.ListInvoiceViewLinksResponse;
import com.meterengine.invoiceview.dto.PublicInvoiceResponse;
import com.meterengine.invoiceview.dto.PublicInvoiceResponse.PublicInvoicePayment;
import com.meterengine.invoiceview.dto.PublicInvoiceResponse.PublicInvoicePaymentRetry;
import com.meterengine.invoiceview.entity.InvoiceViewLink;
import com.meterengine.invoiceview.repository.InvoiceViewLinkRepository;
import com.meterengine.organization.entity.Organization;
import com.meterengine.organization.repository.OrganizationRepository;
import com.meterengine.payment.dto.InvoicePaymentResponse;
import com.meterengine.payment.service.InvoicePaymentService;
import com.meterengine.payment.service.PaymentRetryEligibility;
import com.meterengine.payment.service.PaymentRetryEligibilityService;
import com.meterengine.payment.service.PaymentSettingService;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvoiceViewLinkService {
  private static final String TOKEN_PREFIX = "me_invoice_";
  private static final String TOKEN_PATTERN = "me_invoice_[A-Za-z0-9]{32}";
  private final InvoiceViewLinkRepository invoiceViewLinkRepository;
  private final InvoiceRepository invoiceRepository;
  private final InvoiceService invoiceService;
  private final InvoicePaymentService invoicePaymentService;
  private final PaymentSettingService paymentSettingService;
  private final OrganizationRepository organizationRepository;
  private final AuditLogService auditLogService;
  private final Clock clock;
  private final PaymentRetryEligibilityService paymentRetryEligibilityService;

  InvoiceViewLinkService(
      InvoiceViewLinkRepository invoiceViewLinkRepository,
      InvoiceRepository invoiceRepository,
      InvoiceService invoiceService,
      InvoicePaymentService invoicePaymentService,
      PaymentSettingService paymentSettingService,
      OrganizationRepository organizationRepository,
      AuditLogService auditLogService,
      Clock clock,
      PaymentRetryEligibilityService paymentRetryEligibilityService) {
    this.invoiceViewLinkRepository = invoiceViewLinkRepository;
    this.invoiceRepository = invoiceRepository;
    this.invoiceService = invoiceService;
    this.invoicePaymentService = invoicePaymentService;
    this.paymentSettingService = paymentSettingService;
    this.organizationRepository = organizationRepository;
    this.auditLogService = auditLogService;
    this.clock = clock;
    this.paymentRetryEligibilityService = paymentRetryEligibilityService;
  }

  @Transactional
  public InvoiceViewLinkSecretResponse create(UUID organizationId, UUID invoiceId, Integer days) {
    return create(organizationId, invoiceId, days, false);
  }

  @Transactional
  public InvoiceViewLinkSecretResponse create(
      UUID organizationId, UUID invoiceId, Integer days, boolean allowPaymentRetry) {
    requireInvoice(organizationId, invoiceId);
    String token = SecureTokens.generate(TOKEN_PREFIX);
    OffsetDateTime now = OffsetDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
    InvoiceViewLink link =
        invoiceViewLinkRepository.saveAndFlush(
            new InvoiceViewLink(
                UUID.randomUUID(),
                organizationId,
                invoiceId,
                SecureTokens.hash(token),
                paymentSettingService.get(organizationId).mode(),
                now,
                now.plusDays(days == null ? 30 : days),
                allowPaymentRetry));
    auditLogService.record(
        organizationId,
        "invoice_view_link_created",
        "invoice_view_link",
        link.getId().toString(),
        Map.of(
            "invoice_id",
            invoiceId.toString(),
            "mode",
            link.getMode(),
            "expires_at",
            link.getExpiresAt().toString(),
            "allow_payment_retry",
            allowPaymentRetry));
    return InvoiceViewLinkSecretResponse.from(token, InvoiceViewLinkResponse.from(link));
  }

  @Transactional(readOnly = true)
  public ListInvoiceViewLinksResponse list(UUID organizationId, UUID invoiceId) {
    requireInvoice(organizationId, invoiceId);
    return new ListInvoiceViewLinksResponse(
        invoiceViewLinkRepository
            .findByOrganizationIdAndInvoiceIdOrderByCreatedAtDescIdDesc(organizationId, invoiceId)
            .stream()
            .map(InvoiceViewLinkResponse::from)
            .toList());
  }

  @Transactional
  public InvoiceViewLinkResponse revoke(UUID organizationId, UUID invoiceId, UUID id) {
    requireInvoice(organizationId, invoiceId);
    InvoiceViewLink link =
        invoiceViewLinkRepository
            .findForUpdateByOrganizationIdAndInvoiceIdAndId(organizationId, invoiceId, id)
            .orElseThrow(() -> new NotFoundException(ErrorCode.INVOICE_VIEW_LINK_NOT_FOUND));
    if (link.getRevokedAt() == null) {
      link.revoke(OffsetDateTime.now(clock).truncatedTo(ChronoUnit.MICROS));
      auditLogService.record(
          organizationId,
          "invoice_view_link_revoked",
          "invoice_view_link",
          link.getId().toString(),
          Map.of("invoice_id", invoiceId.toString()));
    }
    return InvoiceViewLinkResponse.from(link);
  }

  @Transactional
  public PublicInvoiceResponse get(String token) {
    InvoiceViewLink link = requireLink(token, false);
    return inLink(link, () -> getPublic(link));
  }

  private PublicInvoiceResponse getPublic(InvoiceViewLink link) {
    Organization organization =
        organizationRepository
            .findById(link.getOrganizationId())
            .filter(value -> Organization.ACTIVE.equals(value.getStatus()))
            .orElseThrow(() -> new NotFoundException(ErrorCode.INVOICE_VIEW_LINK_NOT_FOUND));
    return invoice(link, organization.getName(), publicRetry(link));
  }

  @Transactional
  public PublicInvoiceResponse get(InvoicePaymentRetryAuthorization authorization) {
    InvoiceViewLink link =
        invoiceViewLinkRepository.findById(authorization.actor().id()).orElseThrow();
    if (!Objects.equals(link.getOrganizationId(), authorization.organizationId())
        || !Objects.equals(link.getEnvironmentId(), authorization.environmentId())
        || !Objects.equals(link.getInvoiceId(), authorization.invoiceId())) {
      throw new NotFoundException(ErrorCode.INVOICE_VIEW_LINK_NOT_FOUND);
    }
    return inLink(link, () -> getAuthorized(link, authorization));
  }

  private PublicInvoiceResponse getAuthorized(
      InvoiceViewLink link, InvoicePaymentRetryAuthorization authorization) {
    boolean isUsable =
        link.isUsable(OffsetDateTime.now(clock))
            && organizationRepository
                .findById(link.getOrganizationId())
                .filter(value -> Organization.ACTIVE.equals(value.getStatus()))
                .isPresent();
    return invoice(
        link,
        authorization.organizationName(),
        isUsable
            ? publicRetry(link)
            : new PublicInvoicePaymentRetry(false, "link_unavailable", null));
  }

  private PublicInvoiceResponse invoice(
      InvoiceViewLink link, String organizationName, PublicInvoicePaymentRetry retry) {
    InvoiceResponse invoice = invoiceService.get(link.getOrganizationId(), link.getInvoiceId());
    InvoicePaymentResponse payment =
        invoicePaymentService
            .list(
                link.getOrganizationId(),
                YearMonth.parse(invoice.month()),
                invoice.customerId(),
                link.getMode())
            .invoicePayments()
            .stream()
            .filter(value -> value.invoiceId().equals(link.getInvoiceId()))
            .findFirst()
            .orElseThrow();
    auditLogService.record(
        new AuditActor("invoice_view_link", link.getId(), link.getOrganizationId()),
        "invoice_viewed",
        "invoice",
        link.getInvoiceId().toString(),
        Map.of("mode", link.getMode()));
    return new PublicInvoiceResponse(
        organizationName,
        invoice.customerName(),
        invoice.invoiceNumber(),
        invoice.month(),
        invoice.finalizedAt(),
        invoice.supplyAmount(),
        invoice.taxAmount(),
        invoice.totalAmount(),
        invoice.lines(),
        publicPayment(payment),
        retry,
        InvoiceViewLinkResponse.from(link).expiresAt());
  }

  @Transactional
  public InvoicePaymentRetryAuthorization get(String token, String version) {
    InvoiceViewLink link = requireLink(token, true);
    return inLink(link, () -> authorize(link, version));
  }

  private InvoicePaymentRetryAuthorization authorize(InvoiceViewLink link, String version) {
    if (!link.isAllowPaymentRetry())
      throw new ConflictException(ErrorCode.PAYMENT_RETRY_UNAVAILABLE);
    PaymentRetryEligibility eligibility =
        paymentRetryEligibilityService.get(
            link.getOrganizationId(), link.getInvoiceId(), link.getMode());
    if (!eligibility.allowed()) throw new ConflictException(ErrorCode.PAYMENT_RETRY_UNAVAILABLE);
    if (!Objects.equals(version, retryVersion(link, eligibility)))
      throw new ConflictException(ErrorCode.PAYMENT_RETRY_STALE);
    return new InvoicePaymentRetryAuthorization(
        link.getOrganizationId(),
        link.getInvoiceId(),
        link.getMode(),
        eligibility.failedAttemptId(),
        eligibility.attemptCount(),
        new AuditActor("invoice_view_link", link.getId(), link.getOrganizationId()),
        organizationRepository.findById(link.getOrganizationId()).orElseThrow().getName(),
        link.getEnvironmentId());
  }

  private InvoiceViewLink requireLink(String token, boolean isLocking) {
    if (token == null || !token.matches(TOKEN_PATTERN))
      throw new NotFoundException(ErrorCode.INVOICE_VIEW_LINK_NOT_FOUND);
    InvoiceViewLink link =
        (isLocking
                ? invoiceViewLinkRepository.findForUpdateByTokenHash(SecureTokens.hash(token))
                : invoiceViewLinkRepository.findByTokenHash(SecureTokens.hash(token)))
            .filter(value -> value.isUsable(OffsetDateTime.now(clock)))
            .orElseThrow(() -> new NotFoundException(ErrorCode.INVOICE_VIEW_LINK_NOT_FOUND));
    organizationRepository
        .findById(link.getOrganizationId())
        .filter(value -> Organization.ACTIVE.equals(value.getStatus()))
        .orElseThrow(() -> new NotFoundException(ErrorCode.INVOICE_VIEW_LINK_NOT_FOUND));
    return link;
  }

  private PublicInvoicePaymentRetry publicRetry(InvoiceViewLink link) {
    if (!link.isAllowPaymentRetry())
      return new PublicInvoicePaymentRetry(false, "link_read_only", null);
    PaymentRetryEligibility eligibility =
        paymentRetryEligibilityService.get(
            link.getOrganizationId(), link.getInvoiceId(), link.getMode());
    return new PublicInvoicePaymentRetry(
        eligibility.allowed(),
        eligibility.reason(),
        eligibility.allowed() ? retryVersion(link, eligibility) : null);
  }

  private static String retryVersion(InvoiceViewLink link, PaymentRetryEligibility eligibility) {
    return HexFormat.of()
        .formatHex(
            SecureTokens.hash(
                link.getId()
                    + ":"
                    + eligibility.failedAttemptId()
                    + ":"
                    + eligibility.attemptCount()
                    + ":"
                    + link.getMode()
                    + (link.getEnvironmentId() == null ? "" : ":" + link.getEnvironmentId())));
  }

  private void requireInvoice(UUID organizationId, UUID invoiceId) {
    if ((BusinessEnvironmentContext.environmentId() == null
            ? invoiceRepository.findByOrganizationIdAndId(organizationId, invoiceId)
            : invoiceRepository.findScoped(
                organizationId, BusinessEnvironmentContext.environmentId(), invoiceId))
        .isEmpty()) throw new NotFoundException(ErrorCode.INVOICE_NOT_FOUND);
  }

  private <T> T inLink(InvoiceViewLink link, java.util.function.Supplier<T> action) {
    return BusinessEnvironmentContext.run(
        new BusinessEnvironment(
            link.getOrganizationId(),
            link.getEnvironmentId(),
            link.getEnvironmentId() == null ? null : link.getMode()),
        action);
  }

  private static PublicInvoicePayment publicPayment(InvoicePaymentResponse payment) {
    boolean pending = "pending".equals(payment.status());
    boolean successful =
        java.util.Set.of("paid", "partially_canceled", "canceled").contains(payment.status());
    Long paid = pending ? null : successful ? payment.amount() - payment.canceledAmount() : 0L;
    Long outstanding =
        pending
            ? null
            : java.util.Set.of("unpaid", "failed").contains(payment.status())
                ? payment.amount()
                : 0L;
    return new PublicInvoicePayment(
        payment.status(),
        payment.mode(),
        payment.amount(),
        paid,
        payment.canceledAmount(),
        outstanding,
        payment.completedAt());
  }
}
