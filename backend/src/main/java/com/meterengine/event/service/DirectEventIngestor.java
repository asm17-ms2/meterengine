package com.meterengine.event.service;

import com.meterengine.event.config.EventKafkaConfig;
import com.meterengine.event.dto.IngestEventBatchRequest;
import com.meterengine.event.dto.IngestEventBatchResponse;
import com.meterengine.event.dto.IngestEventRequest;
import com.meterengine.event.dto.IngestEventResponse;
import com.meterengine.global.error.BusinessException;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

// 요청 안에서 DB에 바로 저장하고, 거절한 이벤트는 거절 기록에 남긴다.
@Component
@ConditionalOnProperty(
    name = EventKafkaConfig.WRITE_PATH,
    havingValue = EventKafkaConfig.DIRECT,
    matchIfMissing = true)
public class DirectEventIngestor implements EventIngestor {

  private final EventIngestionService eventIngestionService;
  private final EventBatchService eventBatchService;
  private final EventRejectionService eventRejectionService;

  DirectEventIngestor(
      EventIngestionService eventIngestionService,
      EventBatchService eventBatchService,
      EventRejectionService eventRejectionService) {
    this.eventIngestionService = eventIngestionService;
    this.eventBatchService = eventBatchService;
    this.eventRejectionService = eventRejectionService;
  }

  @Override
  public IngestEventResponse ingest(UUID organizationId, IngestEventRequest request) {
    try {
      return eventIngestionService.ingest(organizationId, request);
    } catch (BusinessException exception) {
      eventRejectionService.record(organizationId, request, exception);
      throw exception;
    }
  }

  @Override
  public IngestEventBatchResponse ingestBatch(UUID organizationId, IngestEventBatchRequest batch) {
    IngestEventBatchResponse response = eventBatchService.ingest(organizationId, batch);
    eventRejectionService.record(organizationId, batch, response);
    return response;
  }
}
