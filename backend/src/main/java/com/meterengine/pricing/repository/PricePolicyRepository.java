package com.meterengine.pricing.repository;

import com.meterengine.pricing.entity.PricePolicy;
import com.meterengine.pricing.entity.PricePolicyId;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 가격 정책의 쓰기 모델 (MS2-157).
 *
 * <p>단가 읽기({@link PriceRateRepository})와 달리 JPA다 (PR 43 리뷰 결정). 단가 등록이 MS2-177로 빠지면서 JSONB 매핑이 이
 * 모델에서 없어졌고, 남은 TEXT[]는 Hibernate가 배열 타입으로 바로 매핑해 JdbcTemplate을 고를 이유가 사라졌다.
 */
public interface PricePolicyRepository extends JpaRepository<PricePolicy, PricePolicyId> {}
