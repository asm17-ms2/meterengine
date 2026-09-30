package com.meterengine.customer.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meterengine.customer.entity.Customer;
import com.meterengine.customer.repository.CustomerRepository;
import com.meterengine.event.repository.EventRepository;
import com.meterengine.global.error.BusinessException;
import com.meterengine.global.error.ConflictException;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.NotFoundException;
import com.meterengine.payment.repository.PaymentAttemptRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

  private static final UUID ORGANIZATION_ID = UUID.randomUUID();
  private static final UUID CUSTOMER_ID = UUID.randomUUID();

  @Mock private CustomerRepository customerRepository;
  @Mock private EventRepository eventRepository;
  @Mock private PaymentAttemptRepository paymentAttemptRepository;

  private CustomerService customerService;

  @BeforeEach
  void setUp() {
    customerService =
        new CustomerService(customerRepository, eventRepository, paymentAttemptRepository);
  }

  @Test
  void 등록_저장에서_난_DataIntegrityViolationException은_바꾸지_않고_그대로_올려_보낸다() {
    DataIntegrityViolationException violation =
        new DataIntegrityViolationException("customer_organization_fk");
    when(customerRepository.saveAndFlush(org.mockito.ArgumentMatchers.any())).thenThrow(violation);

    assertThatThrownBy(() -> customerService.create(ORGANIZATION_ID, "아크메")).isSameAs(violation);
  }

  @Test
  void 이벤트가_있으면_지우지_않고_409_예외다() {
    when(customerRepository.findByOrganizationIdAndId(ORGANIZATION_ID, CUSTOMER_ID))
        .thenReturn(Optional.of(customer()));
    when(eventRepository.existsForCustomer(ORGANIZATION_ID, CUSTOMER_ID)).thenReturn(true);

    assertThatThrownBy(() -> customerService.delete(ORGANIZATION_ID, CUSTOMER_ID))
        .isInstanceOf(ConflictException.class)
        .extracting(exception -> ((BusinessException) exception).getErrorCode())
        .isEqualTo(ErrorCode.CUSTOMER_HAS_EVENTS);

    verify(customerRepository, never()).delete(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void 결제_기록이_있으면_지우지_않고_409_예외다() {
    when(customerRepository.findByOrganizationIdAndId(ORGANIZATION_ID, CUSTOMER_ID))
        .thenReturn(Optional.of(customer()));
    when(paymentAttemptRepository.existsByOrganizationIdAndCustomerId(ORGANIZATION_ID, CUSTOMER_ID))
        .thenReturn(true);

    assertThatThrownBy(() -> customerService.delete(ORGANIZATION_ID, CUSTOMER_ID))
        .isInstanceOf(ConflictException.class)
        .extracting(exception -> ((BusinessException) exception).getErrorCode())
        .isEqualTo(ErrorCode.CUSTOMER_HAS_PAYMENT_ATTEMPTS);

    verify(customerRepository, never()).delete(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void 이벤트와_결제_기록이_둘_다_있으면_customer_has_events가_먼저다() {
    when(customerRepository.findByOrganizationIdAndId(ORGANIZATION_ID, CUSTOMER_ID))
        .thenReturn(Optional.of(customer()));
    when(eventRepository.existsForCustomer(ORGANIZATION_ID, CUSTOMER_ID)).thenReturn(true);
    lenient()
        .when(
            paymentAttemptRepository.existsByOrganizationIdAndCustomerId(
                ORGANIZATION_ID, CUSTOMER_ID))
        .thenReturn(true);

    assertThatThrownBy(() -> customerService.delete(ORGANIZATION_ID, CUSTOMER_ID))
        .extracting(exception -> ((BusinessException) exception).getErrorCode())
        .isEqualTo(ErrorCode.CUSTOMER_HAS_EVENTS);
  }

  @Test
  void 삭제_확인_뒤에_난_DataIntegrityViolationException은_바꾸지_않고_그대로_올려_보낸다() {
    when(customerRepository.findByOrganizationIdAndId(ORGANIZATION_ID, CUSTOMER_ID))
        .thenReturn(Optional.of(customer()));
    when(eventRepository.existsForCustomer(ORGANIZATION_ID, CUSTOMER_ID)).thenReturn(false);
    DataIntegrityViolationException violation =
        new DataIntegrityViolationException("event_customer_same_organization_fk");
    doThrow(violation).when(customerRepository).flush();

    assertThatThrownBy(() -> customerService.delete(ORGANIZATION_ID, CUSTOMER_ID))
        .isSameAs(violation);
  }

  @Test
  void 없는_고객을_지우면_404_예외다() {
    when(customerRepository.findByOrganizationIdAndId(ORGANIZATION_ID, CUSTOMER_ID))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> customerService.delete(ORGANIZATION_ID, CUSTOMER_ID))
        .isInstanceOf(NotFoundException.class)
        .extracting(exception -> ((BusinessException) exception).getErrorCode())
        .isEqualTo(ErrorCode.CUSTOMER_NOT_FOUND);

    verify(eventRepository, never()).existsForCustomer(ORGANIZATION_ID, CUSTOMER_ID);
  }

  private Customer customer() {
    return new Customer(CUSTOMER_ID, ORGANIZATION_ID, "아크메");
  }
}
