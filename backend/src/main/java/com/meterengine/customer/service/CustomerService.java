package com.meterengine.customer.service;

import com.meterengine.customer.entity.Customer;
import com.meterengine.customer.repository.CustomerRepository;
import com.meterengine.event.repository.EventRepository;
import com.meterengine.global.error.ConflictException;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.NotFoundException;
import com.meterengine.payment.repository.PaymentAttemptRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {

  private final CustomerRepository customerRepository;
  private final EventRepository eventRepository;
  private final PaymentAttemptRepository paymentAttemptRepository;

  CustomerService(
      CustomerRepository customerRepository,
      EventRepository eventRepository,
      PaymentAttemptRepository paymentAttemptRepository) {
    this.customerRepository = customerRepository;
    this.eventRepository = eventRepository;
    this.paymentAttemptRepository = paymentAttemptRepository;
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
            .orElseThrow(() -> new NotFoundException(ErrorCode.CUSTOMER_NOT_FOUND));
    customer.rename(name);
    return customer;
  }

  @Transactional
  public void delete(UUID organizationId, UUID customerId) {
    Customer customer =
        customerRepository
            .findByOrganizationIdAndId(organizationId, customerId)
            .orElseThrow(() -> new NotFoundException(ErrorCode.CUSTOMER_NOT_FOUND));

    if (eventRepository.existsForCustomer(organizationId, customerId)) {
      throw new ConflictException(ErrorCode.CUSTOMER_HAS_EVENTS);
    }
    if (paymentAttemptRepository.existsByOrganizationIdAndCustomerId(organizationId, customerId)) {
      throw new ConflictException(ErrorCode.CUSTOMER_HAS_PAYMENT_ATTEMPTS);
    }

    customerRepository.delete(customer);
    customerRepository.flush();
  }
}
