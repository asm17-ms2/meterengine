package com.meterengine.metric.repository;

import com.meterengine.metric.entity.BillableMetric;
import com.meterengine.metric.entity.BillableMetricId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** 과금 지표 조회 (MS2-129에서 집계가 쓰는 최소 범위). */
public interface BillableMetricRepository extends JpaRepository<BillableMetric, BillableMetricId> {

  /**
   * 이 도입사의 미터를 전부 가져온다.
   *
   * <p>정렬을 code로 고정한다. 없으면 반환 순서가 보장되지 않아 같은 데이터에도 응답의 미터 순서가 달라지고, 화면과 테스트가 그때그때 다른 순서를 본다.
   */
  List<BillableMetric> findByOrganizationIdOrderByCodeAsc(UUID organizationId);
}
