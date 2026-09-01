package com.meterengine.customer.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meterengine.customer.entity.Customer;
import com.meterengine.customer.exception.CustomerHasEventsException;
import com.meterengine.customer.exception.CustomerHasInvoicesException;
import com.meterengine.customer.exception.CustomerNotFoundException;
import com.meterengine.customer.repository.CustomerRepository;
import com.meterengine.event.repository.EventRepository;
import com.meterengine.invoice.repository.InvoiceExistenceRepository;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

  private static final UUID ORG_ID = UUID.randomUUID();
  private static final UUID CUSTOMER_ID = UUID.randomUUID();

  @Mock private CustomerRepository customers;
  @Mock private EventRepository events;
  @Mock private InvoiceExistenceRepository invoices;

  private CustomerService customerService;

  @BeforeEach
  void setUp() {
    customerService = new CustomerService(customers, events, invoices);
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
  void 확인_뒤에_인보이스_FK가_거절하면_인보이스_예외로_바뀐다() {
    when(customers.findByOrganizationIdAndId(ORG_ID, CUSTOMER_ID))
        .thenReturn(Optional.of(customer()));
    when(events.existsForCustomer(ORG_ID, CUSTOMER_ID)).thenReturn(false);
    when(invoices.existsForCustomer(ORG_ID, CUSTOMER_ID)).thenReturn(false);
    doThrow(
            new DataIntegrityViolationException(
                "could not execute statement",
                new ConstraintViolationException(
                    "could not execute statement",
                    new SQLException(),
                    "invoice_customer_same_org")))
        .when(customers)
        .flush();

    assertThatThrownBy(() -> customerService.delete(ORG_ID, CUSTOMER_ID))
        .isInstanceOf(CustomerHasInvoicesException.class);
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
