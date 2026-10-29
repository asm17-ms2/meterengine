package com.meterengine.account.repository;

import java.util.List;
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

  // 로그인 조회(lower(email))와 같은 DB 규칙으로 계정 키를 정규화해야 대소문자 변형이 다른 계정 키가 되지 않는다.
  public String normalizeAccount(String email) {
    return jdbcTemplate.queryForObject("SELECT lower(?)", String.class, email.strip());
  }

  // 거절된 시도도 저장하며, 로그인/가입 트랜잭션이 실패해도 카운터는 유지한다.
  // IP 한도를 넘은 요청은 계정 한도에 세지 않아, 한 IP의 폭주가 남의 계정을 잠그지 못하게 한다.
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public long consume(Bucket address, List<Bucket> accounts, long windowSeconds) {
    long blocked = take(address, windowSeconds);
    if (blocked > 0) return blocked;
    long retryAfter = 0;
    for (Bucket account : accounts) retryAfter = Math.max(retryAfter, take(account, windowSeconds));
    return retryAfter;
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

  private long take(Bucket bucket, long seconds) {
    return jdbcTemplate
        .queryForObject(
            CONSUME_SQL,
            (resultSet, rowNumber) ->
                new Window(resultSet.getLong("attempts"), resultSet.getLong("retry_after_seconds")),
            bucket.scope(),
            bucket.identityHash(),
            seconds,
            (long) bucket.limit() + 1)
        .retryAfter(bucket.limit());
  }

  public record Bucket(String scope, byte[] identityHash, int limit) {}

  private record Window(long attempts, long retryAfterSeconds) {
    long retryAfter(int limit) {
      return attempts > limit ? retryAfterSeconds : 0;
    }
  }
}
