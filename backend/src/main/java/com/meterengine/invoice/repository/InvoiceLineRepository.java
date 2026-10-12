package com.meterengine.invoice.repository;

import com.meterengine.invoice.entity.InvoiceLine;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceLineRepository extends JpaRepository<InvoiceLine, UUID> {

  List<InvoiceLine> findByOrganizationIdAndInvoiceIdOrderByBillableMetricCodeAsc(
      UUID organizationId, UUID invoiceId);

  List<InvoiceLine> findByOrganizationIdAndInvoiceIdInOrderByBillableMetricCodeAsc(
      UUID organizationId, Collection<UUID> invoiceIds);
}
