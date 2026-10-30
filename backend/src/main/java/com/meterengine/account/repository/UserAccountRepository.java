package com.meterengine.account.repository;

import com.meterengine.account.entity.UserAccount;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

  @Query("SELECT u FROM UserAccount u WHERE lower(u.email) = lower(:email)")
  Optional<UserAccount> findByEmailIgnoreCase(@Param("email") String email);

  List<UserAccount> findByOrderByCreatedAtAscIdAsc();

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT u FROM UserAccount u WHERE u.role = 'operator' ORDER BY u.id")
  List<UserAccount> findOperatorsForUpdate();

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT u FROM UserAccount u WHERE u.id = :id")
  Optional<UserAccount> findForUpdateById(@Param("id") UUID id);
}
