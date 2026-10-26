package com.meterengine.apikey.repository;

import com.meterengine.apikey.entity.ApiKey;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

  Optional<ApiKey> findByKeyHash(byte[] keyHash);

  List<ApiKey> findByOrganizationIdOrderByCreatedAtAscIdAsc(UUID organizationId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT k FROM ApiKey k WHERE k.id = :id AND k.organizationId = :organizationId")
  Optional<ApiKey> findForUpdateByIdAndOrganizationId(
      @Param("id") UUID id, @Param("organizationId") UUID organizationId);
}
