package com.meterengine.customer;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 고객 조회 (MS2-130에서 이벤트 수집이 쓰는 최소 범위). */
@Repository
public class CustomerRepository {

  private final JdbcTemplate jdbc;

  CustomerRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * 이 도입사에 속한 고객인지 확인한다.
   *
   * <p>organization_id를 조건에 함께 넣으므로 다른 도입사 소속 고객은 자연히 "없음"이 된다. 미등록과 타 도입사 소속을 구별해 응답하지 않는 편이 맞다.
   * 남의 도입사에 그 고객이 있다는 사실을 흘리지 않는다.
   */
  public boolean existsInOrganization(UUID organizationId, UUID customerId) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM customer WHERE organization_id = ? AND id = ?)",
            Boolean.class,
            organizationId,
            customerId));
  }
}
