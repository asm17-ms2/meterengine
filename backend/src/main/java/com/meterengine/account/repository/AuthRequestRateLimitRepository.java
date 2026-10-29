package com.meterengine.account.repository;

import java.util.List;
import java.util.Optional;
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

  private static final String LIMITED_SQL =
      """
      SELECT GREATEST(1, CEIL(EXTRACT(EPOCH FROM expires_at - statement_timestamp())))
      FROM auth_request_rate_limit
      WHERE scope = ? AND identity_hash = ? AND expires_at > statement_timestamp() AND attempts > ?
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
  // 앞 한도가 거절한 요청은 뒤 한도에 세지 않는다. IP 한도를 넘었거나 계정과 IP 쌍 한도를 넘은 시도가
  // 계정 전체 상한을 채워, 한 IP의 폭주가 남의 계정을 잠그지 못하게 한다.
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Optional<Rejection> consume(Bucket address, List<Bucket> accounts, long windowSeconds) {
    long blocked = take(address, windowSeconds);
    if (blocked > 0) return Optional.of(new Rejection(address.scope(), blocked));
    for (Bucket account : accounts) {
      long retryAfter = take(account, windowSeconds);
      if (retryAfter > 0) return Optional.of(new Rejection(account.scope(), retryAfter));
    }
    return Optional.empty();
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

  // 이미 한도를 넘은 카운터는 upsert해도 값이 같으므로, 잠금 없이 읽고 끝내 거절 경로에서 쓰기와 행 잠금을 없앤다.
  private long take(Bucket bucket, long seconds) {
    List<Long> limited =
        jdbcTemplate.queryForList(
            LIMITED_SQL, Long.class, bucket.scope(), bucket.identityHash(), bucket.limit());
    if (!limited.isEmpty()) return limited.getFirst();
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

  public record Rejection(String scope, long retryAfterSeconds) {}

  private record Window(long attempts, long retryAfterSeconds) {
    long retryAfter(int limit) {
      return attempts > limit ? retryAfterSeconds : 0;
    }
  }
}
