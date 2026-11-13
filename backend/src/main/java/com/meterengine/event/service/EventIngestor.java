package com.meterengine.event.service;

import com.meterengine.event.dto.IngestEventBatchRequest;
import com.meterengine.event.dto.IngestEventBatchResponse;
import com.meterengine.event.dto.IngestEventRequest;
import com.meterengine.event.dto.IngestEventResponse;
import java.util.UUID;

// 수집 API가 받은 이벤트를 어디로 보낼지 가른다. 구현은 meterengine.event.write-path 값이 고른다.
public interface EventIngestor {

  IngestEventResponse ingest(UUID organizationId, IngestEventRequest request);

  IngestEventBatchResponse ingestBatch(UUID organizationId, IngestEventBatchRequest batch);
}
