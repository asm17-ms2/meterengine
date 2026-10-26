package com.meterengine.account.repository;

import com.meterengine.account.entity.UserSession;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {

  Optional<UserSession> findByTokenHash(byte[] tokenHash);

  List<UserSession> findByUserAccountIdAndRevokedAtIsNull(UUID userAccountId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT s FROM UserSession s WHERE s.id = :id")
  Optional<UserSession> findForUpdateById(@Param("id") UUID id);
}
