package com.meterengine.customer.repository;

import com.meterengine.customer.entity.Customer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 고객 조회 (MS2-130 이벤트 수집, MS2-129 집계, MS2-155 고객 관리가 쓰는 최소 범위).
 *
 * <p>세 메서드 다 organization_id를 조건에 함께 넣는다. 도입사를 빼먹은 조회가 만들어지지 않게 이름 자체에 박아 둔 것이다.
 */
public interface CustomerRepository extends JpaRepository<Customer, UUID> {

  /**
   * 이 도입사에 속한 고객인지 확인한다.
   *
   * <p>organization_id가 조건에 함께 들어가므로 다른 도입사 소속 고객은 자연히 "없음"이 된다. 미등록과 타 도입사 소속을 구별해 응답하지 않는 편이 맞다.
   * 남의 도입사에 그 고객이 있다는 사실을 흘리지 않는다.
   */
  boolean existsByOrganizationIdAndId(UUID organizationId, UUID id);

  /**
   * 이 도입사의 고객을 전부 가져온다 (MS2-129).
   *
   * <p>집계가 이 목록을 기준으로 삼는다. 이벤트를 한 건도 보내지 않은 고객이 사용량 0으로 응답에 나오려면 이벤트 쪽이 아니라 고객 쪽이 기준이어야 한다.
   *
   * <p>정렬을 이름으로 고정한다. 없으면 반환 순서가 보장되지 않아 같은 데이터에도 화면의 행 순서가 매번 달라진다. 동명이인이 있어도 순서가 흔들리지 않도록 id를 두
   * 번째 키로 둔다.
   */
  List<Customer> findByOrganizationIdOrderByNameAscIdAsc(UUID organizationId);

  /**
   * 고치거나 지울 고객 하나를 집어 온다 (MS2-155).
   *
   * <p>{@code findById}를 쓰지 않는 이유는 그것이 도입사를 보지 않아서다. 그러면 남의 도입사 고객의 이름을 UUID만 알면 고칠 수 있고, 지울 수도 있다.
   *
   * <p>삭제에 행 잠금({@code SELECT ... FOR UPDATE})을 걸지 않는다. 지키려는 것은 "이벤트가 있는 고객은 사라지지 않는다"인데, 물리
   * DELETE는 그것을 DB가 직접 막는다. 이벤트를 넣는 트랜잭션이 고객 행에 FK 검사용 공유 잠금(FOR KEY SHARE)을 잡고, DELETE는 그것과 충돌하는
   * 잠금을 요구해 기다린 뒤 FK 위반으로 실패한다. 앱이 따로 잠글 이유가 없다.
   */
  Optional<Customer> findByOrganizationIdAndId(UUID organizationId, UUID id);
}
