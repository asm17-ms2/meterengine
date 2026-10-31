package com.meterengine.customer.repository;

import com.meterengine.customer.entity.Customer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

  @Query(
      value =
          "SELECT EXISTS(SELECT 1 FROM customer WHERE organization_id = :organizationId AND id = :id)",
      nativeQuery = true)
  boolean existsByOrganizationIdAndId(UUID organizationId, UUID id);

  @Query(
      value =
          "SELECT * FROM customer WHERE organization_id = :organizationId AND id IN :ids ORDER BY id FOR SHARE",
      nativeQuery = true)
  List<Customer> lock(UUID organizationId, java.util.Collection<UUID> ids);

  @Query(
      value = "SELECT * FROM customer WHERE organization_id = :organizationId ORDER BY name, id",
      nativeQuery = true)
  List<Customer> findByOrganizationIdOrderByNameAscIdAsc(UUID organizationId);

  @Query(
      value = "SELECT * FROM customer WHERE organization_id = :organizationId AND id = :id",
      nativeQuery = true)
  Optional<Customer> findByOrganizationIdAndId(UUID organizationId, UUID id);

  @Query(
      value =
          "SELECT * FROM customer WHERE organization_id = :organizationId AND id = :id FOR UPDATE",
      nativeQuery = true)
  Optional<Customer> lock(UUID organizationId, UUID id);

  @Query(
      value =
          "SELECT * FROM customer WHERE organization_id = :organizationId AND external_id = :externalId ORDER BY name, id",
      nativeQuery = true)
  List<Customer> findByOrganizationIdAndExternalIdOrderByNameAscIdAsc(
      UUID organizationId, String externalId);
}
