package com.meterengine.customer.service;

import com.meterengine.audit.service.AuditLogService;
import com.meterengine.customer.entity.Customer;
import com.meterengine.customer.repository.CustomerRepository;
import com.meterengine.global.error.ConflictException;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.NotFoundException;
import com.meterengine.global.validation.ArchiveStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {

  private final CustomerRepository customerRepository;
  private final AuditLogService auditLogService;

  CustomerService(CustomerRepository customerRepository, AuditLogService auditLogService) {
    this.customerRepository = customerRepository;
    this.auditLogService = auditLogService;
  }

  @Transactional(readOnly = true)
  public List<Customer> list(UUID organizationId) {
    return list(organizationId, "active");
  }

  @Transactional(readOnly = true)
  public List<Customer> list(UUID organizationId, String status) {
    ArchiveStatus filter = ArchiveStatus.parse(status);
    return customerRepository.findByOrganizationIdOrderByNameAscIdAsc(organizationId).stream()
        .filter(customer -> filter.includes(customer.getArchivedAt()))
        .toList();
  }

  @Transactional(readOnly = true)
  public Customer get(UUID organizationId, UUID id) {
    return customerRepository
        .findByOrganizationIdAndId(organizationId, id)
        .orElseThrow(() -> new NotFoundException(ErrorCode.CUSTOMER_NOT_FOUND));
  }

  @Transactional
  public Customer create(UUID organizationId, String name, String billingEmail) {
    Customer customer =
        customerRepository.saveAndFlush(
            new Customer(UUID.randomUUID(), organizationId, name, billingEmail));
    var fields = new ArrayList<>(List.of("name"));
    if (billingEmail != null) fields.add("billing_email");
    auditLogService.record(
        organizationId,
        "customer_created",
        "customer",
        customer.getId().toString(),
        Map.of("changed_fields", fields));
    return customer;
  }

  @Transactional
  public Customer update(UUID organizationId, UUID customerId, String name, String billingEmail) {
    Customer customer =
        customerRepository
            .lock(organizationId, customerId)
            .orElseThrow(() -> new NotFoundException(ErrorCode.CUSTOMER_NOT_FOUND));
    if (customer.getArchivedAt() != null) throw new ConflictException(ErrorCode.CUSTOMER_ARCHIVED);
    var fields = new ArrayList<String>();
    if (!Objects.equals(customer.getName(), name)) fields.add("name");
    if (!Objects.equals(customer.getBillingEmail(), billingEmail)) fields.add("billing_email");
    customer.update(name, billingEmail);
    if (!fields.isEmpty())
      auditLogService.record(
          organizationId,
          "customer_updated",
          "customer",
          customerId.toString(),
          Map.of("changed_fields", fields));
    return customer;
  }

  @Transactional
  public void delete(UUID organizationId, UUID customerId) {
    Customer customer =
        customerRepository
            .lock(organizationId, customerId)
            .orElseThrow(() -> new NotFoundException(ErrorCode.CUSTOMER_NOT_FOUND));

    if (customer.getArchivedAt() != null) return;
    customer.archive();
    auditLogService.record(
        organizationId, "customer_archived", "customer", customerId.toString(), Map.of());
  }

  @Transactional
  public Customer restore(UUID organizationId, UUID customerId) {
    Customer customer =
        customerRepository
            .lock(organizationId, customerId)
            .orElseThrow(() -> new NotFoundException(ErrorCode.CUSTOMER_NOT_FOUND));
    if (customer.getArchivedAt() != null) {
      customer.restore();
      auditLogService.record(
          organizationId, "customer_restored", "customer", customerId.toString(), Map.of());
    }
    return customer;
  }
}
