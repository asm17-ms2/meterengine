package com.meterengine.metric.dto;

import com.meterengine.metric.entity.BillableMetric;
import java.util.List;

/**
 * 미터 하나의 기간 집계 결과 (MS2-129).
 *
 * <p>미터를 코드만이 아니라 {@link BillableMetric} 통째로 싣는다. 금액 계산(MS2-124)이 라인을 만들 때 code와 target_property를
 * 쓰기 때문이다. 단가는 MS2-158부터 미터에 없고, 계산 쪽이 price_rate에서 따로 조회한다.
 *
 * @param customers 도입사의 모든 고객. 이벤트가 한 건도 없는 고객도 quantity 0으로 들어 있다 (MS2-129 팀 결정)
 */
public record MetricUsage(BillableMetric metric, List<CustomerUsage> customers) {}
