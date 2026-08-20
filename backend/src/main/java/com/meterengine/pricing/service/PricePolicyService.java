package com.meterengine.pricing.service;

import com.meterengine.metric.entity.BillableMetricId;
import com.meterengine.metric.repository.BillableMetricRepository;
import com.meterengine.pricing.dto.PricePolicyResponse;
import com.meterengine.pricing.dto.SavePricePolicyRequest;
import com.meterengine.pricing.entity.PricePolicy;
import com.meterengine.pricing.entity.PricePolicyId;
import com.meterengine.pricing.exception.InvalidPricePolicyException;
import com.meterengine.pricing.exception.MetricNotFoundException;
import com.meterengine.pricing.exception.PricePolicyAlreadyExistsException;
import com.meterengine.pricing.repository.PricePolicyRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 가격 정책 등록 (MS2-157).
 *
 * <p>정책(축 선언)만 등록한다. 단가는 유효 기간 같은 자체 수명을 가질 예정이라 정책과 등록 시점이 다르고, MS2-177의 단가 API가 따로 붙인다 (PR 43 리뷰
 * 결정). 단가가 아직 없는 미터는 청구 예정액 계산이 라인에서 제외하므로 정책만 있는 상태가 안전하다.
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

    validate(request.dimensionProperties());

    if (policies.existsById(new PricePolicyId(organizationId, metricCode))) {
      throw new PricePolicyAlreadyExistsException(metricCode);
    }

    PricePolicy policy = new PricePolicy(organizationId, metricCode, request.dimensionProperties());
    try {
      // flush까지 해야 확인과 INSERT 사이에 커밋된 경합이 여기서 제약 위반으로 터진다.
      // 커밋 시점으로 미루면 예외가 이 메서드 밖에서 나 409로 바꿀 수 없다.
      policies.saveAndFlush(policy);
    } catch (DataIntegrityViolationException exception) {
      throw new PricePolicyAlreadyExistsException(metricCode);
    }

    return PricePolicyResponse.from(policy);
  }

  /**
   * 선언 안의 관계를 검증한다. 필드 형식은 Bean Validation이 먼저 본다.
   *
   * <p>중복 키와 빈 키를 여기서 잡는 이유: TEXT[]는 DB가 못 막고, 저장된 선언은 조합별 단가(MS2-177)의 키 집합 검증 기준이 되므로 여기가 방어선이다
   * (V2 마이그레이션 주석).
   */
  private void validate(List<String> dimensionProperties) {
    Set<String> declared = new HashSet<>(dimensionProperties);
    if (declared.size() < dimensionProperties.size()) {
      throw new InvalidPricePolicyException("dimension_properties has duplicate keys");
    }
    if (dimensionProperties.stream().anyMatch(key -> key == null || key.isBlank())) {
      throw new InvalidPricePolicyException("dimension_properties has a blank key");
    }
  }
}
