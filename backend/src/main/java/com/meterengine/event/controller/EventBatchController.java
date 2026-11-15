package com.meterengine.event.controller;

import com.meterengine.event.dto.IngestEventBatchRequest;
import com.meterengine.event.dto.IngestEventBatchResponse;
import com.meterengine.event.service.EventIngestor;
import com.meterengine.global.error.ErrorResponse;
import com.meterengine.global.security.CurrentOrganizationId;
import com.meterengine.global.security.EnvironmentReady;
import com.meterengine.global.security.IngestAccess;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@EnvironmentReady
public class EventBatchController {
  private final EventIngestor eventIngestor;

  EventBatchController(EventIngestor eventIngestor) {
    this.eventIngestor = eventIngestor;
  }

  @PostMapping("/v1/event-batches")
  @IngestAccess
  @Operation(
      summary = "사용량 이벤트 일괄 수집",
      description =
          """
          1건부터 100건까지 받아 입력 순서대로 stored, duplicate, rejected 결과를 돌려준다. 원소의 형식 오류는 다른 원소의 저장을 막지 않는다. 봉투 오류는 400이다.
          서버가 이벤트를 받아 두고 뒤에서 저장하도록 설정된 환경에서는 결과가 accepted, rejected, failed다.
          accepted는 받았고 저장은 뒤에서 한다는 뜻이고, failed는 받지 못했으니 같은 transaction_id로 다시 보내라는 뜻이다.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "원소마다 저장 결과를 돌려준다"),
    @ApiResponse(responseCode = "202", description = "받은 원소가 하나 이상 있다. 저장은 뒤에서 한다"),
    @ApiResponse(
        responseCode = "413",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=request_body_too_large: 요청 본문이 512KB를 넘는다"),
    @ApiResponse(
        responseCode = "503",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=event_ingest_unavailable: 한 원소도 받지 못했다. 그대로 다시 보내면 된다")
  })
  public ResponseEntity<IngestEventBatchResponse> ingestEventBatch(
      @CurrentOrganizationId UUID organizationId,
      @Valid @RequestBody IngestEventBatchRequest request) {
    IngestEventBatchResponse response = eventIngestor.ingestBatch(organizationId, request);
    HttpStatus status = response.hasAcceptedEvent() ? HttpStatus.ACCEPTED : HttpStatus.OK;
    return ResponseEntity.status(status).body(response);
  }
}
