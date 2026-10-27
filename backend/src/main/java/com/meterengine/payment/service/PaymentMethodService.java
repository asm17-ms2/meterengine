package com.meterengine.payment.service;

import com.meterengine.audit.service.AuditLogService;
import com.meterengine.customer.repository.CustomerRepository;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.NotFoundException;
import com.meterengine.global.validation.ArchiveStatus;
import com.meterengine.payment.dto.ListPaymentMethodsResponse;
import com.meterengine.payment.dto.PaymentMethodResponse;
import com.meterengine.payment.entity.BillingKeyId;
import com.meterengine.payment.repository.BillingKeyRepository;
import com.meterengine.payment.repository.PaymentArchiveRepository;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentMethodService {

  private final BillingKeyRepository billingKeyRepository;
  private final CustomerRepository customerRepository;
  private final PaymentSettingService paymentSettingService;
  private final AuditLogService auditLogService;
  private final PaymentArchiveRepository paymentArchiveRepository;

  PaymentMethodService(
      BillingKeyRepository billingKeyRepository,
      CustomerRepository customerRepository,
      PaymentSettingService paymentSettingService,
      AuditLogService auditLogService,
      PaymentArchiveRepository paymentArchiveRepository) {
    this.billingKeyRepository = billingKeyRepository;
    this.customerRepository = customerRepository;
    this.paymentSettingService = paymentSettingService;
    this.auditLogService = auditLogService;
    this.paymentArchiveRepository = paymentArchiveRepository;
  }

  @Transactional(readOnly = true)
  public PaymentMethodResponse get(UUID organizationId, UUID customerId) {
    if (!customerRepository.existsByOrganizationIdAndId(organizationId, customerId)) {
      throw new NotFoundException(ErrorCode.CUSTOMER_NOT_FOUND);
    }
    String mode = paymentSettingService.findMode(organizationId);
    return billingKeyRepository
        .findById(new BillingKeyId(organizationId, customerId, mode))
        .map(PaymentMethodResponse::from)
        .orElseThrow(() -> new NotFoundException(ErrorCode.PAYMENT_METHOD_NOT_FOUND));
  }

  @Transactional(readOnly = true)
  public ListPaymentMethodsResponse list(UUID organizationId) {
    return list(organizationId, "active");
  }

  @Transactional(readOnly = true)
  public ListPaymentMethodsResponse list(UUID organizationId, String status) {
    ArchiveStatus filter = ArchiveStatus.parse(status);
    String mode = paymentSettingService.findMode(organizationId);
    var methods =
        new ArrayList<>(
            billingKeyRepository
                .findByOrganizationIdAndModeOrderByCustomerIdAsc(organizationId, mode)
                .stream()
                .filter(key -> filter.includes(key.getArchivedAt()))
                .map(PaymentMethodResponse::from)
                .toList());
    if (filter != ArchiveStatus.ACTIVE)
      methods.addAll(paymentArchiveRepository.methods(organizationId, mode));
    return new ListPaymentMethodsResponse(methods);
  }

  @Transactional
  public void delete(UUID organizationId, UUID customerId) {
    paymentArchiveRepository.lock(organizationId);
    String mode = paymentSettingService.findMode(organizationId);
    var key =
        billingKeyRepository
            .findById(new BillingKeyId(organizationId, customerId, mode))
            .orElseThrow(() -> new NotFoundException(ErrorCode.PAYMENT_METHOD_NOT_FOUND));
    if (key.getArchivedAt() != null) return;
    key.archive();
    auditLogService.record(
        organizationId,
        "payment_method_archived",
        "customer",
        customerId.toString(),
        Map.of("mode", mode));
  }
}
