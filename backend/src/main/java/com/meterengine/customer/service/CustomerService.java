package com.meterengine.customer.service;

import com.meterengine.audit.service.AuditLogService;
import com.meterengine.customer.entity.Customer;
import com.meterengine.customer.repository.CustomerRepository;
import com.meterengine.global.error.ConflictException;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.NotFoundException;
import com.meterengine.global.security.BusinessEnvironmentContext;
import com.meterengine.global.validation.ArchiveStatus;
import com.meterengine.global.validation.VersionPrecondition;
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
    var customers =
        BusinessEnvironmentContext.environmentId() == null
            ? customerRepository.findByOrganizationIdOrderByNameAscIdAsc(organizationId)
            : customerRepository.listScoped(
                organizationId, BusinessEnvironmentContext.environmentId());
    return customers.stream()
        .filter(customer -> filter.includes(customer.getArchivedAt()))
        .toList();
  }

  @Transactional(readOnly = true)
  public Customer get(UUID organizationId, UUID id) {
    return (BusinessEnvironmentContext.environmentId() == null
            ? customerRepository.findByOrganizationIdAndId(organizationId, id)
            : customerRepository.findScoped(
                organizationId, BusinessEnvironmentContext.environmentId(), id))
        .orElseThrow(() -> new NotFoundException(ErrorCode.CUSTOMER_NOT_FOUND));
  }

  @Transactional
  public Customer create(UUID organizationId, String name, String billingEmail) {
    Customer customer =
        customerRepository.saveAndFlush(
            new Customer(
                UUID.randomUUID(),
                organizationId,
                name,
                billingEmail,
                BusinessEnvironmentContext.environmentId()));
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
    return update(organizationId, customerId, name, billingEmail, null);
  }

  @Transactional
  public Customer update(
      UUID organizationId,
      UUID customerId,
      String name,
      String billingEmail,
      Long expectedVersion) {
    Customer customer = lock(organizationId, customerId);
    VersionPrecondition.requireMatch(expectedVersion, customer.getVersion());
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
    customerRepository.flush();
    return customer;
  }

  @Transactional
  public void delete(UUID organizationId, UUID customerId) {
    Customer customer = lock(organizationId, customerId);

    if (customer.getArchivedAt() != null) return;
    customer.archive();
    auditLogService.record(
        organizationId, "customer_archived", "customer", customerId.toString(), Map.of());
  }

  @Transactional
  public Customer restore(UUID organizationId, UUID customerId) {
    Customer customer = lock(organizationId, customerId);
    if (customer.getArchivedAt() != null) {
      customer.restore();
      auditLogService.record(
          organizationId, "customer_restored", "customer", customerId.toString(), Map.of());
    }
    return customer;
  }

  private Customer lock(UUID organizationId, UUID customerId) {
    return (BusinessEnvironmentContext.environmentId() == null
            ? customerRepository.lock(organizationId, customerId)
            : customerRepository.lockScoped(
                organizationId, BusinessEnvironmentContext.environmentId(), customerId))
        .orElseThrow(() -> new NotFoundException(ErrorCode.CUSTOMER_NOT_FOUND));
  }
}
