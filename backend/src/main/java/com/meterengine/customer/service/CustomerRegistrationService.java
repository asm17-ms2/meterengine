package com.meterengine.customer.service;

import com.meterengine.audit.service.AuditLogService;
import com.meterengine.customer.dto.CreateCustomerRequest;
import com.meterengine.customer.dto.CustomerRegistration;
import com.meterengine.customer.entity.Customer;
import com.meterengine.customer.repository.CustomerRegistrationRepository;
import com.meterengine.customer.repository.CustomerRepository;
import com.meterengine.global.security.BusinessEnvironmentContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerRegistrationService {
  private final CustomerRegistrationRepository customerRegistrationRepository;
  private final CustomerRepository customerRepository;
  private final CustomerService customerService;
  private final AuditLogService auditLogService;

  CustomerRegistrationService(
      CustomerRegistrationRepository customerRegistrationRepository,
      CustomerRepository customerRepository,
      CustomerService customerService,
      AuditLogService auditLogService) {
    this.customerRegistrationRepository = customerRegistrationRepository;
    this.customerRepository = customerRepository;
    this.customerService = customerService;
    this.auditLogService = auditLogService;
  }

  @Transactional
  public CustomerRegistration create(UUID organizationId, CreateCustomerRequest request) {
    if (request.externalId() == null) {
      return new CustomerRegistration(
          customerService.create(organizationId, request.name(), request.billingEmail()), true);
    }
    UUID id = UUID.randomUUID();
    boolean created =
        customerRegistrationRepository.insertIfAbsent(
            id, organizationId, request.name(), request.billingEmail(), request.externalId());
    Customer customer =
        created
            ? customerRepository.findById(id).orElseThrow()
            : externalCustomers(organizationId, request.externalId()).getFirst();
    if (customer.getArchivedAt() != null)
      throw new com.meterengine.global.error.ConflictException(
          com.meterengine.global.error.ErrorCode.CUSTOMER_ARCHIVED);
    if (created) {
      var fields = new ArrayList<>(List.of("name", "external_id"));
      if (request.billingEmail() != null) fields.add("billing_email");
      auditLogService.record(
          organizationId,
          "customer_created",
          "customer",
          customer.getId().toString(),
          Map.of("changed_fields", fields));
    }
    return new CustomerRegistration(customer, created);
  }

  @Transactional(readOnly = true)
  public List<Customer> list(UUID organizationId, String externalId) {
    return list(organizationId, externalId, "active");
  }

  @Transactional(readOnly = true)
  public List<Customer> list(UUID organizationId, String externalId, String status) {
    var filter = com.meterengine.global.validation.ArchiveStatus.parse(status);
    return externalId == null
        ? customerService.list(organizationId, status)
        : externalCustomers(organizationId, externalId).stream()
            .filter(customer -> filter.includes(customer.getArchivedAt()))
            .toList();
  }

  private List<Customer> externalCustomers(UUID organizationId, String externalId) {
    return BusinessEnvironmentContext.environmentId() == null
        ? customerRepository.findByOrganizationIdAndExternalIdOrderByNameAscIdAsc(
            organizationId, externalId)
        : customerRepository.listExternalScoped(
            organizationId, BusinessEnvironmentContext.environmentId(), externalId);
  }
}
