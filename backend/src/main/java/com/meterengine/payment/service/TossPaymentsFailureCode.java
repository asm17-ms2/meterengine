package com.meterengine.payment.service;

import com.meterengine.payment.client.TossPaymentsResult;
import org.springframework.http.HttpStatus;

// 토스 거절에서 실패 코드를 뽑는 규칙을 한곳에 둔다.
final class TossPaymentsFailureCode {

  static final String NOT_FOUND_PAYMENT = "NOT_FOUND_PAYMENT";
  private static final String HTTP_STATUS_PREFIX = "HTTP_";

  private TossPaymentsFailureCode() {}

  static String of(TossPaymentsResult.Rejected<?> rejected) {
    return rejected.code() == null ? httpStatus(rejected.status()) : rejected.code();
  }

  static String httpStatus(int status) {
    return HTTP_STATUS_PREFIX + status;
  }

  static boolean isNotFoundPayment(TossPaymentsResult.Rejected<?> rejected) {
    return rejected.status() == HttpStatus.NOT_FOUND.value()
        && NOT_FOUND_PAYMENT.equals(rejected.code());
  }
}
