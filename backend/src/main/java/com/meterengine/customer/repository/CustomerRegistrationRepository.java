package com.meterengine.customer.repository;

import com.meterengine.global.security.BusinessEnvironmentContext;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CustomerRegistrationRepository {
  private final JdbcTemplate jdbcTemplate;

  CustomerRegistrationRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public boolean insertIfAbsent(
      UUID id, UUID organizationId, String name, String billingEmail, String externalId) {
    List<UUID> inserted =
        jdbcTemplate.query(
            """
        INSERT INTO customer (id, organization_id, name, billing_email, external_id, environment_id)
        VALUES (?, ?, ?, ?, ?, ?)
        ON CONFLICT (organization_id, environment_scope_id, external_id) WHERE external_id IS NOT NULL DO NOTHING
        RETURNING id
        """,
            (rs, rowNum) -> rs.getObject("id", UUID.class),
            id,
            organizationId,
            name,
            billingEmail,
            externalId,
            BusinessEnvironmentContext.environmentId());
    return !inserted.isEmpty();
  }
}
