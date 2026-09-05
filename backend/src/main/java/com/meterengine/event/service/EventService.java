package com.meterengine.event.service;

import com.meterengine.customer.repository.CustomerRepository;
import com.meterengine.event.dto.Event;
import com.meterengine.event.dto.IngestEventRequest;
import com.meterengine.event.dto.IngestEventResponse;
import com.meterengine.event.dto.ListEventsResponse;
import com.meterengine.event.exception.UnknownCustomerException;
import com.meterengine.event.repository.EventRepository;
import com.meterengine.metric.service.BillableMetricUsageService;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
public class EventService {

  private final EventRepository eventRepository;
  private final CustomerRepository customerRepository;
  private final JsonMapper jsonMapper;

  EventService(
      EventRepository eventRepository,
      CustomerRepository customerRepository,
      JsonMapper jsonMapper) {
    this.eventRepository = eventRepository;
    this.customerRepository = customerRepository;
    this.jsonMapper = jsonMapper;
  }

  /** 이벤트 수집 (MS2-130). 형식은 컨트롤러가 검증하고, 여기는 고객 판정과 멱등 저장만 한다. */
  public IngestEventResponse ingest(UUID organizationId, IngestEventRequest request) {
    if (!customerRepository.existsByOrganizationIdAndId(organizationId, request.customerId())) {
      throw new UnknownCustomerException(organizationId, request.customerId());
    }

    // properties는 원문 그대로 넘긴다. 어느 키가 사용량 값인지 판정하지 않는다.
    String propertiesJson = jsonMapper.writeValueAsString(request.properties());

    try {
      int inserted =
          eventRepository.insertIfAbsent(
              organizationId,
              request.transactionId(),
              request.customerId(),
              request.eventType(),
              propertiesJson,
              request.timestamp());
      return inserted == 1
          ? IngestEventResponse.stored(request.transactionId())
          : IngestEventResponse.alreadyStored(request.transactionId());
    } catch (DuplicateKeyException alreadyStored) {
      // ON CONFLICT DO NOTHING이 이미 걸러내므로 정상 경로에서는 도달하지 않는다. 누군가 그 절을 지웠을
      // 때 500 대신 200이 나가도록 받쳐 두는 안전망이다. 중복 키만 잡는다. FK 위반이나 jsonb가 담을 수
      // 없는 값까지 삼키면 저장되지 않은 이벤트를 저장됐다고 답하게 된다 (그건 예외 핸들러가 400으로).
      //
      // 주의: ingest()가 트랜잭션 밖에서 도는 지금 구조라 안전하다. PostgreSQL은 제약 위반이 나면
      // 트랜잭션을 abort 상태로 만들어서, 배치 엔드포인트 같은 것이 ingest()를 한 트랜잭션으로 감싸면
      // 여기서 삼키고 200을 답해도 커밋이 롤백된다. 그때는 이 catch를 걷어내야 한다.
      return IngestEventResponse.alreadyStored(request.transactionId());
    }
  }

  /**
   * 이벤트 로그 조회 (MS2-131). 지정 월의 이벤트를 최신순 한 페이지 돌려준다.
   *
   * <p>수집한 이벤트를 화면이 표로 그릴 수 있게 한 페이지씩 돌려준다. 값을 해석하지 않는다. 어느 키가 사용량인지 판정하고 합치는 일은 집계(MS2-129)의 몫이다.
   *
   * <p><b>기간 계산을 여기서 하지 않고 집계와 공유한다.</b> {@link BillableMetricUsageService#BILLING_ZONE}과 반열린 구간
   * [start, end)를 그대로 쓴다. 같은 상수를 두 벌 두면 한쪽만 고쳐질 때 사용량 집계 화면과 이벤트 로그가 서로 다른 달을 보게 된다.
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
  public ListEventsResponse list(
      UUID organizationId, UUID customerId, YearMonth month, String eventType, int page, int size) {
    // 고객 판정을 먼저 한다. 빈 목록은 이미 "이 고객은 이벤트가 없다"는 뜻을 갖고 있어서(시드의 베타 스튜디오가 그 경우다),
    // 잘못된 ID에도 빈 목록을 주면 두 경우가 화면에서 똑같아진다. 그러면 FE 버그가 정상 화면으로 위장된다.
    // 조건에 organization_id가 함께 들어가므로 미등록과 타 도입사 소속은 구별되지 않는다. 남의 도입사에 그 고객이
    // 있다는 사실을 흘리지 않는다 (수집 API와 같은 판정, 같은 예외).
    if (customerId != null
        && !customerRepository.existsByOrganizationIdAndId(organizationId, customerId)) {
      throw new UnknownCustomerException(organizationId, customerId);
    }

    OffsetDateTime start =
        month.atDay(1).atStartOfDay(BillableMetricUsageService.BILLING_ZONE).toOffsetDateTime();
    OffsetDateTime end =
        month
            .plusMonths(1)
            .atDay(1)
            .atStartOfDay(BillableMetricUsageService.BILLING_ZONE)
            .toOffsetDateTime();

    long total = eventRepository.count(organizationId, customerId, eventType, start, end);
    List<Event> events =
        eventRepository.findPage(organizationId, customerId, eventType, start, end, page, size);

    return new ListEventsResponse(
        month.toString(),
        page,
        size,
        total,
        events.stream().map(EventService::toEventResponse).toList());
  }

  private static ListEventsResponse.EventResponse toEventResponse(Event event) {
    return new ListEventsResponse.EventResponse(
        event.transactionId(),
        event.customerId(),
        event.customerName(),
        event.eventType(),
        event.propertiesJson(),
        event.occurredAt(),
        event.receivedAt());
  }
}
