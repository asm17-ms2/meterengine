package com.meterengine.invoice.repository;

import com.meterengine.invoice.entity.InvoiceFinalizationAttempt;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

// environmentId가 null이면 환경 구분 이전 범위다. 모든 조회와 표시는 그 환경 범위 안에서만 본다.
public interface InvoiceFinalizationAttemptRepository
    extends JpaRepository<InvoiceFinalizationAttempt, UUID> {

  // 그 달 마감 전에 등록됐고 그 달에 끝난 시도가 없는 고객이다.
  @Query(
      value =
          """
          SELECT c.id
          FROM customer c
          WHERE c.organization_id = :organizationId
            AND c.environment_scope_id = COALESCE(
              CAST(:environmentId AS UUID), CAST('00000000-0000-0000-0000-000000000000' AS UUID))
            AND c.created_at < :registeredBefore
            AND NOT EXISTS (
              SELECT 1 FROM invoice_finalization_attempt a
              WHERE a.organization_id = c.organization_id
                AND a.environment_scope_id = c.environment_scope_id
                AND a.period = :period
                AND a.customer_id = c.id
                AND a.result IN ('finalized', 'no_events', 'skipped', 'zero_amount'))
          ORDER BY c.name, c.id
          """,
      nativeQuery = true)
  List<UUID> findUnfinishedCandidates(
      UUID organizationId, UUID environmentId, String period, OffsetDateTime registeredBefore);

  // 끝난 시도도 인보이스도 없는 (고객, 달)마다 마지막 시도다. 마지막 결과는 보류나 실패다.
  @Query(
      value =
          """
          SELECT DISTINCT ON (a.customer_id, a.period)
                 a.customer_id AS customerId, a.period AS period, a.result AS result
          FROM invoice_finalization_attempt a
          WHERE a.organization_id = :organizationId
            AND a.environment_scope_id = COALESCE(
              CAST(:environmentId AS UUID), CAST('00000000-0000-0000-0000-000000000000' AS UUID))
            AND a.result IN ('held', 'failed')
            AND NOT EXISTS (
              SELECT 1 FROM invoice_finalization_attempt f
              WHERE f.organization_id = a.organization_id
                AND f.environment_scope_id = a.environment_scope_id
                AND f.period = a.period
                AND f.customer_id = a.customer_id
                AND f.result IN ('finalized', 'no_events', 'skipped', 'zero_amount'))
            AND NOT EXISTS (
              SELECT 1 FROM invoice i
              WHERE i.organization_id = a.organization_id
                AND i.environment_scope_id = a.environment_scope_id
                AND i.customer_id = a.customer_id
                AND i.period = a.period)
          ORDER BY a.customer_id, a.period, a.attempted_at DESC
          """,
      nativeQuery = true)
  List<OpenAttempt> findOpenAttempts(UUID organizationId, UUID environmentId);

  // 모든 도입사의 열린 시도를 환경(legacy, test, live)과 마지막 결과마다 센다.
  @Query(
      value =
          """
          SELECT latest.environment AS environment, latest.result AS result, count(*) AS count
          FROM (
            SELECT DISTINCT ON (a.organization_id, a.environment_scope_id, a.customer_id, a.period)
                   COALESCE(e.mode, 'legacy') AS environment, a.result
            FROM invoice_finalization_attempt a
            LEFT JOIN organization_environment e ON e.id = a.environment_id
            WHERE a.result IN ('held', 'failed')
              AND NOT EXISTS (
                SELECT 1 FROM invoice_finalization_attempt f
                WHERE f.organization_id = a.organization_id
                  AND f.environment_scope_id = a.environment_scope_id
                  AND f.period = a.period
                  AND f.customer_id = a.customer_id
                  AND f.result IN ('finalized', 'no_events', 'skipped', 'zero_amount'))
              AND NOT EXISTS (
                SELECT 1 FROM invoice i
                WHERE i.organization_id = a.organization_id
                  AND i.environment_scope_id = a.environment_scope_id
                  AND i.customer_id = a.customer_id
                  AND i.period = a.period)
            ORDER BY a.organization_id, a.environment_scope_id, a.customer_id, a.period,
                     a.attempted_at DESC
          ) latest
          GROUP BY latest.environment, latest.result
          """,
      nativeQuery = true)
  List<OpenCount> countOpen();

  @Query(
      value =
          """
          SELECT period FROM invoice_finalization_month
          WHERE organization_id = :organizationId
            AND environment_scope_id = COALESCE(
              CAST(:environmentId AS UUID), CAST('00000000-0000-0000-0000-000000000000' AS UUID))
          """,
      nativeQuery = true)
  List<String> findSweptPeriods(UUID organizationId, UUID environmentId);

  @Query(
      value =
          """
          SELECT EXISTS (
            SELECT 1 FROM invoice_finalization_month
            WHERE organization_id = :organizationId
              AND environment_scope_id = COALESCE(
                CAST(:environmentId AS UUID), CAST('00000000-0000-0000-0000-000000000000' AS UUID))
              AND period = :period)
          """,
      nativeQuery = true)
  boolean existsSweptPeriod(UUID organizationId, UUID environmentId, String period);

  // 그 달에 끝난 시도도 인보이스도 없고 마지막 시도가 실패인 고객이 있으면 다음 정시가 다시 시도한다.
  @Query(
      value =
          """
          SELECT EXISTS (
            SELECT 1
            FROM (
              SELECT DISTINCT ON (a.customer_id) a.result
              FROM invoice_finalization_attempt a
              WHERE a.organization_id = :organizationId
                AND a.environment_scope_id = COALESCE(
                  CAST(:environmentId AS UUID),
                  CAST('00000000-0000-0000-0000-000000000000' AS UUID))
                AND a.period = :period
                AND a.result IN ('held', 'failed')
                AND NOT EXISTS (
                  SELECT 1 FROM invoice_finalization_attempt f
                  WHERE f.organization_id = a.organization_id
                    AND f.environment_scope_id = a.environment_scope_id
                    AND f.period = a.period
                    AND f.customer_id = a.customer_id
                    AND f.result IN ('finalized', 'no_events', 'skipped', 'zero_amount'))
                AND NOT EXISTS (
                  SELECT 1 FROM invoice i
                  WHERE i.organization_id = a.organization_id
                    AND i.environment_scope_id = a.environment_scope_id
                    AND i.customer_id = a.customer_id
                    AND i.period = a.period)
              ORDER BY a.customer_id, a.attempted_at DESC
            ) latest
            WHERE latest.result = 'failed')
          """,
      nativeQuery = true)
  boolean existsOpenFailure(UUID organizationId, UUID environmentId, String period);

  // 그 달의 대상 고객을 모두 한 번씩 시도했다는 표시다. 표시가 있는 달은 후보를 다시 훑지 않는다.
  @Modifying
  @Transactional
  @Query(
      value =
          """
          INSERT INTO invoice_finalization_month
            (organization_id, environment_id, period, swept_at)
          VALUES (:organizationId, CAST(:environmentId AS UUID), :period, :sweptAt)
          ON CONFLICT (organization_id, environment_scope_id, period) DO NOTHING
          """,
      nativeQuery = true)
  int insertSweptIfAbsent(
      UUID organizationId, UUID environmentId, String period, OffsetDateTime sweptAt);

  interface OpenAttempt {

    UUID getCustomerId();

    String getPeriod();

    String getResult();
  }

  interface OpenCount {

    String getEnvironment();

    String getResult();

    long getCount();
  }
}
