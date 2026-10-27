package com.meterengine.invoice.repository;

import com.meterengine.invoice.entity.Invoice;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

  @Query(
      """
      SELECT i.customerId
      FROM Invoice i
      WHERE i.organizationId = :organizationId
        AND i.environmentId IS NULL
        AND i.period = :period
      """)
  List<UUID> findCustomerIds(UUID organizationId, String period);

  @Query(
      """
      SELECT i
      FROM Invoice i
      WHERE i.organizationId = :organizationId
        AND i.environmentId IS NULL
        AND (:customerId IS NULL OR i.customerId = :customerId)
        AND (:period IS NULL OR i.period = :period)
      ORDER BY i.period DESC, i.customerName ASC, i.customerId ASC
      """)
  List<Invoice> findNewestFirst(UUID organizationId, UUID customerId, String period);

  @Query(
      "select i from Invoice i where i.organizationId=:organizationId and i.environmentId IS NULL"
          + " and i.id=:id")
  Optional<Invoice> findByOrganizationIdAndId(UUID organizationId, UUID id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select i from Invoice i where i.organizationId=:organizationId and i.environmentId IS NULL"
          + " and i.id=:id")
  Optional<Invoice> findForUpdate(UUID organizationId, UUID id);

  @Query(
      value =
          "SELECT customer_id FROM invoice WHERE organization_id = :organizationId AND"
              + " environment_id IS NOT DISTINCT FROM CAST(:environmentId AS UUID) AND period ="
              + " :period",
      nativeQuery = true)
  List<UUID> findCustomerIdsScoped(UUID organizationId, UUID environmentId, String period);

  @Query(
      value =
          "SELECT * FROM invoice WHERE organization_id = :organizationId AND environment_id IS NOT"
              + " DISTINCT FROM CAST(:environmentId AS UUID) AND (CAST(:customerId AS UUID) IS NULL"
              + " OR customer_id = :customerId) AND (CAST(:period AS VARCHAR) IS NULL OR period ="
              + " :period) ORDER BY period DESC, customer_name, customer_id",
      nativeQuery = true)
  List<Invoice> findNewestFirstScoped(
      UUID organizationId, UUID environmentId, UUID customerId, String period);

  @Query(
      value =
          "SELECT * FROM invoice WHERE organization_id = :organizationId AND environment_id IS NOT"
              + " DISTINCT FROM CAST(:environmentId AS UUID) AND id = :id",
      nativeQuery = true)
  Optional<Invoice> findScoped(UUID organizationId, UUID environmentId, UUID id);

  @Query(
      value =
          "SELECT * FROM invoice WHERE organization_id = :organizationId AND environment_id IS NOT"
              + " DISTINCT FROM CAST(:environmentId AS UUID) AND id = :id FOR UPDATE",
      nativeQuery = true)
  Optional<Invoice> lockScoped(UUID organizationId, UUID environmentId, UUID id);
}
