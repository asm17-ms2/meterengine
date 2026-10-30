package com.meterengine.account.repository;

import com.meterengine.account.entity.UserSession;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {

  Optional<UserSession> findByTokenHash(byte[] tokenHash);

  List<UserSession> findByUserAccountIdAndRevokedAtIsNull(UUID userAccountId);

  @Modifying(flushAutomatically = true)
  @Query(
      "UPDATE UserSession s SET s.organizationId = null, s.revokedAt = :now WHERE s.organizationId"
          + " = :org AND s.userAccountId = :user")
  void detachMembership(
      @Param("org") UUID organizationId,
      @Param("user") UUID userAccountId,
      @Param("now") OffsetDateTime now);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT s FROM UserSession s WHERE s.id = :id")
  Optional<UserSession> findForUpdateById(@Param("id") UUID id);
}
