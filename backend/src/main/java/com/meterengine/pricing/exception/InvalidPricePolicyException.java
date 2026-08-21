package com.meterengine.pricing.exception;

/**
 * 요청 본문이 형식은 맞지만 정책으로 성립하지 않을 때 (MS2-157). 400으로 매핑된다.
 *
 * <p>{@code validation_error}(필드 하나씩의 형식)와 갈라 두는 이유: 여기 걸리는 것은 필드 사이의 관계다. 조합의 키 집합이 선언과 다르거나, 기본
 * 단가 행이 없거나, 같은 조합이 두 번 왔거나. 어느 한 필드를 짚을 수 없어 {@code errors} 배열 대신 detail이 사유를 담는다.
 *
 * <p>이 검증이 등록 경계에 있는 이유는 V2 마이그레이션 주석에 있다. JSONB 키 오타는 DB가 못 잡고, 선언과 어긋난 단가 행은 계산이 도달하지 못하는 죽은 행이
 * 된다.
 */
public class InvalidPricePolicyException extends RuntimeException {

  public InvalidPricePolicyException(String detail) {
    super(detail);
  }
}
