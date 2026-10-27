package com.meterengine.idempotency.service;

import com.meterengine.global.error.ConflictException;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.ErrorResponse.FieldError;
import com.meterengine.global.error.InvalidRequestException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
public class IdempotencyService {
  private final JdbcTemplate jdbcTemplate;
  private final JsonMapper jsonMapper;

  IdempotencyService(JdbcTemplate jdbcTemplate, JsonMapper jsonMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.jsonMapper = jsonMapper;
  }

  @Transactional
  public <T> ResponseEntity<T> execute(
      UUID organizationId,
      String operation,
      String key,
      Object request,
      Class<T> responseType,
      Supplier<ResponseEntity<T>> action) {
    if (key == null) return action.get();
    if (key.isBlank() || key.length() > 255)
      throw new InvalidRequestException(
          ErrorCode.VALIDATION_ERROR,
          List.of(new FieldError("Idempotency-Key", "1부터 255자까지의 재시도 키를 보내세요")));
    UUID environmentId = (UUID) null;
    String scope = environmentId == null ? "legacy" : environmentId.toString();
    String keyHash = hash(key);
    String requestHash = hash(jsonMapper.writeValueAsString(request));
    boolean created =
        jdbcTemplate.update(
                """
        INSERT INTO idempotent_request
          (organization_id, environment_id, environment_scope, operation, key_hash, request_hash)
        VALUES (?, ?, ?, ?, ?, ?)
        ON CONFLICT (organization_id, environment_scope, operation, key_hash) DO NOTHING
        """,
                organizationId,
                environmentId,
                scope,
                operation,
                keyHash,
                requestHash)
            == 1;
    if (!created) {
      var cached =
          jdbcTemplate.queryForObject(
              """
          SELECT request_hash, response_status, response_body FROM idempotent_request
          WHERE organization_id = ? AND environment_scope = ? AND operation = ? AND key_hash = ?
          FOR UPDATE
          """,
              (rs, row) ->
                  new StoredResponse(
                      rs.getString("request_hash"),
                      rs.getInt("response_status"),
                      rs.getString("response_body")),
              organizationId,
              scope,
              operation,
              keyHash);
      if (!requestHash.equals(cached.requestHash()))
        throw new ConflictException(ErrorCode.IDEMPOTENCY_CONFLICT);
      if (cached.body() == null)
        throw new IllegalStateException("Incomplete idempotent creation response");
      return ResponseEntity.status(cached.status())
          .contentType(MediaType.APPLICATION_JSON)
          .header("Idempotency-Replayed", "true")
          .body(jsonMapper.readValue(cached.body(), responseType));
    }
    ResponseEntity<T> response = action.get();
    jdbcTemplate.update(
        """
        UPDATE idempotent_request SET response_status = ?, response_body = ?
        WHERE organization_id = ? AND environment_scope = ? AND operation = ? AND key_hash = ?
        """,
        response.getStatusCode().value(),
        jsonMapper.writeValueAsString(response.getBody()),
        organizationId,
        scope,
        operation,
        keyHash);
    return response;
  }

  private static String hash(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException(exception);
    }
  }

  private record StoredResponse(String requestHash, int status, String body) {}
}
