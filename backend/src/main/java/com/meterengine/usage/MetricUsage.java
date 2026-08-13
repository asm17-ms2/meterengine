package com.meterengine.usage;

import com.meterengine.metric.BillableMetric;
import java.util.List;

/**
 * 미터 하나의 기간 집계 결과 (MS2-129).
 *
 * <p>미터를 코드만이 아니라 {@link BillableMetric} 통째로 싣는다. 이 결과를 받아 금액을 계산할 MS2-124가 단가를 쓰려고 미터를 다시 조회하지 않게
 * 하기 위해서다.
 *
 * @param customers 도입사의 모든 고객. 이벤트가 한 건도 없는 고객도 quantity 0으로 들어 있다 (MS2-129 팀 결정)
 */
public record MetricUsage(BillableMetric metric, List<CustomerUsage> customers) {}
