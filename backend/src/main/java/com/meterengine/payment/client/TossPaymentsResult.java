package com.meterengine.payment.client;

public sealed interface TossPaymentsResult<T> {

  record Success<T>(T value) implements TossPaymentsResult<T> {}

  record Rejected<T>(int status, String code, String message) implements TossPaymentsResult<T> {}

  record Unknown<T>(String reason) implements TossPaymentsResult<T> {}
}
