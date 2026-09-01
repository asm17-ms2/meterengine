package com.meterengine.customer.service;

import com.meterengine.customer.entity.Customer;
import com.meterengine.customer.exception.CustomerHasEventsException;
import com.meterengine.customer.exception.CustomerHasInvoicesException;
import com.meterengine.customer.exception.CustomerNotFoundException;
import com.meterengine.customer.repository.CustomerRepository;
import com.meterengine.event.repository.EventRepository;
import com.meterengine.invoice.repository.InvoiceExistenceRepository;
import java.util.List;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {

  private static final String INVOICE_CUSTOMER_CONSTRAINT = "invoice_customer_same_org";

  private final CustomerRepository customers;
  private final EventRepository events;
  private final InvoiceExistenceRepository invoices;

  CustomerService(
      CustomerRepository customers, EventRepository events, InvoiceExistenceRepository invoices) {
    this.customers = customers;
    this.events = events;
    this.invoices = invoices;
  }

  @Transactional(readOnly = true)
  public List<Customer> list(UUID organizationId) {
    return customers.findByOrganizationIdOrderByNameAscIdAsc(organizationId);
  }

  @Transactional
  public Customer create(UUID organizationId, String name) {
    return customers.saveAndFlush(new Customer(UUID.randomUUID(), organizationId, name));
  }

  @Transactional
  public Customer rename(UUID organizationId, UUID customerId, String name) {
    Customer customer =
        customers
            .findByOrganizationIdAndId(organizationId, customerId)
            .orElseThrow(() -> new CustomerNotFoundException(organizationId, customerId));
    customer.rename(name);
    return customer;
  }

  @Transactional
  public void delete(UUID organizationId, UUID customerId) {
    Customer customer =
        customers
            .findByOrganizationIdAndId(organizationId, customerId)
            .orElseThrow(() -> new CustomerNotFoundException(organizationId, customerId));

    if (events.existsForCustomer(organizationId, customerId)) {
      throw new CustomerHasEventsException(customerId);
    }
    if (invoices.existsForCustomer(organizationId, customerId)) {
      throw new CustomerHasInvoicesException(customerId);
    }

    try {
      customers.delete(customer);
      customers.flush();
    } catch (DataIntegrityViolationException exception) {
      if (exception.getCause() instanceof ConstraintViolationException cause
          && INVOICE_CUSTOMER_CONSTRAINT.equals(cause.getConstraintName())) {
        throw new CustomerHasInvoicesException(customerId);
      }
      throw new CustomerHasEventsException(customerId);
    }
  }
}
