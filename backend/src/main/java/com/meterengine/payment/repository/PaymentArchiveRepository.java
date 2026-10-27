package com.meterengine.payment.repository;

import com.meterengine.global.crypto.EncryptedStringConverter;
import com.meterengine.payment.dto.PaymentMethodResponse;
import com.meterengine.payment.dto.TossPaymentsCredentialResponse;
import com.meterengine.payment.entity.TossPaymentsCredential;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentArchiveRepository {
  private final JdbcTemplate jdbcTemplate;
  private final EncryptedStringConverter encryptedStringConverter;

  PaymentArchiveRepository(
      JdbcTemplate jdbcTemplate, EncryptedStringConverter encryptedStringConverter) {
    this.jdbcTemplate = jdbcTemplate;
    this.encryptedStringConverter = encryptedStringConverter;
  }

  public void lock(UUID organizationId) {
    jdbcTemplate.queryForObject(
        "SELECT id FROM organization WHERE id = ? FOR UPDATE", UUID.class, organizationId);
  }

  public void credential(UUID organizationId, String mode) {
    jdbcTemplate.update(
        """
        INSERT INTO toss_payments_credential_history
          (organization_id, mode, client_key, secret_key, secret_key_last_four, verified_at, updated_at, archived_at)
        SELECT organization_id, mode, client_key, secret_key, secret_key_last_four, verified_at, updated_at,
          coalesce(archived_at, clock_timestamp()) FROM toss_payments_credential
        WHERE organization_id = ? AND mode = ?
        """,
        organizationId,
        mode);
  }

  public void method(UUID organizationId, UUID customerId, String mode) {
    jdbcTemplate.update(
        """
        INSERT INTO billing_key_history
          (organization_id, customer_id, mode, billing_key, card_issuer_code, card_number, authenticated_at,
           consent_document_version, consented_at, archived_at)
        SELECT organization_id, customer_id, mode, billing_key, card_issuer_code, card_number, authenticated_at,
          consent_document_version, consented_at, coalesce(archived_at, clock_timestamp()) FROM billing_key
        WHERE organization_id = ? AND customer_id = ? AND mode = ?
        """,
        organizationId,
        customerId,
        mode);
  }

  public List<PaymentMethodResponse> methods(UUID organizationId, String mode) {
    return jdbcTemplate.query(
        "SELECT * FROM billing_key_history WHERE organization_id = ? AND mode = ? ORDER BY retired_at DESC, history_id",
        (rs, n) ->
            new PaymentMethodResponse(
                rs.getObject("customer_id", UUID.class),
                rs.getString("card_issuer_code"),
                rs.getString("card_number"),
                rs.getString("mode"),
                rs.getObject("authenticated_at", OffsetDateTime.class),
                rs.getObject("archived_at", OffsetDateTime.class)),
        organizationId,
        mode);
  }

  public List<TossPaymentsCredentialResponse> credentials(UUID organizationId) {
    return jdbcTemplate.query(
        "SELECT * FROM toss_payments_credential_history WHERE organization_id = ? ORDER BY retired_at DESC, history_id",
        (rs, n) ->
            new TossPaymentsCredentialResponse(
                rs.getString("mode"),
                rs.getString("client_key"),
                rs.getString("secret_key_last_four"),
                rs.getObject("verified_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class),
                rs.getObject("archived_at", OffsetDateTime.class)),
        organizationId);
  }

  public Optional<TossPaymentsCredential> historical(
      UUID organizationId, String mode, OffsetDateTime requestedAt) {
    return jdbcTemplate
        .query(
            """
        SELECT * FROM toss_payments_credential_history WHERE organization_id = ? AND mode = ?
          AND updated_at <= ? AND retired_at > ? ORDER BY retired_at ASC LIMIT 1
        """,
            (rs, n) ->
                new TossPaymentsCredential(
                    organizationId,
                    mode,
                    rs.getString("client_key"),
                    encryptedStringConverter.convertToEntityAttribute(rs.getString("secret_key")),
                    rs.getObject("verified_at", OffsetDateTime.class)),
            organizationId,
            mode,
            requestedAt,
            requestedAt)
        .stream()
        .findFirst();
  }
}
