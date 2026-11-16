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
      summary = "사용량 이벤트 묶음 수집",
      description =
          """
          1건부터 100건까지 받아 보낸 순서대로 이벤트마다 결과를 돌려줍니다. 이벤트 하나의 형식 오류는 다른 이벤트의 저장을 막지 않습니다. events가 없거나 비었거나 100건을 넘어 요청 전체의 형식이 틀리면 400입니다.
          받은 이벤트가 하나라도 있으면 202로 답하고 저장은 뒤에서 합니다. 이때 결과는 accepted, rejected, failed입니다. accepted는 받았고 저장은 뒤에서 한다는 뜻이고, failed는 받지 못했으니 같은 transaction_id로 다시 보내라는 뜻입니다. accepted 이벤트의 고객 확인과 중복 판정은 저장할 때 합니다.
          모든 이벤트를 요청 안에서 처리하면 200으로 답하기도 하며, 이 경우도 정상입니다. 이때 결과는 stored, duplicate, rejected입니다.
          200과 202를 모두 성공으로 처리하고, 이벤트마다 result를 확인하세요.
          이벤트 수집이 202로 답할 때는 rejected 이벤트가 수집 거절에 기록되지 않습니다.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "202", description = "받은 이벤트가 하나 이상 있습니다. 저장은 뒤에서 합니다."),
    @ApiResponse(responseCode = "200", description = "모든 이벤트를 요청 안에서 처리했습니다. 이벤트마다 저장 결과를 돌려줍니다."),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description =
            """
            code=malformed_request_body: 본문을 JSON으로 읽을 수 없습니다.
            code=validation_error: events가 없거나 비었거나 100건을 넘습니다. 이벤트 하나의 형식 오류는 이 응답이 아니라 그 이벤트의 rejected 결과로 돌려줍니다.
            """),
    @ApiResponse(
        responseCode = "413",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=request_body_too_large: 요청 본문이 512KB를 넘습니다."),
    @ApiResponse(
        responseCode = "503",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=event_ingest_unavailable: 이벤트를 하나도 받지 못했습니다. 그대로 다시 보내면 됩니다.")
  })
  public ResponseEntity<IngestEventBatchResponse> ingestEventBatch(
      @CurrentOrganizationId UUID organizationId,
      @Valid @RequestBody IngestEventBatchRequest request) {
    IngestEventBatchResponse response = eventIngestor.ingestBatch(organizationId, request);
    HttpStatus status = response.hasAcceptedEvent() ? HttpStatus.ACCEPTED : HttpStatus.OK;
    return ResponseEntity.status(status).body(response);
  }
}
