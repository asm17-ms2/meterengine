package com.meterengine.webhook.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record ListWebhookDeliveriesResponse(
    @Schema(description = "0부터 세는 페이지 번호") int page,
    @Schema(description = "한 페이지에 담는 개수") int size,
    @Schema(description = "조건에 맞는 전송 전체 건수") long total,
    @Schema(description = "이 페이지의 전송입니다. 최신순으로 싣습니다.") List<WebhookDeliveryResponse> deliveries) {}
