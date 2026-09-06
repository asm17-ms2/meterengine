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

@Service
public class CustomerService {

  private final CustomerRepository customerRepository;
  private final EventRepository eventRepository;

  CustomerService(CustomerRepository customerRepository, EventRepository eventRepository) {
    this.customerRepository = customerRepository;
    this.eventRepository = eventRepository;
  }

  @Transactional(readOnly = true)
  public List<Customer> list(UUID organizationId) {
    return customerRepository.findByOrganizationIdOrderByNameAscIdAsc(organizationId);
  }

  @Transactional
  public Customer create(UUID organizationId, String name) {
    return customerRepository.saveAndFlush(new Customer(UUID.randomUUID(), organizationId, name));
  }

  @Transactional
  public Customer update(UUID organizationId, UUID customerId, String name) {
    Customer customer =
        customerRepository
            .findByOrganizationIdAndId(organizationId, customerId)
            .orElseThrow(() -> new CustomerNotFoundException(organizationId, customerId));
    customer.rename(name);
    return customer;
  }

  @Transactional
  public void delete(UUID organizationId, UUID customerId) {
    Customer customer =
        customerRepository
            .findByOrganizationIdAndId(organizationId, customerId)
            .orElseThrow(() -> new CustomerNotFoundException(organizationId, customerId));

    if (eventRepository.existsForCustomer(organizationId, customerId)) {
      throw new CustomerHasEventsException(customerId);
    }

    try {
      customerRepository.delete(customer);
      customerRepository.flush();
    } catch (DataIntegrityViolationException exception) {
      throw new CustomerHasEventsException(customerId);
    }
  }
}
