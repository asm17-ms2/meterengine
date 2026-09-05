package com.meterengine.pricing.exception;

/**
 * 그 미터에 가격 정책이 이미 있을 때 (MS2-157). 409로 매핑된다.
 *
 * <p>미터당 정책이 1개라는 것은 price_policy의 PK가 강제하는 불변식이다. 덮어쓰지 않는 이유는 청구 예정액이 읽는 단가를 등록 요청이 조용히 바꾸게 되어서다.
 * 수정은 정책 이력(버전) 논의가 정리된 뒤 별도 티켓의 몫이다.
 */
public class PricePolicyAlreadyExistsException extends RuntimeException {

  public PricePolicyAlreadyExistsException(String billableMetricCode) {
    super(
        "metric %s already has a price policy; it cannot be registered twice"
            .formatted(billableMetricCode));
  }
}
