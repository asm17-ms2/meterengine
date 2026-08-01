package com.meterengine.ingestion;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * 이벤트 수집. 멱등의 최종 방어선은 DB 유니크 제약이다 (ADR 0002, 0003).
 *
 * <p>select-then-insert로 중복을 거르지 않는다: 그 사이를 뚫는 동시 요청은 앱이 못 막는다. 항상 INSERT를 시도하고 유니크 충돌이면 기존 행을 돌려준다
 * (first-write-wins, MS2-26 결정 4).
 */
@Service
public class EventIngestionService {

  /** created가 true면 이번 요청으로 새로 저장된 것이다 (201). false면 기존 행 반환이다 (200). */
  public record IngestionResult(UsageEvent event, boolean created) {}

  private final UsageEventRepository repository;

  public EventIngestionService(UsageEventRepository repository) {
    this.repository = repository;
  }

  // 트랜잭션을 걸지 않는다: INSERT 단문은 그 자체로 원자적이고, 충돌 후 조회가 같은
  // 트랜잭션 안에 있으면 유니크 위반이 트랜잭션을 rollback-only로 만들어 조회까지 죽는다.
  public IngestionResult ingest(NewUsageEvent newEvent) {
    try {
      return new IngestionResult(repository.insert(newEvent), true);
    } catch (DuplicateKeyException e) {
      UsageEvent existing =
          repository
              .findByTransactionId(newEvent.organizationId(), newEvent.transactionId())
              .orElseThrow(() -> e);
      return new IngestionResult(existing, false);
    }
  }
}
