package com.meterengine.event.service;

import com.meterengine.customer.repository.CustomerRepository;
import com.meterengine.event.dto.IngestEventRequest;
import com.meterengine.event.dto.IngestEventResponse;
import com.meterengine.event.exception.UnknownCustomerException;
import com.meterengine.event.repository.UsageEventRepository;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/** 이벤트 수집 (MS2-130). 형식은 컨트롤러가 검증하고, 여기는 고객 판정과 멱등 저장만 한다. */
@Service
public class UsageEventIngestService {

  private final UsageEventRepository usageEvents;
  private final CustomerRepository customers;
  private final JsonMapper jsonMapper;

  UsageEventIngestService(
      UsageEventRepository usageEvents, CustomerRepository customers, JsonMapper jsonMapper) {
    this.usageEvents = usageEvents;
    this.customers = customers;
    this.jsonMapper = jsonMapper;
  }

  public IngestEventResponse ingest(UUID organizationId, IngestEventRequest request) {
    if (!customers.existsByOrganizationIdAndId(organizationId, request.customerId())) {
      throw new UnknownCustomerException(organizationId, request.customerId());
    }

    // properties는 원문 그대로 넘긴다. 어느 키가 사용량 값인지 판정하지 않는다.
    String propertiesJson = jsonMapper.writeValueAsString(request.properties());

    try {
      int inserted =
          usageEvents.insertIfAbsent(
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
}
