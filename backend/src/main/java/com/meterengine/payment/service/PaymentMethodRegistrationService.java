package com.meterengine.payment.service;

import com.meterengine.customer.entity.Customer;
import com.meterengine.customer.repository.CustomerRepository;
import com.meterengine.global.error.ConflictException;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.NotFoundException;
import com.meterengine.organization.entity.Organization;
import com.meterengine.organization.repository.OrganizationRepository;
import com.meterengine.payment.dto.PaymentMethodRegistrationResponse;
import com.meterengine.payment.entity.PaymentMethodRegistration;
import com.meterengine.payment.entity.TossPaymentsCredential;
import com.meterengine.payment.repository.PaymentMethodRegistrationRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentMethodRegistrationService {

  private static final int TOKEN_BYTES = 32;
  private static final Duration REGISTRATION_LIFETIME = Duration.ofHours(24);

  private final PaymentMethodRegistrationRepository paymentMethodRegistrationRepository;
  private final CustomerRepository customerRepository;
  private final OrganizationRepository organizationRepository;
  private final TossPaymentsCredentialService tossPaymentsCredentialService;
  private final PaymentSettingService paymentSettingService;
  private final SecureRandom secureRandom = new SecureRandom();

  PaymentMethodRegistrationService(
      PaymentMethodRegistrationRepository paymentMethodRegistrationRepository,
      CustomerRepository customerRepository,
      OrganizationRepository organizationRepository,
      TossPaymentsCredentialService tossPaymentsCredentialService,
      PaymentSettingService paymentSettingService) {
    this.paymentMethodRegistrationRepository = paymentMethodRegistrationRepository;
    this.customerRepository = customerRepository;
    this.organizationRepository = organizationRepository;
    this.tossPaymentsCredentialService = tossPaymentsCredentialService;
    this.paymentSettingService = paymentSettingService;
  }

  @Transactional
  public PaymentMethodRegistrationResponse create(UUID organizationId, UUID customerId) {
    return create(organizationId, customerId, null);
  }

  @Transactional
  public PaymentMethodRegistrationResponse create(
      UUID organizationId, UUID customerId, String principalMode) {
    Customer customer =
        customerRepository
            .findByOrganizationIdAndId(organizationId, customerId)
            .orElseThrow(() -> new NotFoundException(ErrorCode.CUSTOMER_NOT_FOUND));
    String mode = paymentSettingService.findMode(organizationId);
    if (principalMode != null && !principalMode.equals(mode)) {
      throw new ConflictException(ErrorCode.PAYMENT_MODE_MISMATCH);
    }
    TossPaymentsCredential credential = findCredential(organizationId, mode);
    String token = generateToken();
    OffsetDateTime createdAt = OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS);
    PaymentMethodRegistration registration =
        paymentMethodRegistrationRepository.saveAndFlush(
            new PaymentMethodRegistration(
                UUID.randomUUID(),
                organizationId,
                customerId,
                hash(token),
                mode,
                createdAt,
                createdAt.plus(REGISTRATION_LIFETIME)));
    return PaymentMethodRegistrationResponse.from(
        token,
        registration,
        customer.getName(),
        organizationName(organizationId),
        credential.getClientKey());
  }

  @Transactional(readOnly = true)
  public PaymentMethodRegistrationResponse get(String token) {
    PaymentMethodRegistration registration = findUsable(token);
    Customer customer =
        customerRepository
            .findByOrganizationIdAndId(
                registration.getOrganizationId(), registration.getCustomerId())
            .orElseThrow();
    TossPaymentsCredential credential =
        findCredential(registration.getOrganizationId(), registration.getMode());
    return PaymentMethodRegistrationResponse.from(
        null,
        registration,
        customer.getName(),
        organizationName(registration.getOrganizationId()),
        credential.getClientKey());
  }

  PaymentMethodRegistration findUsable(String token) {
    return paymentMethodRegistrationRepository
        .findByTokenHashAndUsedAtIsNullAndExpiresAtAfter(hash(token), OffsetDateTime.now())
        .filter(registration -> isActive(registration.getOrganizationId()))
        .orElseThrow(() -> new NotFoundException(ErrorCode.PAYMENT_METHOD_REGISTRATION_NOT_FOUND));
  }

  TossPaymentsCredential findCredential(UUID organizationId, String mode) {
    return tossPaymentsCredentialService
        .find(organizationId, mode)
        .orElseThrow(() -> new ConflictException(ErrorCode.TOSS_PAYMENTS_CREDENTIAL_MISSING));
  }

  private boolean isActive(UUID organizationId) {
    return organizationRepository
        .findById(organizationId)
        .filter(organization -> Organization.ACTIVE.equals(organization.getStatus()))
        .isPresent();
  }

  private String organizationName(UUID organizationId) {
    return organizationRepository.findById(organizationId).orElseThrow().getName();
  }

  private String generateToken() {
    byte[] bytes = new byte[TOKEN_BYTES];
    secureRandom.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private static String hash(String token) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException(exception);
    }
  }
}
