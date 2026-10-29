package com.meterengine.payment.service;

import com.meterengine.customer.repository.CustomerRepository;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.NotFoundException;
import com.meterengine.payment.dto.ListPaymentMethodsResponse;
import com.meterengine.payment.dto.PaymentMethodResponse;
import com.meterengine.payment.entity.BillingKeyId;
import com.meterengine.payment.repository.BillingKeyRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentMethodService {

  private final BillingKeyRepository billingKeyRepository;
  private final CustomerRepository customerRepository;
  private final PaymentSettingService paymentSettingService;

  PaymentMethodService(
      BillingKeyRepository billingKeyRepository,
      CustomerRepository customerRepository,
      PaymentSettingService paymentSettingService) {
    this.billingKeyRepository = billingKeyRepository;
    this.customerRepository = customerRepository;
    this.paymentSettingService = paymentSettingService;
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
    return ListPaymentMethodsResponse.from(
        billingKeyRepository.findByOrganizationIdAndModeOrderByCustomerIdAsc(
            organizationId, paymentSettingService.findMode(organizationId)));
  }
}
