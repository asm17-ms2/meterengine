package com.meterengine.webhook.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.global.validation.StorableText;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UpdateWebhookEndpointRequest(
    @Schema(description = "웹훅을 받을 자사 서버 주소입니다. HTTPS이고 공개된 주소여야 하며 2048자까지 받습니다.")
        @NotBlank @Size(max = 2048) @StorableText
        String url,
    @ArraySchema(
            arraySchema =
                @Schema(
                    description =
                        "받을 웹훅 종류입니다. 하나 이상 고르며 중복은 없애고 저장합니다. webhook_test(테스트 웹훅), unbilled_usage_detected(과금 제외 사용량), payment_succeeded(결제 성공), payment_failed(결제 실패), payment_canceled(결제 취소) 가운데 고릅니다."),
            schema =
                @Schema(
                    allowableValues = {
                      "webhook_test",
                      "unbilled_usage_detected",
                      "payment_succeeded",
                      "payment_failed",
                      "payment_canceled"
                    }))
        @NotEmpty @Size(min = 1, max = 20) @JsonProperty("event_types")
        List<@NotBlank @StorableText String> eventTypes,
    @Schema(
            description =
                "false면 이 웹훅 URL로 웹훅을 보내지 않습니다. 멈춘 동안 생긴 웹훅은 쌓이지 않아 true로 되돌려도 보내지 않고, 이미 대기 중이던 전송은 endpoint_disabled로 실패합니다. true면 그 뒤에 생긴 웹훅부터 보냅니다.")
        @NotNull Boolean enabled,
    @Schema(description = "웹훅 URL을 알아보려고 붙이는 메모입니다. 1000자까지 받고, 없으면 빼거나 null로 보냅니다.")
        @Size(max = 1000) @StorableText
        String description) {}
