package com.meterengine.invoiceview.repository;

import com.meterengine.invoiceview.entity.InvoiceViewLink;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface InvoiceViewLinkRepository extends JpaRepository<InvoiceViewLink, UUID> {
  List<InvoiceViewLink> findByOrganizationIdAndInvoiceIdOrderByCreatedAtDescIdDesc(
      UUID organizationId, UUID invoiceId);

  Optional<InvoiceViewLink> findByTokenHash(byte[] tokenHash);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT l FROM InvoiceViewLink l WHERE l.tokenHash=:tokenHash")
  Optional<InvoiceViewLink> findForUpdateByTokenHash(byte[] tokenHash);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "SELECT l FROM InvoiceViewLink l WHERE l.organizationId=:organizationId"
          + " AND l.invoiceId=:invoiceId AND l.id=:id")
  Optional<InvoiceViewLink> findForUpdateByOrganizationIdAndInvoiceIdAndId(
      UUID organizationId, UUID invoiceId, UUID id);
}
