package com.meterengine.invoice.repository;

import com.meterengine.invoice.entity.InvoiceLine;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface InvoiceLineRepository extends JpaRepository<InvoiceLine, UUID> {

  @Query(
      "SELECT line FROM InvoiceLine line WHERE line.organizationId = :organizationId AND"
          + " line.environmentId IS NULL AND line.invoiceId = :invoiceId ORDER BY"
          + " line.billableMetricCode, line.dimensionValues, line.lineType, line.tierIndex")
  List<InvoiceLine> findByOrganizationIdAndInvoiceIdOrderByBillableMetricCodeAscDimensionValuesAsc(
      UUID organizationId, UUID invoiceId);

  @Query(
      "SELECT line FROM InvoiceLine line WHERE line.organizationId = :organizationId AND"
          + " line.environmentId IS NULL AND line.invoiceId IN :invoiceIds ORDER BY"
          + " line.billableMetricCode, line.dimensionValues, line.lineType, line.tierIndex")
  List<InvoiceLine>
      findByOrganizationIdAndInvoiceIdInOrderByBillableMetricCodeAscDimensionValuesAsc(
          UUID organizationId, Collection<UUID> invoiceIds);

  @Query(
      value =
          "SELECT * FROM invoice_line WHERE organization_id = :organizationId AND environment_id IS"
              + " NOT DISTINCT FROM CAST(:environmentId AS UUID) AND invoice_id = :invoiceId ORDER"
              + " BY billable_metric_code, dimension_values, line_type, tier_index",
      nativeQuery = true)
  List<InvoiceLine> listScoped(UUID organizationId, UUID environmentId, UUID invoiceId);

  @Query(
      value =
          "SELECT * FROM invoice_line WHERE organization_id = :organizationId AND environment_id IS"
              + " NOT DISTINCT FROM CAST(:environmentId AS UUID) AND invoice_id IN :invoiceIds"
              + " ORDER BY billable_metric_code, dimension_values, line_type, tier_index",
      nativeQuery = true)
  List<InvoiceLine> listScoped(
      UUID organizationId, UUID environmentId, Collection<UUID> invoiceIds);
}
