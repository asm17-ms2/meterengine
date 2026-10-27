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
        AND i.period = :period
      """)
  List<UUID> findCustomerIds(UUID organizationId, String period);

  @Query(
      """
      SELECT i
      FROM Invoice i
      WHERE i.organizationId = :organizationId
        AND (:customerId IS NULL OR i.customerId = :customerId)
        AND (:period IS NULL OR i.period = :period)
      ORDER BY i.period DESC, i.customerName ASC, i.customerId ASC
      """)
  List<Invoice> findNewestFirst(UUID organizationId, UUID customerId, String period);

  Optional<Invoice> findByOrganizationIdAndId(UUID organizationId, UUID id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select i from Invoice i where i.organizationId=:organizationId and i.id=:id")
  Optional<Invoice> findForUpdate(UUID organizationId, UUID id);
}
