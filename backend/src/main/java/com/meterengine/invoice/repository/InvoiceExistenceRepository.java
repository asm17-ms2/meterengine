package com.meterengine.invoice.repository;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class InvoiceExistenceRepository {

  private final JdbcTemplate jdbc;

  InvoiceExistenceRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public boolean existsForCustomer(UUID organizationId, UUID customerId) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            """
            SELECT EXISTS(
              SELECT 1 FROM invoice
              WHERE organization_id = ? AND customer_id = ?
            )
            """,
            Boolean.class,
            organizationId,
            customerId));
  }
}
