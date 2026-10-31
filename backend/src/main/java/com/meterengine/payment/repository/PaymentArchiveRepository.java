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
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentArchiveRepository {
  private static final RowMapper<PaymentMethodResponse> PAYMENT_METHOD_HISTORY =
      (rs, n) ->
          PaymentMethodResponse.of(
              rs.getObject("customer_id", UUID.class),
              rs.getString("card_issuer_code"),
              rs.getString("card_number"),
              rs.getString("mode"),
              rs.getObject("authenticated_at", OffsetDateTime.class),
              rs.getObject("archived_at", OffsetDateTime.class));

  private final JdbcTemplate jdbcTemplate;
  private final EncryptedStringConverter encryptedStringConverter;

  PaymentArchiveRepository(
      JdbcTemplate jdbcTemplate, EncryptedStringConverter encryptedStringConverter) {
    this.jdbcTemplate = jdbcTemplate;
    this.encryptedStringConverter = encryptedStringConverter;
  }

  public void lock(UUID organizationId) {
    jdbcTemplate.queryForObject(
        "SELECT id FROM shared.lock_organization_no_key_update(?)", UUID.class, organizationId);
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
           consent_document_version, consented_at, credential_updated_at, archived_at)
        SELECT organization_id, customer_id, mode, billing_key, card_issuer_code, card_number, authenticated_at,
          consent_document_version, consented_at, credential_updated_at, coalesce(archived_at, clock_timestamp()) FROM billing_key
        WHERE organization_id = ? AND customer_id = ? AND mode = ?
        """,
        organizationId,
        customerId,
        mode);
  }

  public List<PaymentMethodResponse> methods(UUID organizationId, String mode) {
    return jdbcTemplate.query(
        "SELECT * FROM billing_key_history WHERE organization_id = ? AND mode = ? ORDER BY retired_at DESC, history_id",
        PAYMENT_METHOD_HISTORY,
        organizationId,
        mode);
  }

  public List<PaymentMethodResponse> methods(UUID organizationId, String mode, UUID customerId) {
    return jdbcTemplate.query(
        "SELECT * FROM billing_key_history WHERE organization_id = ? AND mode = ? AND"
            + " customer_id = ?"
            + " ORDER BY retired_at DESC, history_id",
        PAYMENT_METHOD_HISTORY,
        organizationId,
        mode,
        customerId);
  }

  public List<TossPaymentsCredentialResponse> credentials(UUID organizationId) {
    return jdbcTemplate.query(
        "SELECT * FROM toss_payments_credential_history WHERE organization_id = ? ORDER BY retired_at DESC, history_id",
        (rs, n) ->
            TossPaymentsCredentialResponse.of(
                rs.getString("mode"),
                rs.getString("client_key"),
                rs.getString("secret_key_last_four"),
                rs.getObject("verified_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class),
                rs.getObject("archived_at", OffsetDateTime.class)),
        organizationId);
  }

  // 교체로 물러난 키 가운데 since 뒤에 그 키로 고정한 결제나 취소가 있거나 진행 중인 버전만 고른다.
  public List<TossPaymentsCredential> credentialVersionsInUse(
      UUID organizationId, String mode, OffsetDateTime since) {
    return jdbcTemplate.query(
        "SELECT h.* FROM toss_payments_credential_history h WHERE h.organization_id = ? AND"
            + " h.mode = ? AND EXISTS (SELECT 1 FROM"
            + " payment_attempt a WHERE a.organization_id = h.organization_id AND a.mode = h.mode"
            + " AND"
            + " a.credential_updated_at = h.updated_at AND (a.status = 'pending' OR a.requested_at"
            + " >= ? OR a.completed_at >= ? OR EXISTS (SELECT 1 FROM payment_cancellation c WHERE"
            + " c.payment_attempt_id = a.id AND (c.status = 'pending' OR c.completed_at >= ?))))"
            + " ORDER BY h.updated_at",
        (rs, n) ->
            new TossPaymentsCredential(
                organizationId,
                mode,
                rs.getString("client_key"),
                encryptedStringConverter.convertToEntityAttribute(rs.getString("secret_key")),
                rs.getObject("verified_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class)),
        organizationId,
        mode,
        since,
        since,
        since);
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
                    rs.getObject("verified_at", OffsetDateTime.class),
                    rs.getObject("updated_at", OffsetDateTime.class)),
            organizationId,
            mode,
            requestedAt,
            requestedAt)
        .stream()
        .findFirst();
  }
}
