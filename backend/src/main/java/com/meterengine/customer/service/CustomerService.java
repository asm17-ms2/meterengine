package com.meterengine.customer.service;

import com.meterengine.customer.entity.Customer;
import com.meterengine.customer.exception.CustomerHasEventsException;
import com.meterengine.customer.exception.CustomerNotFoundException;
import com.meterengine.customer.repository.CustomerRepository;
import com.meterengine.event.repository.EventRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 고객 등록/수정/삭제와 목록 조회 (MS2-155).
 *
 * <p>고객을 만들거나 고치는 일은 이 클래스만 한다. 다른 도메인이 쓰는 것은 {@link CustomerRepository}의 읽기 메서드뿐이다.
 *
 * <p>도입사 존재 여부를 확인하지 않는다. 등록 때 없는 도입사를 보내면 FK가 거절하고 그것을 컨트롤러 쪽 advice가 400으로 바꾼다. 미리 조회해 봐야 그 조회와
 * INSERT 사이가 다시 경합 구간이라 어차피 FK를 신뢰해야 한다. 삭제도 같은 태도다 ({@link #delete} 참조).
 *
 * <p><b>{@link EventRepository}를 참조한다.</b> 삭제가 이벤트 유무를 물어야 해서인데, event 쪽도 수집할 때 {@link
 * CustomerRepository}를 보므로 두 패키지가 서로를 참조하게 된다. 그 조회를 이쪽에 SQL로 두면 참조는 없어지지만 usage_event를 모르는 패키지가 그
 * 테이블을 읽게 되어, 스키마가 바뀔 때 컴파일러도 event 쪽 테스트도 잡지 못한다. 참조가 실제로 걸림돌이 되는 시점(모듈 경계를 강제할 때)에는 필요한 조회를 이
 * 패키지의 인터페이스로 선언하고 event가 구현하게 해서 방향을 되돌릴 수 있다.
 */
@Service
public class CustomerService {

  private final CustomerRepository customers;
  private final EventRepository events;

  CustomerService(CustomerRepository customers, EventRepository events) {
    this.customers = customers;
    this.events = events;
  }

  /** 이 도입사의 고객 전부를 이름순으로. 없으면 빈 목록이다. */
  @Transactional(readOnly = true)
  public List<Customer> list(UUID organizationId) {
    return customers.findByOrganizationIdOrderByNameAscIdAsc(organizationId);
  }

  /**
   * 고객을 만든다.
   *
   * <p>id를 앱에서 발급한다. 컬럼에 {@code DEFAULT gen_random_uuid()}가 있지만 JPA로 저장하면 그 기본값이 쓰이지 않는다. 엔티티가 id를
   * 들고 있어야 저장 직후 그 값을 응답에 실을 수 있기도 하다.
   *
   * <p>이름 중복을 막지 않는다. 스키마에 유니크 제약이 없고, 같은 이름의 계열사나 부서를 따로 관리하는 것을 막을 근거가 지금 없다. 구별은 화면이 함께 보여주는
   * customer_id의 몫이다.
   *
   * <p>{@code save()}가 id를 이미 가진 엔티티를 받으면 Spring Data JPA는 새 것이 아니라고 보고 {@code merge}로 간다. INSERT
   * 앞에 SELECT가 한 번 붙는다는 뜻인데, 고객 등록은 드문 작업이라 그 한 번을 없애려고 {@code EntityManager}를 직접 다루는 코드를 들이지 않는다.
   *
   * <p><b>{@code saveAndFlush}인 이유.</b> 그냥 {@code save}면 INSERT가 이 메서드가 아니라 트랜잭션이 끝날 때 나간다. 그러면
   * organization_id가 없는 도입사를 가리킬 때 나는 FK 위반이 서비스 밖에서 터져, 어느 예외가 어디서 잡히는지가 트랜잭션 경계에 따라 달라진다. 실제로 그
   * 상태에서는 없는 도입사로 등록해도 201이 나갔다(실측). 여기서 flush하면 위반이 이 호출 안에서 나고 컨트롤러 advice가 400으로 바꾼다.
   */
  @Transactional
  public Customer create(UUID organizationId, String name) {
    return customers.saveAndFlush(new Customer(UUID.randomUUID(), organizationId, name));
  }

  /**
   * 고객 이름을 바꾼다.
   *
   * <p>조회가 도입사를 함께 걸러서, 남의 도입사 고객은 여기서 예외가 된다.
   *
   * <p>UPDATE를 직접 부르지 않고 엔티티를 고친다. 트랜잭션이 끝날 때 변경 감지가 UPDATE를 낸다.
   */
  @Transactional
  public Customer rename(UUID organizationId, UUID customerId, String name) {
    Customer customer =
        customers
            .findByOrganizationIdAndId(organizationId, customerId)
            .orElseThrow(() -> new CustomerNotFoundException(organizationId, customerId));
    customer.rename(name);
    return customer;
  }

  /**
   * 고객을 지운다. 이벤트가 한 건이라도 있으면 지우지 않고 거절한다.
   *
   * <p><b>행을 실제로 지운다.</b> 지워진 표시를 남기는 방식(소프트 삭제)을 쓰지 않는 이유는, 지워도 되는 고객이 곧 이벤트가 하나도 없는 고객이라 남길 것이
   * 없어서다. 행이 남으면 모든 조회가 "지워진 것은 빼고"라는 조건을 들고 다녀야 하고, 그 조건을 한 곳에서 빠뜨리면 지운 고객이 화면에 되살아난다.
   *
   * <p><b>막는 층이 둘이다.</b> 아래 {@code existsFor} 확인이 하나이고, V1의 복합 FK {@code
   * usage_event_customer_same_org}가 다른 하나다. 앱의 확인은 사용자에게 쓸 만한 409를 주는 몫이고, FK는 앱을 거치지 않는 경로까지 막는
   * 몫이다.
   *
   * <p><b>확인과 DELETE 사이에 이벤트가 커밋되는 창은 FK가 닫는다.</b> 그래서 행 잠금을 따로 걸지 않는다. 이벤트를 넣는 트랜잭션이 고객 행에 FK 검사용
   * 잠금(FOR KEY SHARE)을 잡고 있으면 DELETE는 그것과 충돌해 기다리다가 FK 위반으로 실패한다. 그 실패를 아래에서 409로 바꾼다. 소프트 삭제였다면
   * deleted_at 갱신이 키 컬럼을 건드리지 않아 그 잠금과 충돌하지 않고, 그래서 앱이 직접 잠가야 했다.
   *
   * <p>{@code flush}가 필요한 이유는 등록과 같다. 없으면 DELETE가 트랜잭션이 끝날 때 나가서 FK 위반이 이 메서드 밖에서 터지고, 아래 {@code
   * catch}를 비껴간다.
   */
  @Transactional
  public void delete(UUID organizationId, UUID customerId) {
    Customer customer =
        customers
            .findByOrganizationIdAndId(organizationId, customerId)
            .orElseThrow(() -> new CustomerNotFoundException(organizationId, customerId));

    if (events.existsForCustomer(organizationId, customerId)) {
      throw new CustomerHasEventsException(customerId);
    }

    try {
      customers.delete(customer);
      customers.flush();
    } catch (DataIntegrityViolationException exception) {
      // customer 행을 참조하는 제약은 usage_event의 복합 FK 하나뿐이다. 그래서 여기 오는 경우도 하나다.
      // 위 확인을 통과한 뒤 DELETE 전에 그 고객의 이벤트가 커밋된 것. 결과는 확인에 걸린 것과 같아야 한다.
      throw new CustomerHasEventsException(customerId);
    }
  }
}
