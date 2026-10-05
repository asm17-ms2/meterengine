package com.meterengine.invoice.repository;

import com.meterengine.invoice.dto.InvoiceWithCustomerName;
import com.meterengine.invoice.entity.Invoice;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

  @Query(
      """
      SELECT new com.meterengine.invoice.dto.InvoiceWithCustomerName(i, c.name)
      FROM Invoice i
      JOIN Customer c ON c.organizationId = i.organizationId AND c.id = i.customerId
      WHERE i.organizationId = :organizationId
        AND (:customerId IS NULL OR i.customerId = :customerId)
        AND (:period IS NULL OR i.period = :period)
      ORDER BY i.period DESC, c.name ASC, c.id ASC
      """)
  List<InvoiceWithCustomerName> findWithCustomerName(
      UUID organizationId, UUID customerId, String period);
}
