package com.meterengine.apikey.repository;

import com.meterengine.apikey.service.ApiKeyHash;
import com.meterengine.apikey.service.ApiKeySnapshot;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

// JPA를 거치지 않아 수천 행을 영속성 컨텍스트에 남기지 않고, 같은 트랜잭션의 SQL 변경도 그대로 본다.
@Repository
public class ApiKeySnapshotRepository {

  private static final String USABLE_SQL =
      """
      SELECT id, organization_id, environment_id, mode, scopes, rate_limit_per_minute,
        expires_at, revoked_at, key_hash
      FROM api_key
      WHERE revoked_at IS NULL AND (expires_at IS NULL OR expires_at > ?)
      """;

  private final JdbcTemplate jdbcTemplate;

  public ApiKeySnapshotRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public List<ApiKeySnapshot> findUsableAt(OffsetDateTime now) {
    return jdbcTemplate.query(USABLE_SQL, (rs, row) -> snapshotOf(rs), now);
  }

  private static ApiKeySnapshot snapshotOf(ResultSet rs) throws SQLException {
    Array scopes = rs.getArray("scopes");
    return new ApiKeySnapshot(
        new ApiKeyHash(rs.getBytes("key_hash")),
        rs.getObject("id", UUID.class),
        rs.getObject("organization_id", UUID.class),
        rs.getObject("environment_id", UUID.class),
        rs.getString("mode"),
        Set.copyOf(Arrays.asList((String[]) scopes.getArray())),
        rs.getObject("rate_limit_per_minute", Integer.class),
        rs.getObject("expires_at", OffsetDateTime.class),
        rs.getObject("revoked_at", OffsetDateTime.class));
  }
}
