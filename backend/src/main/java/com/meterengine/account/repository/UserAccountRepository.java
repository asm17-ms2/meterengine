package com.meterengine.account.repository;

import com.meterengine.account.entity.UserAccount;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

  @Query("SELECT u FROM UserAccount u WHERE lower(u.email) = lower(:email)")
  Optional<UserAccount> findByEmailIgnoreCase(@Param("email") String email);

  List<UserAccount> findByOrderByCreatedAtAscIdAsc();
}
