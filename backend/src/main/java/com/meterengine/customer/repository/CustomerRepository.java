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
          "SELECT EXISTS(SELECT 1 FROM customer WHERE organization_id = :organizationId AND id = :id AND environment_id IS NULL)",
      nativeQuery = true)
  boolean existsByOrganizationIdAndId(UUID organizationId, UUID id);

  @Query(
      value =
          "SELECT * FROM customer WHERE organization_id = :organizationId AND environment_id IS NULL AND id IN :ids ORDER BY id FOR SHARE",
      nativeQuery = true)
  List<Customer> lock(UUID organizationId, java.util.Collection<UUID> ids);

  @Query(
      value =
          "SELECT * FROM customer WHERE organization_id = :organizationId AND environment_id IS NULL ORDER BY name, id",
      nativeQuery = true)
  List<Customer> findByOrganizationIdOrderByNameAscIdAsc(UUID organizationId);

  @Query(
      value =
          "SELECT * FROM customer WHERE organization_id = :organizationId AND environment_id IS NULL AND id = :id",
      nativeQuery = true)
  Optional<Customer> findByOrganizationIdAndId(UUID organizationId, UUID id);

  @Query(
      value =
          "SELECT * FROM customer WHERE organization_id = :organizationId AND environment_id IS NULL AND id = :id FOR UPDATE",
      nativeQuery = true)
  Optional<Customer> lock(UUID organizationId, UUID id);

  @Query(
      value =
          "SELECT * FROM customer WHERE organization_id = :organizationId AND environment_id IS NULL AND external_id = :externalId ORDER BY name, id",
      nativeQuery = true)
  List<Customer> findByOrganizationIdAndExternalIdOrderByNameAscIdAsc(
      UUID organizationId, String externalId);

  @Query(
      value =
          "SELECT * FROM customer WHERE organization_id = :organizationId AND environment_id IS NULL AND id IN :ids",
      nativeQuery = true)
  List<Customer> findByOrganizationIdAndIdIn(UUID organizationId, java.util.Collection<UUID> ids);

  @Query(
      value =
          "SELECT * FROM customer WHERE organization_id = :organizationId AND environment_id IS NOT DISTINCT FROM CAST(:environmentId AS UUID) ORDER BY name, id",
      nativeQuery = true)
  List<Customer> listScoped(UUID organizationId, UUID environmentId);

  @Query(
      value =
          "SELECT * FROM customer WHERE organization_id = :organizationId AND environment_id IS NOT DISTINCT FROM CAST(:environmentId AS UUID) AND id = :id",
      nativeQuery = true)
  Optional<Customer> findScoped(UUID organizationId, UUID environmentId, UUID id);

  @Query(
      value =
          "SELECT * FROM customer WHERE organization_id = :organizationId AND environment_id IS NOT DISTINCT FROM CAST(:environmentId AS UUID) AND external_id = :externalId ORDER BY name, id",
      nativeQuery = true)
  List<Customer> listExternalScoped(UUID organizationId, UUID environmentId, String externalId);

  @Query(
      value =
          "SELECT * FROM customer WHERE organization_id = :organizationId AND environment_id IS NOT DISTINCT FROM CAST(:environmentId AS UUID) AND id = :id FOR UPDATE",
      nativeQuery = true)
  Optional<Customer> lockScoped(UUID organizationId, UUID environmentId, UUID id);

  @Query(
      value =
          "SELECT * FROM customer WHERE organization_id = :organizationId AND environment_id IS NOT DISTINCT FROM CAST(:environmentId AS UUID) AND id IN :ids ORDER BY id FOR SHARE",
      nativeQuery = true)
  List<Customer> lockScoped(
      UUID organizationId, UUID environmentId, java.util.Collection<UUID> ids);

  @Query(
      value =
          "SELECT EXISTS(SELECT 1 FROM price_rate WHERE organization_id = :organizationId AND customer_id = :id) OR EXISTS(SELECT 1 FROM price_plan WHERE organization_id = :organizationId AND customer_id = :id)",
      nativeQuery = true)
  boolean existsWithPricing(UUID organizationId, UUID id);
}
