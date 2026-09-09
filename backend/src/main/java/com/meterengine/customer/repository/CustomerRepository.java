package com.meterengine.customer.repository;

import com.meterengine.customer.entity.Customer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

  boolean existsByOrganizationIdAndId(UUID organizationId, UUID id);

  List<Customer> findByOrganizationIdOrderByNameAscIdAsc(UUID organizationId);

  Optional<Customer> findByOrganizationIdAndId(UUID organizationId, UUID id);
}
