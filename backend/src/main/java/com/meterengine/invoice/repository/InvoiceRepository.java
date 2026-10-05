package com.meterengine.invoice.repository;

import com.meterengine.invoice.entity.Invoice;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

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
}
