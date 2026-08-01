package com.meterengine.ingestion;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 사용량 이벤트 단건 수집 (openapi.yaml ingestEvent, MS2-38). */
@RestController
@RequestMapping("/v1/events")
public class EventController {

  private final EventIngestionService service;

  // MS2-38 임시: 인증 스텁(PR 4)이 API 키 -> 도입사 매핑으로 대체할 때까지 설정의 고정 도입사를 쓴다.
  private final UUID organizationId;

  public EventController(
      EventIngestionService service,
      @Value("${meterengine.ingestion.organization-id}") UUID organizationId) {
    this.service = service;
    this.organizationId = organizationId;
  }

  @PostMapping
  public ResponseEntity<EventIngestResponse> ingest(@RequestBody EventIngestRequest request) {
    EventIngestRequest.EventInput input = request.event();
    EventIngestionService.IngestionResult result =
        service.ingest(
            new NewUsageEvent(
                organizationId,
                input.transactionId(),
                input.externalCustomerId(),
                input.code(),
                input.propertiesOrEmpty(),
                input.occurredAt()));
    return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
        .body(EventIngestResponse.from(result.event()));
  }
}
