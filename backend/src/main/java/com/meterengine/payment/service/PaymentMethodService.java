package com.meterengine.payment.service;

import com.meterengine.audit.service.AuditLogService;
import com.meterengine.customer.repository.CustomerRepository;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.NotFoundException;
import com.meterengine.global.security.BusinessEnvironmentContext;
import com.meterengine.global.validation.ArchiveStatus;
import com.meterengine.payment.client.TossPaymentsClient;
import com.meterengine.payment.client.TossPaymentsResult;
import com.meterengine.payment.dto.ListPaymentMethodsResponse;
import com.meterengine.payment.dto.PaymentMethodResponse;
import com.meterengine.payment.entity.BillingKey;
import com.meterengine.payment.entity.BillingKeyId;
import com.meterengine.payment.entity.TossPaymentsCredential;
import com.meterengine.payment.repository.BillingKeyRepository;
import com.meterengine.payment.repository.PaymentArchiveRepository;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class PaymentMethodService {

  private static final Logger logger = LoggerFactory.getLogger(PaymentMethodService.class);

  private final BillingKeyRepository billingKeyRepository;
  private final CustomerRepository customerRepository;
  private final PaymentSettingService paymentSettingService;
  private final AuditLogService auditLogService;
  private final PaymentArchiveRepository paymentArchiveRepository;
  private final TossPaymentsCredentialService tossPaymentsCredentialService;
  private final TossPaymentsClient tossPaymentsClient;

  PaymentMethodService(
      BillingKeyRepository billingKeyRepository,
      CustomerRepository customerRepository,
      PaymentSettingService paymentSettingService,
      AuditLogService auditLogService,
      PaymentArchiveRepository paymentArchiveRepository,
      TossPaymentsCredentialService tossPaymentsCredentialService,
      TossPaymentsClient tossPaymentsClient) {
    this.billingKeyRepository = billingKeyRepository;
    this.customerRepository = customerRepository;
    this.paymentSettingService = paymentSettingService;
    this.auditLogService = auditLogService;
    this.paymentArchiveRepository = paymentArchiveRepository;
    this.tossPaymentsCredentialService = tossPaymentsCredentialService;
    this.tossPaymentsClient = tossPaymentsClient;
  }

  @Transactional(readOnly = true)
  public PaymentMethodResponse get(UUID organizationId, UUID customerId) {
    if (!customerRepository
        .findScoped(organizationId, BusinessEnvironmentContext.environmentId(), customerId)
        .isPresent()) {
      throw new NotFoundException(ErrorCode.CUSTOMER_NOT_FOUND);
    }
    String mode = paymentSettingService.findMode(organizationId);
    return billingKeyRepository
        .findById(new BillingKeyId(organizationId, customerId, mode))
        .filter(value -> BusinessEnvironmentContext.matches(value.getEnvironmentId()))
        .map(PaymentMethodResponse::from)
        .orElseThrow(() -> new NotFoundException(ErrorCode.PAYMENT_METHOD_NOT_FOUND));
  }

  @Transactional(readOnly = true)
  public ListPaymentMethodsResponse list(UUID organizationId) {
    return list(organizationId, "active");
  }

  @Transactional(readOnly = true)
  public ListPaymentMethodsResponse list(UUID organizationId, String status) {
    return list(organizationId, status, null);
  }

  @Transactional(readOnly = true)
  public ListPaymentMethodsResponse list(UUID organizationId, String status, UUID customerId) {
    ArchiveStatus filter = ArchiveStatus.parse(status);
    String mode = paymentSettingService.findMode(organizationId);
    Stream<BillingKey> keys =
        customerId == null
            ? billingKeyRepository
                .findByOrganizationIdAndModeOrderByCustomerIdAsc(organizationId, mode)
                .stream()
            : billingKeyRepository
                .findById(new BillingKeyId(organizationId, customerId, mode))
                .stream();
    var methods =
        new ArrayList<>(
            keys.filter(key -> filter.includes(key.getArchivedAt()))
                .filter(value -> BusinessEnvironmentContext.matches(value.getEnvironmentId()))
                .map(PaymentMethodResponse::from)
                .toList());
    if (filter != ArchiveStatus.ACTIVE)
      methods.addAll(
          customerId == null
              ? paymentArchiveRepository.methods(organizationId, mode)
              : paymentArchiveRepository.methods(organizationId, mode, customerId));
    return new ListPaymentMethodsResponse(methods);
  }

  @Transactional
  public void delete(UUID organizationId, UUID customerId) {
    paymentArchiveRepository.lock(organizationId);
    String mode = paymentSettingService.findMode(organizationId);
    var key =
        billingKeyRepository
            .findById(new BillingKeyId(organizationId, customerId, mode))
            .filter(value -> BusinessEnvironmentContext.matches(value.getEnvironmentId()))
            .orElseThrow(() -> new NotFoundException(ErrorCode.PAYMENT_METHOD_NOT_FOUND));
    if (key.getArchivedAt() != null) return;
    String billingKey = key.getBillingKey();
    key.archive();
    auditLogService.record(
        organizationId,
        "payment_method_archived",
        "customer",
        customerId.toString(),
        Map.of("mode", mode));
    String secretKey =
        tossPaymentsCredentialService
            .active(organizationId, mode)
            .flatMap(
                current ->
                    key.getCredentialUpdatedAt() == null
                        ? Optional.of(current)
                        : tossPaymentsCredentialService.historical(
                            organizationId, mode, key.getCredentialUpdatedAt()))
            .map(TossPaymentsCredential::getSecretKey)
            .orElse(null);
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            deleteBillingKey(organizationId, customerId, mode, secretKey, billingKey);
          }
        });
  }

  private void deleteBillingKey(
      UUID organizationId, UUID customerId, String mode, String secretKey, String billingKey) {
    if (secretKey == null) {
      logger.warn(
          "toss payments billing key deletion skipped: organization_id={}, customer_id={},"
              + " mode={}, reason=toss_payments_credential_missing",
          organizationId,
          customerId,
          mode);
      return;
    }
    switch (tossPaymentsClient.deleteBillingKey(secretKey, billingKey)) {
      case TossPaymentsResult.Success<Void> deleted ->
          logger.info(
              "toss payments billing key deleted: organization_id={}, customer_id={}, mode={}",
              organizationId,
              customerId,
              mode);
      case TossPaymentsResult.Rejected<Void> rejected ->
          logger.warn(
              "toss payments rejected billing key deletion: organization_id={}, customer_id={},"
                  + " mode={}, code={}",
              organizationId,
              customerId,
              mode,
              rejected.code());
      case TossPaymentsResult.Unknown<Void> unknown ->
          logger.warn(
              "toss payments billing key deletion result unknown: organization_id={},"
                  + " customer_id={}, mode={}, reason={}",
              organizationId,
              customerId,
              mode,
              unknown.reason());
    }
  }
}
