package com.meterengine.payment.service;

import com.meterengine.global.crypto.SecretMask;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.ErrorResponse.FieldError;
import com.meterengine.global.error.InvalidRequestException;
import com.meterengine.global.error.NotFoundException;
import com.meterengine.global.error.ServiceUnavailableException;
import com.meterengine.payment.client.TossPaymentsBilling;
import com.meterengine.payment.client.TossPaymentsClient;
import com.meterengine.payment.client.TossPaymentsError;
import com.meterengine.payment.client.TossPaymentsResult;
import com.meterengine.payment.dto.CreateBillingKeyRequest;
import com.meterengine.payment.dto.PaymentMethodResponse;
import com.meterengine.payment.entity.BillingKey;
import com.meterengine.payment.entity.BillingKeyId;
import com.meterengine.payment.entity.PaymentMethodRegistration;
import com.meterengine.payment.repository.BillingKeyRepository;
import com.meterengine.payment.repository.PaymentArchiveRepository;
import com.meterengine.payment.repository.PaymentMethodRegistrationRepository;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class BillingKeyService {

  private static final Logger logger = LoggerFactory.getLogger(BillingKeyService.class);
  private static final String CUSTOMER_KEY_MISMATCH_MESSAGE = "카드 등록 링크의 고객과 맞지 않습니다";

  private final PaymentMethodRegistrationService paymentMethodRegistrationService;
  private final PaymentMethodRegistrationRepository paymentMethodRegistrationRepository;
  private final BillingKeyRepository billingKeyRepository;
  private final PaymentArchiveRepository paymentArchiveRepository;
  private final TossPaymentsClient tossPaymentsClient;
  private final TransactionTemplate transactionTemplate;

  BillingKeyService(
      PaymentMethodRegistrationService paymentMethodRegistrationService,
      PaymentMethodRegistrationRepository paymentMethodRegistrationRepository,
      BillingKeyRepository billingKeyRepository,
      PaymentArchiveRepository paymentArchiveRepository,
      TossPaymentsClient tossPaymentsClient,
      PlatformTransactionManager platformTransactionManager) {
    this.paymentMethodRegistrationService = paymentMethodRegistrationService;
    this.paymentMethodRegistrationRepository = paymentMethodRegistrationRepository;
    this.billingKeyRepository = billingKeyRepository;
    this.paymentArchiveRepository = paymentArchiveRepository;
    this.tossPaymentsClient = tossPaymentsClient;
    this.transactionTemplate = new TransactionTemplate(platformTransactionManager);
  }

  public PaymentMethodResponse create(String token, CreateBillingKeyRequest request) {
    OffsetDateTime consentedAt = now();
    Issuance issuance =
        transactionTemplate.execute(status -> prepare(token, request.customerKey()));
    TossPaymentsBilling billing =
        issue(issuance.secretKey(), request.authKey(), request.customerKey());
    Optional<Replacement> replacement =
        transactionTemplate.execute(
            status ->
                replace(
                    issuance.registration(),
                    billing,
                    request.consentDocumentVersion(),
                    consentedAt));
    if (replacement.isEmpty()) {
      delete(issuance, billing.billingKey());
      throw new NotFoundException(ErrorCode.PAYMENT_METHOD_REGISTRATION_NOT_FOUND);
    }
    String replacedBillingKey = replacement.get().replacedBillingKey();
    if (replacedBillingKey != null && !replacedBillingKey.equals(billing.billingKey())) {
      delete(issuance, replacedBillingKey);
    }
    return replacement.get().paymentMethod();
  }

  private Issuance prepare(String token, String customerKey) {
    PaymentMethodRegistration registration = paymentMethodRegistrationService.findUsable(token);
    if (!registration.getCustomerId().toString().equals(customerKey)) {
      throw new InvalidRequestException(
          ErrorCode.VALIDATION_ERROR,
          List.of(new FieldError("customer_key", CUSTOMER_KEY_MISMATCH_MESSAGE)));
    }
    String secretKey =
        paymentMethodRegistrationService
            .findCredential(registration.getOrganizationId(), registration.getMode())
            .getSecretKey();
    return new Issuance(registration, secretKey);
  }

  private TossPaymentsBilling issue(String secretKey, String authKey, String customerKey) {
    return switch (tossPaymentsClient.issueBillingKey(secretKey, authKey, customerKey)) {
      case TossPaymentsResult.Success<TossPaymentsBilling> issued -> issued.value();
      case TossPaymentsResult.Rejected<TossPaymentsBilling> rejected -> {
        logger.warn(
            "toss payments rejected billing key issuance: code={}, message={}",
            rejected.code(),
            new TossPaymentsError(rejected.code(), rejected.message()).singleLineMessage());
        throw new InvalidRequestException(ErrorCode.PAYMENT_METHOD_REJECTED);
      }
      case TossPaymentsResult.Unknown<TossPaymentsBilling> unknown -> {
        logger.warn("toss payments billing key issuance result unknown: {}", unknown.reason());
        throw new ServiceUnavailableException(ErrorCode.TOSS_PAYMENTS_UNAVAILABLE);
      }
    };
  }

  private Optional<Replacement> replace(
      PaymentMethodRegistration registration,
      TossPaymentsBilling billing,
      String consentDocumentVersion,
      OffsetDateTime consentedAt) {
    paymentArchiveRepository.lock(registration.getOrganizationId());
    if (!paymentMethodRegistrationService.available(registration)
        || paymentMethodRegistrationRepository.markUsed(registration.getId(), now()) == 0) {
      return Optional.empty();
    }
    OffsetDateTime authenticatedAt = billing.authenticatedAt().truncatedTo(ChronoUnit.MICROS);
    Optional<BillingKey> existing =
        billingKeyRepository.findById(
            new BillingKeyId(
                registration.getOrganizationId(),
                registration.getCustomerId(),
                registration.getMode()));
    String replacedBillingKey = existing.map(BillingKey::getBillingKey).orElse(null);
    BillingKey billingKey =
        existing
            .map(
                found -> {
                  paymentArchiveRepository.method(
                      registration.getOrganizationId(),
                      registration.getCustomerId(),
                      registration.getMode());
                  found.update(
                      billing.billingKey(),
                      billing.card().issuerCode(),
                      billing.card().number(),
                      authenticatedAt,
                      consentDocumentVersion,
                      consentedAt);
                  return found;
                })
            .orElseGet(
                () ->
                    new BillingKey(
                        registration.getOrganizationId(),
                        registration.getCustomerId(),
                        registration.getMode(),
                        billing.billingKey(),
                        billing.card().issuerCode(),
                        billing.card().number(),
                        authenticatedAt,
                        consentDocumentVersion,
                        consentedAt));
    return Optional.of(
        new Replacement(
            PaymentMethodResponse.from(billingKeyRepository.saveAndFlush(billingKey)),
            replacedBillingKey));
  }

  private void delete(Issuance issuance, String billingKey) {
    PaymentMethodRegistration registration = issuance.registration();
    switch (tossPaymentsClient.deleteBillingKey(issuance.secretKey(), billingKey)) {
      case TossPaymentsResult.Success<Void> deleted ->
          logger.info(
              "toss payments billing key deleted: organization_id={}, customer_id={}, mode={}",
              registration.getOrganizationId(),
              registration.getCustomerId(),
              registration.getMode());
      case TossPaymentsResult.Rejected<Void> rejected ->
          logger.warn(
              "toss payments rejected billing key deletion: organization_id={}, customer_id={},"
                  + " mode={}, code={}, message={}",
              registration.getOrganizationId(),
              registration.getCustomerId(),
              registration.getMode(),
              rejected.code(),
              new TossPaymentsError(rejected.code(), rejected.message()).singleLineMessage());
      case TossPaymentsResult.Unknown<Void> unknown ->
          logger.warn(
              "toss payments billing key deletion result unknown: organization_id={},"
                  + " customer_id={}, mode={}, reason={}",
              registration.getOrganizationId(),
              registration.getCustomerId(),
              registration.getMode(),
              unknown.reason());
    }
  }

  private static OffsetDateTime now() {
    return OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS);
  }

  record Issuance(PaymentMethodRegistration registration, String secretKey) {

    @Override
    public String toString() {
      return "Issuance[registration="
          + registration
          + ", secretKey="
          + SecretMask.lastFour(secretKey)
          + "]";
    }
  }

  record Replacement(PaymentMethodResponse paymentMethod, String replacedBillingKey) {

    @Override
    public String toString() {
      return "Replacement[paymentMethod="
          + paymentMethod
          + ", replacedBillingKey="
          + SecretMask.lastFour(replacedBillingKey)
          + "]";
    }
  }
}
