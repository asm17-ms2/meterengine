package com.meterengine.apikey.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.apikey.entity.ApiKey;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record ListApiKeysResponse(
    @Schema(description = "도입사의 키. 선택한 상태에 맞는 키를 만든 순서로 담는다") @JsonProperty("api_keys")
        List<ApiKeyResponse> apiKeys) {

  public static ListApiKeysResponse from(List<ApiKey> apiKeys) {
    return new ListApiKeysResponse(apiKeys.stream().map(ApiKeyResponse::from).toList());
  }
}
