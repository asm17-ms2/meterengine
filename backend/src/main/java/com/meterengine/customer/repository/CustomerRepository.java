package com.meterengine.customer.repository;

import com.meterengine.customer.entity.Customer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

  boolean existsByOrganizationIdAndId(UUID organizationId, UUID id);

  @Query(
      value =
          "SELECT * FROM customer WHERE organization_id = :organizationId AND id IN :ids ORDER BY id FOR SHARE",
      nativeQuery = true)
  List<Customer> lock(UUID organizationId, java.util.Collection<UUID> ids);

  List<Customer> findByOrganizationIdOrderByNameAscIdAsc(UUID organizationId);

  Optional<Customer> findByOrganizationIdAndId(UUID organizationId, UUID id);

  @Query(
      value =
          "SELECT * FROM customer WHERE organization_id = :organizationId AND id = :id FOR UPDATE",
      nativeQuery = true)
  Optional<Customer> lock(UUID organizationId, UUID id);

  List<Customer> findByOrganizationIdAndExternalIdOrderByNameAscIdAsc(
      UUID organizationId, String externalId);

  List<Customer> findByOrganizationIdAndIdIn(UUID organizationId, java.util.Collection<UUID> ids);

  @Query(
      value =
          "SELECT EXISTS(SELECT 1 FROM price_rate WHERE organization_id = :organizationId AND customer_id = :id) OR EXISTS(SELECT 1 FROM price_plan WHERE organization_id = :organizationId AND customer_id = :id)",
      nativeQuery = true)
  boolean existsWithPricing(UUID organizationId, UUID id);
}
