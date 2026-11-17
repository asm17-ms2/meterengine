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
import io.swagger.v3.oas.annotations.media.ExampleObject;
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
      summary = "사용량 이벤트 batch 수집",
      description =
          """
          이벤트를 1건부터 100건까지 한 요청에 담아 보냅니다. 응답의 results에 보낸 순서대로 이벤트마다 결과가 하나씩 나오고, 이벤트 하나가 거절돼도 나머지 이벤트는 그대로 처리합니다.

          | HTTP 상태 | 뜻 | 나올 수 있는 result |
          | --- | --- | --- |
          | 200 | 모든 이벤트를 요청 안에서 처리했습니다. | stored, duplicate, rejected |
          | 202 | 받은 이벤트가 하나 이상 있습니다. 저장은 응답 뒤에 합니다. | accepted, rejected, failed |
          | 400 | 요청 전체를 거절했습니다. 본문을 JSON으로 읽을 수 없거나, events가 없거나 비었거나 100건을 넘습니다. | 없음(results 대신 오류 응답) |

          200과 202는 둘 다 성공입니다. 어느 쪽으로 답할지는 서버 구성에 따라 다르므로 상태 코드로 분기하지 말고 이벤트마다 result를 확인합니다.

          | result | 뜻 | 다시 보내기 |
          | --- | --- | --- |
          | stored | 이번 요청으로 저장했습니다. | 보내지 않습니다. |
          | duplicate | 같은 transaction_id의 이벤트가 이미 저장돼 있어 저장하지 않았습니다. 처음 저장한 이벤트를 유지합니다. | 보내지 않습니다. |
          | accepted | 받기만 했고 아직 저장하지 않았습니다. 고객 확인과 중복 판정을 포함한 저장은 응답 뒤에 합니다. | 보내지 않습니다. |
          | rejected | 거절했습니다. 이유는 error.code에 있습니다. | 그대로 보내면 같은 이유로 거절됩니다. 값을 고친 뒤 보냅니다. |
          | failed | 받지 못했습니다. | 같은 transaction_id로 다시 보냅니다. |

          ### accepted와 stored의 차이

          stored는 저장까지 끝났다는 뜻이고, accepted는 받았다는 뜻일 뿐 저장을 약속하지 않습니다. accepted 이벤트는 저장할 때 다음과 같이 처리합니다.

          - 고객이 없거나(customer_not_found) 보관한 고객이면(customer_archived) 저장하지 않고 수집 거절 기록(GET /v1/event-rejections/search)에 남깁니다.
          - 같은 transaction_id의 이벤트가 이미 저장돼 있으면 저장하지 않고 처음 이벤트를 유지합니다. 따로 기록하거나 알리지 않습니다.
          - 나머지는 저장합니다.

          이벤트 수집이 202로 답할 때는 요청에서 바로 rejected로 돌려준 이벤트가 수집 거절 기록에 남지 않습니다. 응답의 error를 로그에 남겨 확인합니다.

          ### 다시 보내야 하는 경우

          - result가 failed인 이벤트: 그 이벤트를 같은 transaction_id로 다시 보냅니다. 같은 transaction_id는 한 번만 저장되므로, 실제로는 받았던 이벤트를 다시 보내도 두 번 저장되지 않습니다.
          - 503 event_ingest_unavailable: 이벤트를 하나도 받지 못했습니다. 같은 요청을 그대로 다시 보냅니다.
          - 429 rate_limit_exceeded: 키의 분당 요청 한도를 넘었습니다. Retry-After 헤더의 초만큼 기다린 뒤 같은 요청을 다시 보냅니다.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "202",
        description = "받은 이벤트가 하나 이상 있습니다. 저장은 응답 뒤에 합니다. 이 응답에는 stored와 duplicate가 없습니다.",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = IngestEventBatchResponse.class),
                examples =
                    @ExampleObject(
                        name = "accepted_rejected_failed",
                        summary = "받음, 기간 지남, 받지 못함",
                        description = "0번은 받았고, 1번은 받는 기간이 지나 거절했고, 2번은 받지 못해 다시 보내야 하는 경우입니다.",
                        value =
                            """
                            {
                              "results": [
                                {"index": 0, "transaction_id": "tx-20261105-0001", "result": "accepted"},
                                {
                                  "index": 1,
                                  "transaction_id": "tx-20261105-0002",
                                  "result": "rejected",
                                  "error": {
                                    "code": "event_too_old",
                                    "message": "받는 기간이 지난 이벤트입니다",
                                    "errors": [{"field": "events[1].timestamp", "message": "수신 시각보다 35일 넘게 지난 시각입니다"}]
                                  }
                                },
                                {
                                  "index": 2,
                                  "transaction_id": "tx-20261105-0003",
                                  "result": "failed",
                                  "error": {
                                    "code": "event_ingest_unavailable",
                                    "message": "이벤트를 받지 못했습니다. 같은 transaction_id로 다시 보내세요"
                                  }
                                }
                              ]
                            }
                            """))),
    @ApiResponse(
        responseCode = "200",
        description = "모든 이벤트를 요청 안에서 처리했습니다. 이 응답에는 accepted와 failed가 없습니다.",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = IngestEventBatchResponse.class),
                examples =
                    @ExampleObject(
                        name = "stored_duplicate_rejected",
                        summary = "저장, 중복, 형식 오류",
                        description =
                            "0번은 저장했고, 1번은 이미 저장된 transaction_id라 저장하지 않았고, 2번은 timestamp에 오프셋이 없어 거절한 경우입니다.",
                        value =
                            """
                            {
                              "results": [
                                {"index": 0, "transaction_id": "tx-20261105-0001", "result": "stored"},
                                {"index": 1, "transaction_id": "tx-20261105-0002", "result": "duplicate"},
                                {
                                  "index": 2,
                                  "transaction_id": "tx-20261105-0003",
                                  "result": "rejected",
                                  "error": {
                                    "code": "validation_error",
                                    "message": "요청 값이 올바르지 않습니다",
                                    "errors": [{"field": "events[2].timestamp", "message": "이벤트 필드의 형식이 올바르지 않습니다"}]
                                  }
                                }
                              ]
                            }
                            """))),
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
