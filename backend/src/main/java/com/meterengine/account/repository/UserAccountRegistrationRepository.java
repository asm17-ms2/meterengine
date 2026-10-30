package com.meterengine.account.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserAccountRegistrationRepository {
  private final JdbcTemplate jdbcTemplate;

  UserAccountRegistrationRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public boolean insertIfAbsent(
      UUID id, String email, String passwordHash, String name, OffsetDateTime createdAt) {
    List<UUID> inserted =
        jdbcTemplate.query(
            """
            INSERT INTO user_account
              (id, email, password_hash, name, role, created_at, consent_required)
            VALUES (?, ?, ?, ?, 'operator', ?, TRUE)
            ON CONFLICT (lower(email)) DO NOTHING
            RETURNING id
            """,
            (resultSet, rowNumber) -> resultSet.getObject("id", UUID.class),
            id,
            email,
            passwordHash,
            name,
            createdAt);
    return !inserted.isEmpty();
  }
}
