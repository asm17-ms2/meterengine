package com.meterengine.webhook.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

@Service
public class WebhookSignatureService {
  public String sign(String secret, String id, String timestamp, String body) {
    try {
      byte[] key = Base64.getDecoder().decode(secret.substring("whsec_".length()));
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key, "HmacSHA256"));
      return "v1,"
          + Base64.getEncoder()
              .encodeToString(
                  mac.doFinal(
                      (id + "." + timestamp + "." + body).getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException("webhook signature failed", exception);
    }
  }
}
