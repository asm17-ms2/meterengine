package com.meterengine.payment.service;

import com.meterengine.payment.config.PaymentProperties;
import java.time.OffsetDateTime;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class PaymentRetryPolicy {

  static final String ALREADY_REMOVED_BILLING_KEY = "ALREADY_REMOVED_BILLING_KEY";
  private static final Set<String> NON_RETRYABLE_FAILURE_CODES =
      Set.of(
          "INVALID_STOPPED_CARD",
          "INVALID_CARD_LOST_OR_STOLEN",
          "INVALID_CARD_EXPIRATION",
          "INVALID_CARD_NUMBER",
          "INVALID_REJECT_CARD",
          "INVALID_BILL_KEY_REQUEST",
          "NOT_SUPPORTED_CARD_TYPE",
          "NOT_MATCHES_CUSTOMER_KEY",
          "NOT_REGISTERED_CARD_COMPANY",
          ALREADY_REMOVED_BILLING_KEY);

  private final PaymentProperties paymentProperties;

  PaymentRetryPolicy(PaymentProperties paymentProperties) {
    this.paymentProperties = paymentProperties;
  }

  // 실패한 시도를 다시 할 시각을 돌려주고, 다시 하지 않으면 null이다.
  public OffsetDateTime nextRetryAt(
      String failureCode,
      long attemptCount,
      OffsetDateTime failedAt,
      OffsetDateTime billingKeyAuthenticatedAt) {
    if (!isRetryable(failureCode, failedAt, billingKeyAuthenticatedAt)
        || attemptCount >= paymentProperties.retryMaxAttempts()
        || isExpiredNotFound(failureCode, failedAt)) {
      return null;
    }
    return failedAt.plus(paymentProperties.retryInterval());
  }

  Set<String> nonRetryableFailureCodes() {
    return NON_RETRYABLE_FAILURE_CODES;
  }

  // 없음 실패는 이 시각 뒤에 끝난 것만 토스에 다시 물어 재시도한다.
  OffsetDateTime notFoundRetryableFrom(OffsetDateTime now) {
    return now.minus(PaymentAttemptConfirmationService.CONFIRMATION_WINDOW);
  }

  private boolean isExpiredNotFound(String failureCode, OffsetDateTime failedAt) {
    return TossPaymentsFailureCode.NOT_FOUND_PAYMENT.equals(failureCode)
        && failedAt.isBefore(notFoundRetryableFrom(OffsetDateTime.now()));
  }

  // 카드 오류로 실패했어도 그 뒤에 카드를 다시 등록했으면 다시 한다.
  // 지워진 빌링키로 실패했는데 지금 빌링키가 있으면 새로 등록한 카드다.
  private static boolean isRetryable(
      String failureCode, OffsetDateTime failedAt, OffsetDateTime billingKeyAuthenticatedAt) {
    if (failureCode == null) {
      return false;
    }
    if (!NON_RETRYABLE_FAILURE_CODES.contains(failureCode)) {
      return true;
    }
    if (billingKeyAuthenticatedAt == null) {
      return false;
    }
    return ALREADY_REMOVED_BILLING_KEY.equals(failureCode)
        || billingKeyAuthenticatedAt.isAfter(failedAt);
  }
}
