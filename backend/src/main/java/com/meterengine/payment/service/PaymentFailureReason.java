package com.meterengine.payment.service;

import com.meterengine.global.error.ErrorCode;
import java.util.Set;

// 토스 오류 코드는 100개가 넘어 지표 라벨로는 이 묶음만 쓰고, 원래 코드는 DB와 로그에 둔다.
enum PaymentFailureReason {
  CARD_DECLINED("card_declined"),
  REQUEST_ERROR("request_error"),
  PG_ERROR("pg_error"),
  UNCONFIRMED("unconfirmed"),
  NOT_REQUESTED("not_requested");

  private static final Set<String> NOT_REQUESTED_CODES =
      Set.of(
          ErrorCode.TOSS_PAYMENTS_CREDENTIAL_MISSING.getCode(),
          ErrorCode.PAYMENT_METHOD_MISSING.getCode());
  private static final Set<String> CARD_DECLINED_CODES =
      Set.of(
          "REJECT_CARD_PAYMENT",
          "REJECT_CARD_COMPANY",
          "REJECT_ACCOUNT_PAYMENT",
          "INVALID_STOPPED_CARD",
          "INVALID_CARD_EXPIRATION",
          "INVALID_REJECT_CARD",
          "INVALID_CARD_LOST_OR_STOLEN",
          "INVALID_CARD_NUMBER",
          "INVALID_PASSWORD",
          "NOT_SUPPORTED_CARD_TYPE",
          "NOT_REGISTERED_CARD_COMPANY",
          "EXCEED_MAX_DAILY_PAYMENT_COUNT",
          "EXCEED_MAX_PAYMENT_AMOUNT",
          "EXCEED_MAX_AMOUNT",
          "EXCEED_MAX_ONE_DAY_AMOUNT",
          "EXCEED_MAX_MONTHLY_PAYMENT_AMOUNT",
          "EXCEED_MAX_AUTH_COUNT",
          "FDS_ERROR");
  private static final Set<String> PG_ERROR_CODES =
      Set.of(
          "PROVIDER_ERROR",
          "CARD_PROCESSING_ERROR",
          "NOT_AVAILABLE_PAYMENT",
          "FAILED_CARD_COMPANY_RESPONSE");

  private final String tag;

  PaymentFailureReason(String tag) {
    this.tag = tag;
  }

  static PaymentFailureReason of(String failureCode) {
    if (NOT_REQUESTED_CODES.contains(failureCode)) {
      return NOT_REQUESTED;
    }
    if (CARD_DECLINED_CODES.contains(failureCode)) {
      return CARD_DECLINED;
    }
    if (PG_ERROR_CODES.contains(failureCode)) {
      return PG_ERROR;
    }
    // 승인 응답을 받지 못해 나중에 조회로 실패를 확정한 것이라 카드사 거절인지 요청이 안 닿은 것인지 모른다.
    if (TossPaymentsFailureCode.NOT_FOUND_PAYMENT.equals(failureCode)
        || TossPaymentsFailureCode.ENDED_WITHOUT_APPROVAL.contains(failureCode)) {
      return UNCONFIRMED;
    }
    // 토스가 4xx로 거절했고 위 묶음에 없는 코드는 우리 요청을 받지 않은 것으로 본다.
    return REQUEST_ERROR;
  }

  String tag() {
    return tag;
  }
}
