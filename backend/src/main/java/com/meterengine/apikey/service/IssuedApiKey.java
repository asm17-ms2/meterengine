package com.meterengine.apikey.service;

import com.meterengine.apikey.entity.ApiKey;

public record IssuedApiKey(String secret, ApiKey apiKey) {

  @Override
  public String toString() {
    return "IssuedApiKey[secret=(redacted), apiKeyId=" + apiKey.getId() + "]";
  }
}
