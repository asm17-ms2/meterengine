package com.meterengine.payment.service;

import com.meterengine.audit.service.AuditLogService;
import com.meterengine.global.error.ConflictException;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.ErrorResponse.FieldError;
import com.meterengine.global.error.InvalidRequestException;
import com.meterengine.global.error.NotFoundException;
import com.meterengine.global.error.ServiceUnavailableException;
import com.meterengine.global.validation.ArchiveStatus;
import com.meterengine.payment.client.TossPaymentsClient;
import com.meterengine.payment.client.TossPaymentsPayment;
import com.meterengine.payment.client.TossPaymentsResult;
import com.meterengine.payment.dto.ListTossPaymentsCredentialsResponse;
import com.meterengine.payment.dto.TossPaymentsCredentialResponse;
import com.meterengine.payment.dto.UpdateTossPaymentsCredentialRequest;
import com.meterengine.payment.entity.TossPaymentsCredential;
import com.meterengine.payment.entity.TossPaymentsCredentialId;
import com.meterengine.payment.repository.PaymentArchiveRepository;
import com.meterengine.payment.repository.TossPaymentsCredentialRepository;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class TossPaymentsCredentialService {

  private static final String CREDENTIAL_CHECK_ORDER_ID_PREFIX = "credential-check-";
  private static final String CLIENT_KEY_INFIX = "_ck_";
  private static final String SECRET_KEY_INFIX = "_sk_";
  private static final String KEY_PREFIX_MESSAGE = "%s 모드의 키는 %s 뒤에 값이 이어져야 합니다";
  private static final String SECRET_KEY_CHARACTER_MESSAGE = "시크릿 키에는 콜론, 공백, ASCII 밖 문자를 쓸 수 없습니다";
  private static final String SECRET_KEY_REJECTED_MESSAGE = "토스페이먼츠가 받지 않는 시크릿 키입니다";

  private final TossPaymentsCredentialRepository tossPaymentsCredentialRepository;
  private final TossPaymentsClient tossPaymentsClient;
  private final PaymentArchiveRepository paymentArchiveRepository;
  private final AuditLogService auditLogService;
  private final TransactionTemplate transactionTemplate;

  TossPaymentsCredentialService(
      TossPaymentsCredentialRepository tossPaymentsCredentialRepository,
      TossPaymentsClient tossPaymentsClient,
      PaymentArchiveRepository paymentArchiveRepository,
      AuditLogService auditLogService,
      PlatformTransactionManager platformTransactionManager) {
    this.tossPaymentsCredentialRepository = tossPaymentsCredentialRepository;
    this.tossPaymentsClient = tossPaymentsClient;
    this.paymentArchiveRepository = paymentArchiveRepository;
    this.auditLogService = auditLogService;
    this.transactionTemplate = new TransactionTemplate(platformTransactionManager);
  }

  @Transactional(readOnly = true)
  public ListTossPaymentsCredentialsResponse list(UUID organizationId) {
    return list(organizationId, "active");
  }

  @Transactional(readOnly = true)
  public ListTossPaymentsCredentialsResponse list(UUID organizationId, String status) {
    ArchiveStatus filter = ArchiveStatus.parse(status);
    List<TossPaymentsCredentialResponse> credentials =
        new ArrayList<>(
            tossPaymentsCredentialRepository
                .findByOrganizationIdOrderByModeAsc(organizationId)
                .stream()
                .filter(credential -> filter.includes(credential.getArchivedAt()))
                .map(TossPaymentsCredentialResponse::from)
                .toList());
    if (filter != ArchiveStatus.ACTIVE)
      credentials.addAll(paymentArchiveRepository.credentials(organizationId));
    return new ListTossPaymentsCredentialsResponse(credentials);
  }

  Optional<TossPaymentsCredential> find(UUID organizationId, String mode) {
    return tossPaymentsCredentialRepository.findById(
        new TossPaymentsCredentialId(organizationId, mode));
  }

  Optional<TossPaymentsCredential> historical(
      UUID organizationId, String mode, OffsetDateTime requestedAt) {
    Optional<TossPaymentsCredential> current = find(organizationId, mode);
    if (current.isEmpty() || !current.get().getUpdatedAt().isAfter(requestedAt)) return current;
    return paymentArchiveRepository.historical(organizationId, mode, requestedAt);
  }

  Optional<TossPaymentsCredential> active(UUID organizationId, String mode) {
    return find(organizationId, mode).filter(credential -> credential.getArchivedAt() == null);
  }

  @Transactional
  public void delete(UUID organizationId, String mode) {
    paymentArchiveRepository.lock(organizationId);
    TossPaymentsCredential credential =
        find(organizationId, mode)
            .orElseThrow(() -> new NotFoundException(ErrorCode.TOSS_PAYMENTS_CREDENTIAL_MISSING));
    if (credential.getArchivedAt() != null) return;
    credential.archive();
    auditLogService.record(
        organizationId,
        "toss_payments_credential_archived",
        "toss_payments_credential",
        mode,
        Map.of("mode", mode));
  }

  public TossPaymentsCredentialResponse update(
      UUID organizationId, String mode, UpdateTossPaymentsCredentialRequest request) {
    return update(organizationId, mode, request, null);
  }

  public TossPaymentsCredentialResponse update(
      UUID organizationId,
      String mode,
      UpdateTossPaymentsCredentialRequest request,
      String principalMode) {
    if (principalMode != null && !principalMode.equals(mode)) {
      throw new ConflictException(ErrorCode.PAYMENT_MODE_MISMATCH);
    }
    checkPrefixes(mode, request);
    verify(request.secretKey());
    return transactionTemplate.execute(status -> save(organizationId, mode, request));
  }

  private TossPaymentsCredentialResponse save(
      UUID organizationId, String mode, UpdateTossPaymentsCredentialRequest request) {
    paymentArchiveRepository.lock(organizationId);
    OffsetDateTime verifiedAt = OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS);
    TossPaymentsCredential credential =
        find(organizationId, mode)
            .map(
                existing -> {
                  paymentArchiveRepository.credential(organizationId, mode);
                  existing.update(request.clientKey(), request.secretKey(), verifiedAt);
                  return existing;
                })
            .orElseGet(
                () ->
                    new TossPaymentsCredential(
                        organizationId,
                        mode,
                        request.clientKey(),
                        request.secretKey(),
                        verifiedAt));
    tossPaymentsCredentialRepository.saveAndFlush(credential);
    auditLogService.record(
        organizationId,
        "toss_payments_credential_updated",
        "toss_payments_credential",
        mode,
        Map.of("mode", mode));
    return TossPaymentsCredentialResponse.from(credential);
  }

  private static void checkPrefixes(String mode, UpdateTossPaymentsCredentialRequest request) {
    List<FieldError> errors = new ArrayList<>();
    String clientKeyPrefix = mode + CLIENT_KEY_INFIX;
    if (!hasValueAfterPrefix(request.clientKey(), clientKeyPrefix)) {
      errors.add(new FieldError("client_key", KEY_PREFIX_MESSAGE.formatted(mode, clientKeyPrefix)));
    }
    String secretKeyPrefix = mode + SECRET_KEY_INFIX;
    if (!hasValueAfterPrefix(request.secretKey(), secretKeyPrefix)) {
      errors.add(new FieldError("secret_key", KEY_PREFIX_MESSAGE.formatted(mode, secretKeyPrefix)));
    } else if (!fitsBasicAuth(request.secretKey())) {
      errors.add(new FieldError("secret_key", SECRET_KEY_CHARACTER_MESSAGE));
    }
    if (!errors.isEmpty()) {
      throw new InvalidRequestException(ErrorCode.INVALID_TOSS_PAYMENTS_CREDENTIAL, errors);
    }
  }

  private static boolean hasValueAfterPrefix(String key, String prefix) {
    return key.length() > prefix.length() && key.startsWith(prefix);
  }

  // 시크릿 키는 Basic 인증 사용자 이름으로 보내므로 콜론과 ISO-8859-1 밖 문자를 쓰면 헤더를 만들지 못한다.
  private static boolean fitsBasicAuth(String key) {
    return key.chars().allMatch(c -> c > 0x20 && c < 0x7f && c != ':');
  }

  private void verify(String secretKey) {
    TossPaymentsResult<TossPaymentsPayment> result =
        tossPaymentsClient.findPaymentByOrderId(
            secretKey, CREDENTIAL_CHECK_ORDER_ID_PREFIX + UUID.randomUUID());
    switch (result) {
      case TossPaymentsResult.Success<TossPaymentsPayment> found -> {}
      case TossPaymentsResult.Rejected<TossPaymentsPayment> rejected
          when rejected.status() == HttpStatus.NOT_FOUND.value() -> {}
      case TossPaymentsResult.Rejected<TossPaymentsPayment> rejected
          when rejected.status() == HttpStatus.UNAUTHORIZED.value()
              || rejected.status() == HttpStatus.BAD_REQUEST.value() ->
          throw new InvalidRequestException(
              ErrorCode.INVALID_TOSS_PAYMENTS_CREDENTIAL,
              List.of(new FieldError("secret_key", SECRET_KEY_REJECTED_MESSAGE)));
      case TossPaymentsResult.Rejected<TossPaymentsPayment> rejected ->
          throw new ServiceUnavailableException(ErrorCode.TOSS_PAYMENTS_UNAVAILABLE);
      case TossPaymentsResult.Unknown<TossPaymentsPayment> unknown ->
          throw new ServiceUnavailableException(ErrorCode.TOSS_PAYMENTS_UNAVAILABLE);
    }
  }
}
