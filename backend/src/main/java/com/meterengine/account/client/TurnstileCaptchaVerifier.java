package com.meterengine.account.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.account.service.CaptchaVerifier;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.InvalidRequestException;
import com.meterengine.global.error.ServiceUnavailableException;
import java.net.URI;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

public class TurnstileCaptchaVerifier implements CaptchaVerifier {

  private static final Logger logger = LoggerFactory.getLogger(TurnstileCaptchaVerifier.class);

  // Siteverify가 받는 토큰의 최대 길이다.
  private static final int TOKEN_MAX_LENGTH = 2048;

  // 방문자 탓이 아닌 실패다. 키 설정이 틀렸거나 Cloudflare 쪽 오류라 가입을 막되 다시 시도하게 한다.
  private static final Set<String> UNAVAILABLE_ERROR_CODES =
      Set.of("missing-input-secret", "invalid-input-secret", "bad-request", "internal-error");

  private final RestClient restClient;
  private final URI siteverifyUrl;
  private final String secretKey;
  private final Set<String> allowedHostnames;

  public TurnstileCaptchaVerifier(
      RestClient restClient, URI siteverifyUrl, String secretKey, List<String> allowedHostnames) {
    this.restClient = restClient;
    this.siteverifyUrl = siteverifyUrl;
    this.secretKey = secretKey;
    this.allowedHostnames = Set.copyOf(allowedHostnames);
  }

  @Override
  public void verify(String token, String action, String remoteAddress) {
    if (token == null || token.isBlank() || token.length() > TOKEN_MAX_LENGTH) {
      throw new InvalidRequestException(ErrorCode.CAPTCHA_FAILED);
    }
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("secret", secretKey);
    form.add("response", token);
    if (remoteAddress != null) form.add("remoteip", remoteAddress);
    SiteverifyResponse response;
    try {
      response =
          restClient
              .post()
              .uri(siteverifyUrl)
              .contentType(MediaType.APPLICATION_FORM_URLENCODED)
              .body(form)
              .retrieve()
              .body(SiteverifyResponse.class);
    } catch (RestClientException exception) {
      logger.warn("Turnstile siteverify failed: {}", exception.toString());
      throw new ServiceUnavailableException(ErrorCode.CAPTCHA_UNAVAILABLE);
    }
    if (response == null) {
      logger.warn("Turnstile siteverify returned no body");
      throw new ServiceUnavailableException(ErrorCode.CAPTCHA_UNAVAILABLE);
    }
    List<String> errorCodes = response.errorCodes() == null ? List.of() : response.errorCodes();
    if (!response.success()) {
      if (errorCodes.stream().anyMatch(UNAVAILABLE_ERROR_CODES::contains)) {
        logger.warn("Turnstile siteverify could not decide: error_codes={}", errorCodes);
        throw new ServiceUnavailableException(ErrorCode.CAPTCHA_UNAVAILABLE);
      }
      throw new InvalidRequestException(ErrorCode.CAPTCHA_FAILED);
    }
    if (response.hostname() == null || !allowedHostnames.contains(response.hostname())) {
      logger.warn("Turnstile token from unexpected hostname={}", response.hostname());
      throw new InvalidRequestException(ErrorCode.CAPTCHA_FAILED);
    }
    // 시험용 비밀 키의 응답에는 action이 없다(Cloudflare Testing 문서). 실제 키의 응답에만 있다.
    if (response.action() != null && !response.action().equals(action)) {
      logger.warn("Turnstile token for unexpected action={}", response.action());
      throw new InvalidRequestException(ErrorCode.CAPTCHA_FAILED);
    }
  }

  record SiteverifyResponse(
      boolean success,
      String hostname,
      String action,
      @JsonProperty("error-codes") List<String> errorCodes) {}
}
