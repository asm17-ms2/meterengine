package com.meterengine.pricing.repository;

import com.meterengine.pricing.entity.PricePolicy;
import com.meterengine.pricing.entity.PricePolicyId;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 가격 정책의 쓰기 모델 (MS2-157).
 *
 * <p>MS2-158이 JdbcTemplate로 시작한 pricing 리포지토리를 {@link PriceRateRepository}와 함께 JPA로 통일했다 (PR 43 리뷰
 * 결정). TEXT[]는 Hibernate가 배열 타입으로 바로 매핑해 JdbcTemplate을 고를 이유가 사라졌다.
 */
public interface PricePolicyRepository extends JpaRepository<PricePolicy, PricePolicyId> {}
