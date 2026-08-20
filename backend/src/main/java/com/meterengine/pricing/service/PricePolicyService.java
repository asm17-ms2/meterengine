package com.meterengine.pricing.service;

import com.meterengine.metric.entity.BillableMetricId;
import com.meterengine.metric.repository.BillableMetricRepository;
import com.meterengine.pricing.dto.PricePolicyResponse;
import com.meterengine.pricing.dto.SavePricePolicyRequest;
import com.meterengine.pricing.exception.InvalidPricePolicyException;
import com.meterengine.pricing.exception.MetricNotFoundException;
import com.meterengine.pricing.exception.PricePolicyAlreadyExistsException;
import com.meterengine.pricing.repository.PricePolicyRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 가격 정책 등록 (MS2-157).
 *
 * <p>정책과 단가를 한 트랜잭션으로 넣는다. 어느 한쪽만 들어간 상태가 남으면 청구 예정액 계산이 그 미터에서 실패하거나(단가 없음) 죽은 행이 생긴다(정책 없음 -- 이쪽은
 * FK가 이미 막지만).
 *
 * <p><b>{@link BillableMetricRepository}를 참조한다.</b> 미터 존재를 확인해 404를 주는 몫이다. 확인 없이 FK 위반에 맡기면 "없는
 * 미터"와 "미등록 도입사"가 모두 같은 예외 타입이라 상태 코드를 갈라낼 수 없다. 이 참조로 pricing이 metric에 의존하게 된다 (README의 의존 방향 참조).
 */
@Service
public class PricePolicyService {

  private final PricePolicyRepository policies;
  private final BillableMetricRepository metrics;

  PricePolicyService(PricePolicyRepository policies, BillableMetricRepository metrics) {
    this.policies = policies;
    this.metrics = metrics;
  }

  @Transactional
  public PricePolicyResponse register(
      UUID organizationId, String metricCode, SavePricePolicyRequest request) {
    if (!metrics.existsById(new BillableMetricId(organizationId, metricCode))) {
      throw new MetricNotFoundException(organizationId, metricCode);
    }

    validate(request);

    if (policies.exists(organizationId, metricCode)) {
      throw new PricePolicyAlreadyExistsException(metricCode);
    }

    try {
      policies.insertPolicy(organizationId, metricCode, request.dimensionProperties());
    } catch (DuplicateKeyException exception) {
      // 위 확인과 INSERT 사이에 같은 미터의 정책이 커밋된 경합. 결과는 확인에 걸린 것과 같아야 한다.
      throw new PricePolicyAlreadyExistsException(metricCode);
    }
    for (SavePricePolicyRequest.RateEntry rate : request.rates()) {
      policies.insertRate(organizationId, metricCode, rate.dimensionValues(), rate.unitPrice());
    }

    return PricePolicyResponse.from(metricCode, request);
  }

  /**
   * 필드 사이의 관계를 검증한다. 필드 하나씩의 형식은 Bean Validation이 먼저 본다.
   *
   * <p>조합 중복은 DB PK도 막지만 여기서 선검증한다. PK 위반에 맡기면 어느 조합이 겹쳤는지 사용자에게 말해줄 수 없고, 정책 경합의 409와 같은 예외 타입으로
   * 뭉개진다.
   */
  private void validate(SavePricePolicyRequest request) {
    Set<String> declared = declaredProperties(request.dimensionProperties());

    Set<Map<String, String>> combinations = new HashSet<>();
    boolean hasBaseRate = false;
    for (SavePricePolicyRequest.RateEntry rate : request.rates()) {
      Map<String, String> values = rate.dimensionValues();
      if (!combinations.add(values)) {
        throw new InvalidPricePolicyException(
            "rates has duplicate dimension_values combination: %s".formatted(values));
      }
      if (values.isEmpty()) {
        hasBaseRate = true;
        continue;
      }
      if (!values.keySet().equals(declared)) {
        throw new InvalidPricePolicyException(
            "dimension_values keys %s do not match dimension_properties %s"
                .formatted(values.keySet(), declared));
      }
      if (values.values().stream().anyMatch(value -> value == null || value.isBlank())) {
        throw new InvalidPricePolicyException(
            "dimension_values has a blank value in combination %s".formatted(values));
      }
    }
    if (!hasBaseRate) {
      throw new InvalidPricePolicyException(
          "rates must include a base rate whose dimension_values is an empty object ({})");
    }
  }

  private Set<String> declaredProperties(List<String> dimensionProperties) {
    Set<String> declared = new HashSet<>(dimensionProperties);
    if (declared.size() < dimensionProperties.size()) {
      throw new InvalidPricePolicyException("dimension_properties has duplicate keys");
    }
    if (dimensionProperties.stream().anyMatch(key -> key == null || key.isBlank())) {
      throw new InvalidPricePolicyException("dimension_properties has a blank key");
    }
    return declared;
  }
}
