package com.meterengine.payment.service;

import com.meterengine.customer.entity.Customer;
import com.meterengine.customer.repository.CustomerRepository;
import com.meterengine.global.error.ConflictException;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.NotFoundException;
import com.meterengine.global.security.BusinessEnvironmentContext;
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
    Customer customer =
        customerRepository
            .findScoped(organizationId, BusinessEnvironmentContext.environmentId(), customerId)
            .orElseThrow(() -> new NotFoundException(ErrorCode.CUSTOMER_NOT_FOUND));
    if (customer.getArchivedAt() != null) throw new ConflictException(ErrorCode.CUSTOMER_ARCHIVED);
    String mode = paymentSettingService.findMode(organizationId);
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
                    createdAt.plus(REGISTRATION_LIFETIME))
                .pinCredential(credential.getUpdatedAt()));
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
            .findScoped(
                registration.getOrganizationId(),
                registration.getEnvironmentId(),
                registration.getCustomerId())
            .orElseThrow();
    if (customer.getArchivedAt() != null) throw new ConflictException(ErrorCode.CUSTOMER_ARCHIVED);
    TossPaymentsCredential credential = findCredential(registration);
    return PaymentMethodRegistrationResponse.from(
        null,
        registration,
        customer.getName(),
        organizationName(registration.getOrganizationId()),
        credential.getClientKey());
  }

  PaymentMethodRegistration findUsable(String token) {
    PaymentMethodRegistration registration =
        paymentMethodRegistrationRepository
            .findByTokenHashAndUsedAtIsNullAndExpiresAtAfter(hash(token), OffsetDateTime.now())
            .orElseThrow(
                () -> new NotFoundException(ErrorCode.PAYMENT_METHOD_REGISTRATION_NOT_FOUND));
    if (customerRepository
            .findScoped(
                registration.getOrganizationId(),
                registration.getEnvironmentId(),
                registration.getCustomerId())
            .orElseThrow(() -> new NotFoundException(ErrorCode.CUSTOMER_NOT_FOUND))
            .getArchivedAt()
        != null) throw new ConflictException(ErrorCode.CUSTOMER_ARCHIVED);
    return registration;
  }

  boolean available(PaymentMethodRegistration registration) {
    return customerRepository
            .lockScoped(
                registration.getOrganizationId(),
                registration.getEnvironmentId(),
                java.util.List.of(registration.getCustomerId()))
            .stream()
            .anyMatch(customer -> customer.getArchivedAt() == null)
        && (registration.getEnvironmentId() == null
                ? tossPaymentsCredentialService.active(
                    registration.getOrganizationId(), registration.getMode())
                : historicalCredential(registration))
            .isPresent();
  }

  TossPaymentsCredential findCredential(PaymentMethodRegistration registration) {
    return historicalCredential(registration)
        .orElseThrow(() -> new ConflictException(ErrorCode.TOSS_PAYMENTS_CREDENTIAL_MISSING));
  }

  private java.util.Optional<TossPaymentsCredential> historicalCredential(
      PaymentMethodRegistration registration) {
    return PaymentEnvironments.call(
        registration.getOrganizationId(),
        registration.getEnvironmentId(),
        registration.getMode(),
        () ->
            tossPaymentsCredentialService.historical(
                registration.getOrganizationId(),
                registration.getMode(),
                registration.getCredentialUpdatedAt() == null
                    ? registration.getCreatedAt()
                    : registration.getCredentialUpdatedAt()));
  }

  java.util.Optional<TossPaymentsCredential> credentialVersion(
      PaymentMethodRegistration registration, OffsetDateTime updatedAt) {
    return PaymentEnvironments.call(
        registration.getOrganizationId(),
        registration.getEnvironmentId(),
        registration.getMode(),
        () ->
            tossPaymentsCredentialService.historical(
                registration.getOrganizationId(), registration.getMode(), updatedAt));
  }

  TossPaymentsCredential findCredential(UUID organizationId, String mode) {
    return tossPaymentsCredentialService
        .active(organizationId, mode)
        .orElseThrow(() -> new ConflictException(ErrorCode.TOSS_PAYMENTS_CREDENTIAL_MISSING));
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
