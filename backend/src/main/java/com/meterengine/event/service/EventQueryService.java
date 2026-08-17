package com.meterengine.event.service;

import com.meterengine.customer.repository.CustomerRepository;
import com.meterengine.event.dto.EventPageResponse;
import com.meterengine.event.dto.EventRow;
import com.meterengine.event.exception.UnknownCustomerException;
import com.meterengine.event.repository.EventRepository;
import com.meterengine.metric.service.MetricUsageService;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이벤트 로그 조회 (MS2-131).
 *
 * <p>수집한 이벤트를 화면이 표로 그릴 수 있게 한 페이지씩 돌려준다. 값을 해석하지 않는다. 어느 키가 사용량인지 판정하고 합치는 일은 집계(MS2-129)의 몫이다.
 */
@Service
public class EventQueryService {

  private final EventRepository usageEvents;
  private final CustomerRepository customers;

  EventQueryService(EventRepository usageEvents, CustomerRepository customers) {
    this.usageEvents = usageEvents;
    this.customers = customers;
  }

  /**
   * 지정 월의 이벤트를 최신순 한 페이지 돌려준다.
   *
   * <p><b>기간 계산을 여기서 하지 않고 집계와 공유한다.</b> {@link MetricUsageService#BILLING_ZONE}과 반열린 구간 [start,
   * end)를 그대로 쓴다. 같은 상수를 두 벌 두면 한쪽만 고쳐질 때 사용량 집계 화면과 이벤트 로그가 서로 다른 달을 보게 된다.
   *
   * <p><b>읽기 트랜잭션으로 묶되, 두 쿼리의 스냅샷이 같다고 보장하지는 않는다.</b> 기본 격리 수준이 READ COMMITTED라 문장마다 새 스냅샷을 뜨므로,
   * count와 findPage 사이에 수집이 커밋되면 total과 목록이 한 눈금 어긋날 수 있다. 이 슬라이스는 그것을 감수한다. offset 방식이 이미 같은 종류의
   * 밀림을 감수하기로 한 결정이고(화면 갱신은 사용자가 새로고침을 누를 때만 일어난다), 막으려면 REPEATABLE READ가 필요한데 조회 하나 때문에 격리 수준을 올릴
   * 이유가 아직 없다. 트랜잭션은 두 쿼리가 같은 커넥션을 쓰게 하는 몫만 한다.
   *
   * @param customerId null이면 고객을 좁히지 않는다. 값이 있으면 이 도입사 고객인지 먼저 판정한다
   * @throws UnknownCustomerException 미등록이거나 다른 도입사 소속인 customerId
   */
  @Transactional(readOnly = true)
  public EventPageResponse query(
      UUID organizationId, UUID customerId, YearMonth month, String eventType, int page, int size) {
    // 고객 판정을 먼저 한다. 빈 목록은 이미 "이 고객은 이벤트가 없다"는 뜻을 갖고 있어서(시드의 베타 스튜디오가 그 경우다),
    // 잘못된 ID에도 빈 목록을 주면 두 경우가 화면에서 똑같아진다. 그러면 FE 버그가 정상 화면으로 위장된다.
    // 조건에 organization_id가 함께 들어가므로 미등록과 타 도입사 소속은 구별되지 않는다. 남의 도입사에 그 고객이
    // 있다는 사실을 흘리지 않는다 (수집 API와 같은 판정, 같은 예외).
    if (customerId != null && !customers.existsByOrganizationIdAndId(organizationId, customerId)) {
      throw new UnknownCustomerException(organizationId, customerId);
    }

    OffsetDateTime start =
        month.atDay(1).atStartOfDay(MetricUsageService.BILLING_ZONE).toOffsetDateTime();
    OffsetDateTime end =
        month
            .plusMonths(1)
            .atDay(1)
            .atStartOfDay(MetricUsageService.BILLING_ZONE)
            .toOffsetDateTime();

    long total = usageEvents.count(organizationId, customerId, eventType, start, end);
    List<EventRow> rows =
        usageEvents.findPage(organizationId, customerId, eventType, start, end, page, size);

    return new EventPageResponse(
        month.toString(),
        page,
        size,
        total,
        rows.stream().map(EventQueryService::toEntry).toList());
  }

  private static EventPageResponse.EventEntry toEntry(EventRow row) {
    return new EventPageResponse.EventEntry(
        row.transactionId(),
        row.customerId(),
        row.customerName(),
        row.eventType(),
        row.propertiesJson(),
        row.occurredAt(),
        row.receivedAt());
  }
}
