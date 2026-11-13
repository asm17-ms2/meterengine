package com.meterengine.liveenvironment.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.liveenvironment.service.BusinessRegistrationLookup;
import com.meterengine.liveenvironment.service.BusinessRegistrationStatus;
import com.meterengine.liveenvironment.service.BusinessRegistrationStatus.State;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

// 공공데이터포털 "국세청_사업자등록정보 진위확인 및 상태조회 서비스"의 상태조회(POST /status)를 부른다.
public class NtsBusinessRegistrationLookup implements BusinessRegistrationLookup {

  private static final Logger logger = LoggerFactory.getLogger(NtsBusinessRegistrationLookup.class);

  private static final String OK = "OK";
  private static final String OPERATING_CODE = "01";
  private static final String SUSPENDED_CODE = "02";
  private static final String CLOSED_CODE = "03";
  private static final String UNREGISTERED_NOTICE = "등록되지 않은";

  private final RestClient restClient;
  private final URI statusUrl;

  public NtsBusinessRegistrationLookup(RestClient restClient, URI baseUrl, String serviceKey) {
    this.restClient = restClient;
    this.statusUrl = URI.create(baseUrl + "/status?serviceKey=" + encoded(serviceKey));
  }

  // 포털은 인증키를 인코딩한 값과 원문 둘 다 준다. 인코딩한 값(% 포함)은 그대로, 원문은 한 번만 인코딩한다.
  private static String encoded(String serviceKey) {
    return serviceKey.contains("%")
        ? serviceKey
        : URLEncoder.encode(serviceKey, StandardCharsets.UTF_8);
  }

  @Override
  public BusinessRegistrationStatus find(String businessRegistrationNumber) {
    StatusResponse response;
    try {
      response =
          restClient
              .post()
              .uri(statusUrl)
              .contentType(MediaType.APPLICATION_JSON)
              .accept(MediaType.APPLICATION_JSON)
              .body(Map.of("b_no", List.of(businessRegistrationNumber)))
              .retrieve()
              .body(StatusResponse.class);
    } catch (RestClientException exception) {
      // 예외 문구에는 인증키가 든 주소가 들어 있어 종류와 상태 코드만 남긴다.
      logger.warn(
          "NTS business status lookup failed: {}, status={}",
          exception.getClass().getSimpleName(),
          exception instanceof RestClientResponseException rejected
              ? rejected.getStatusCode().value()
              : null);
      return BusinessRegistrationStatus.unavailable();
    }
    if (response == null
        || !OK.equals(response.statusCode())
        || response.data() == null
        || response.data().isEmpty()) {
      logger.warn(
          "NTS business status lookup returned no usable result: status_code={}",
          response == null ? null : response.statusCode());
      return BusinessRegistrationStatus.unavailable();
    }
    BusinessStatus status = response.data().getFirst();
    if (!businessRegistrationNumber.equals(status.businessRegistrationNumber())) {
      logger.warn("NTS business status lookup returned another business registration number");
      return BusinessRegistrationStatus.unavailable();
    }
    String code = status.statusCode() == null ? "" : status.statusCode();
    return switch (code) {
      case OPERATING_CODE -> new BusinessRegistrationStatus(State.OPERATING, status.taxType());
      case SUSPENDED_CODE -> new BusinessRegistrationStatus(State.SUSPENDED, status.taxType());
      case CLOSED_CODE -> new BusinessRegistrationStatus(State.CLOSED, status.taxType());
      // 등록되지 않은 번호는 상태 코드가 비고 tax_type에 안내 문구가 온다. 문구가 없으면 판정하지 않는다.
      case "" -> {
        if (status.taxType() != null && status.taxType().contains(UNREGISTERED_NOTICE)) {
          yield new BusinessRegistrationStatus(State.UNREGISTERED, null);
        }
        logger.warn("NTS business status lookup returned an empty status code without a notice");
        yield BusinessRegistrationStatus.unavailable();
      }
      default -> {
        logger.warn("NTS business status lookup returned an unknown status code");
        yield BusinessRegistrationStatus.unavailable();
      }
    };
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  record StatusResponse(
      @JsonProperty("status_code") String statusCode, List<BusinessStatus> data) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record BusinessStatus(
      @JsonProperty("b_no") String businessRegistrationNumber,
      @JsonProperty("b_stt_cd") String statusCode,
      @JsonProperty("tax_type") String taxType) {}
}
