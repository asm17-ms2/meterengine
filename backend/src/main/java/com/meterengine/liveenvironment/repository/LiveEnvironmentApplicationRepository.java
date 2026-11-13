package com.meterengine.liveenvironment.repository;

import com.meterengine.liveenvironment.entity.LiveEnvironmentApplication;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LiveEnvironmentApplicationRepository
    extends JpaRepository<LiveEnvironmentApplication, UUID> {

  List<LiveEnvironmentApplication> findByOrganizationIdOrderByCreatedAtDescIdDesc(
      UUID organizationId);

  List<LiveEnvironmentApplication> findByOrderByCreatedAtDescIdDesc();

  List<LiveEnvironmentApplication> findByStatusOrderByCreatedAtDescIdDesc(String status);

  boolean existsByOrganizationIdAndStatus(UUID organizationId, String status);

  // 조직 행을 먼저 잠그려고 신청을 영속성 컨텍스트에 올리지 않고 조직 ID만 읽는다.
  @Query("SELECT a.organizationId FROM LiveEnvironmentApplication a WHERE a.id = :id")
  Optional<UUID> findOrganizationIdById(@Param("id") UUID id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT a FROM LiveEnvironmentApplication a WHERE a.id = :id")
  Optional<LiveEnvironmentApplication> findForUpdateById(@Param("id") UUID id);
}
