package com.meterengine.customer.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meterengine.customer.entity.Customer;
import com.meterengine.customer.exception.CustomerHasEventsException;
import com.meterengine.customer.exception.CustomerNotFoundException;
import com.meterengine.customer.repository.CustomerRepository;
import com.meterengine.event.repository.EventRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 삭제 분기 검증 (MS2-155).
 *
 * <p><b>왜 단위 테스트인가.</b> 여기서 보려는 것은 {@code delete()}가 두 갈래를 같은 결과로 모으는가다. 사전 확인에 걸린 경우와, 확인을 통과한 뒤
 * DB가 거절한 경우. 뒤쪽은 확인과 DELETE 사이에 다른 트랜잭션이 이벤트를 커밋해야 나는데, 그 순간을 통합 테스트에서 만들려면 서비스가 두 문장 사이에서 멈춰 줘야
 * 한다. 멈춰 주지 않으므로 여기서는 DB가 거절한 상황만 만들어 그 뒤의 처리를 본다.
 *
 * <p>그 거절이 실제로 일어나는지는 {@code CustomerDeleteConcurrencyTest}가 커넥션 두 개로 확인한다. 둘이 짝이다. 저쪽이 "DB가 정말
 * 거절한다"를 보고, 이쪽이 "거절당했을 때 무엇을 내보내는가"를 본다.
 *
 * <p>이 테스트가 없으면 {@code catch} 절은 어느 경로로도 실행되지 않는다. 사전 확인이 먼저 걸려서다.
 */
@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

  private static final UUID ORG_ID = UUID.randomUUID();
  private static final UUID CUSTOMER_ID = UUID.randomUUID();

  @Mock private CustomerRepository customers;
  @Mock private EventRepository events;

  private CustomerService customerService;

  @BeforeEach
  void setUp() {
    customerService = new CustomerService(customers, events);
  }

  @Test
  void 이벤트가_있으면_지우지_않고_409_예외다() {
    when(customers.findByOrganizationIdAndId(ORG_ID, CUSTOMER_ID))
        .thenReturn(Optional.of(customer()));
    when(events.existsForCustomer(ORG_ID, CUSTOMER_ID)).thenReturn(true);

    assertThatThrownBy(() -> customerService.delete(ORG_ID, CUSTOMER_ID))
        .isInstanceOf(CustomerHasEventsException.class);

    verify(customers, never()).delete(org.mockito.ArgumentMatchers.any());
  }

  /**
   * 확인을 통과한 뒤 DB가 거절해도 결과가 같아야 한다.
   *
   * <p>확인과 DELETE 사이에 그 고객의 이벤트가 커밋된 상황이다. 여기서 예외를 그대로 흘리면 컨트롤러 advice의 {@code
   * DataIntegrityViolationException} 핸들러가 잡아 "X-Organization-Id가 등록된 도입사인지 확인하라"는 400을 낸다. 도입사는
   * 멀쩡한데 헤더를 의심하게 만드는 응답이다.
   */
  @Test
  void 확인_뒤에_DB가_거절하면_같은_409_예외로_바뀐다() {
    when(customers.findByOrganizationIdAndId(ORG_ID, CUSTOMER_ID))
        .thenReturn(Optional.of(customer()));
    when(events.existsForCustomer(ORG_ID, CUSTOMER_ID)).thenReturn(false);
    doThrow(new DataIntegrityViolationException("usage_event_customer_same_org"))
        .when(customers)
        .flush();

    assertThatThrownBy(() -> customerService.delete(ORG_ID, CUSTOMER_ID))
        .isInstanceOf(CustomerHasEventsException.class);
  }

  @Test
  void 없는_고객을_지우면_404_예외다() {
    when(customers.findByOrganizationIdAndId(ORG_ID, CUSTOMER_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> customerService.delete(ORG_ID, CUSTOMER_ID))
        .isInstanceOf(CustomerNotFoundException.class);

    verify(events, never()).existsForCustomer(ORG_ID, CUSTOMER_ID);
  }

  private Customer customer() {
    return new Customer(CUSTOMER_ID, ORG_ID, "아크메");
  }
}
