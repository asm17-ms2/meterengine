package com.meterengine.account.repository;

import com.meterengine.account.entity.EmailVerification;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, UUID> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT v FROM EmailVerification v WHERE v.tokenHash=:hash")
  Optional<EmailVerification> findForUpdateByTokenHash(@Param("hash") byte[] tokenHash);
}
