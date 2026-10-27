package com.meterengine.customer.service;

import com.meterengine.customer.api.CustomerDirectory;
import com.meterengine.customer.entity.Customer;
import com.meterengine.customer.repository.CustomerRepository;
import com.meterengine.global.error.ConflictException;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.NotFoundException;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class CustomerDirectoryService implements CustomerDirectory {
  private final CustomerRepository customerRepository;

  CustomerDirectoryService(CustomerRepository customerRepository) {
    this.customerRepository = customerRepository;
  }

  public boolean exists(UUID organizationId, UUID customerId) {
    return (UUID) null == null
        ? customerRepository.existsByOrganizationIdAndId(organizationId, customerId)
        : customerRepository.findScoped(organizationId, (UUID) null, customerId).isPresent();
  }

  public void check(UUID organizationId, UUID customerId) {
    Boolean active = active(organizationId, java.util.List.of(customerId)).get(customerId);
    if (active == null) throw new NotFoundException(ErrorCode.CUSTOMER_NOT_FOUND);
    if (!active) throw new ConflictException(ErrorCode.CUSTOMER_ARCHIVED);
  }

  public Map<UUID, Boolean> active(UUID organizationId, Collection<UUID> customerIds) {
    if (customerIds.isEmpty()) return Map.of();
    var customers =
        (UUID) null == null
            ? customerRepository.lock(organizationId, customerIds)
            : customerRepository.lockScoped(organizationId, (UUID) null, customerIds);
    return customers.stream()
        .collect(Collectors.toMap(Customer::getId, customer -> customer.getArchivedAt() == null));
  }
}
