package com.meterengine.account.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class AuthRequestRateLimitRepository {
  private static final String CONSUME_SQL =
      """
      INSERT INTO auth_request_rate_limit AS counter
        (scope, identity_hash, attempts, expires_at)
      VALUES (?, ?, 1, statement_timestamp() + make_interval(secs => ?))
      ON CONFLICT (scope, identity_hash) DO UPDATE
      SET attempts = CASE WHEN counter.expires_at <= statement_timestamp()
          THEN 1 ELSE LEAST(counter.attempts + 1, ?) END,
        expires_at = CASE WHEN counter.expires_at <= statement_timestamp()
          THEN EXCLUDED.expires_at ELSE counter.expires_at END
      RETURNING attempts,
        GREATEST(1, CEIL(EXTRACT(EPOCH FROM expires_at - statement_timestamp())))
          AS retry_after_seconds
      """;

  private final JdbcTemplate jdbcTemplate;

  public AuthRequestRateLimitRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  // 거절된 시도도 저장하며, 로그인/가입 트랜잭션이 실패해도 카운터는 유지한다.
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public long consume(
      String action,
      byte[] addressHash,
      byte[] accountHash,
      int addressLimit,
      int accountLimit,
      long windowSeconds) {
    Window address = take(action + "_ip", addressHash, addressLimit, windowSeconds);
    Window account = take(action + "_account", accountHash, accountLimit, windowSeconds);
    return Math.max(address.retryAfter(addressLimit), account.retryAfter(accountLimit));
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void cleanExpired() {
    jdbcTemplate.update(
        """
        DELETE FROM auth_request_rate_limit
        WHERE (scope, identity_hash) IN (
          SELECT scope, identity_hash FROM auth_request_rate_limit
          WHERE expires_at <= statement_timestamp()
          ORDER BY expires_at LIMIT 100 FOR UPDATE SKIP LOCKED
        )
        """);
  }

  private Window take(String scope, byte[] hash, int limit, long seconds) {
    return jdbcTemplate.queryForObject(
        CONSUME_SQL,
        (resultSet, rowNumber) ->
            new Window(resultSet.getLong("attempts"), resultSet.getLong("retry_after_seconds")),
        scope,
        hash,
        seconds,
        (long) limit + 1);
  }

  private record Window(long attempts, long retryAfterSeconds) {
    long retryAfter(int limit) {
      return attempts > limit ? retryAfterSeconds : 0;
    }
  }
}
