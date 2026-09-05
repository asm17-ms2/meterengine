package com.meterengine.pricing.exception;

import java.util.UUID;

/**
 * 경로가 가리킨 미터를 찾지 못했을 때 (MS2-157). 404로 매핑된다.
 *
 * <p>세 경우가 한 예외로 묶인다. 그런 미터가 없거나, 다른 도입사 소속이거나, 도입사 자체가 등록되지 않았거나. 미터가 도입사에 속하므로(복합 PK) 셋을 구별할 수
 * 없고, 구별해 답하면 남의 도입사에 그 미터가 있다는 사실이 새어 나간다. 고객 API의 {@code CustomerNotFoundException}과 같은 태도다.
 *
 * <p>미등록 도입사가 400 {@code unknown_organization}이 아니라 여기로 오는 이유: 그 도입사의 미터는 존재할 수 없어서 미터 존재 확인이 먼저
 * 걸린다. FK 위반까지 도달하는 경로가 없다.
 */
public class MetricNotFoundException extends RuntimeException {

  public MetricNotFoundException(UUID organizationId, String billableMetricCode) {
    super(
        "no metric %s in organization %s; it may not exist or belong to another organization"
            .formatted(billableMetricCode, organizationId));
  }
}
