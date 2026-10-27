package com.meterengine.event.controller;

import com.meterengine.event.dto.IngestEventBatchRequest;
import com.meterengine.event.dto.IngestEventBatchResponse;
import com.meterengine.event.service.EventBatchService;
import com.meterengine.event.service.EventRejectionService;
import com.meterengine.global.security.CurrentOrganizationId;
import com.meterengine.global.security.EnvironmentReady;
import com.meterengine.global.security.IngestAccess;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@EnvironmentReady
public class EventBatchController {
  private final EventBatchService eventBatchService;
  private final EventRejectionService eventRejectionService;

  EventBatchController(
      EventBatchService eventBatchService, EventRejectionService eventRejectionService) {
    this.eventBatchService = eventBatchService;
    this.eventRejectionService = eventRejectionService;
  }

  @PostMapping("/v1/event-batches")
  @IngestAccess
  @Operation(
      summary = "사용량 이벤트 일괄 수집",
      description =
          "1건부터 100건까지 받아 입력 순서대로 stored, duplicate, rejected 결과를 돌려준다. 원소의 형식 오류는 다른 원소의 저장을 막지 않는다. 봉투 오류는 400이다.")
  public IngestEventBatchResponse ingestEventBatch(
      @CurrentOrganizationId UUID organizationId,
      @Valid @RequestBody IngestEventBatchRequest request) {
    IngestEventBatchResponse response = eventBatchService.ingest(organizationId, request);
    eventRejectionService.record(organizationId, request, response);
    return response;
  }
}
