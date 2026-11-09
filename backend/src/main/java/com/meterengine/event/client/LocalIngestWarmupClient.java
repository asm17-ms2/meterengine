package com.meterengine.event.client;

import com.meterengine.event.dto.IngestEventBatchRequest;
import com.meterengine.event.dto.IngestEventBatchResponse;
import com.meterengine.event.dto.IngestEventRequest;
import com.meterengine.global.security.BusinessEnvironmentInterceptor;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import tools.jackson.databind.json.JsonMapper;

final class LocalIngestWarmupClient implements IngestWarmupClient {

  // 환경마다 이벤트가 한 행만 남도록 모든 예열 요청이 같은 거래 id를 쓴다.
  private static final String TRANSACTION_ID = "meterengine-warmup";

  private static final String EVENT_TYPE = "meterengine_warmup";

  private static final Map<String, Object> PROPERTIES =
      Map.of(
          "model", "meterengine-warmup",
          "input_tokens", 100,
          "output_tokens", 50,
          "cache_read_tokens", 10,
          "cache_creation_tokens", 5);

  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

  private static final String EVENTS_PATH = "/v1/events";

  private static final String EVENT_BATCHES_PATH = "/v1/event-batches";

  private static final String BEARER_PREFIX = "Bearer ";

  private static final Set<String> ACCEPTED_RESULTS = Set.of("stored", "duplicate");

  private final HttpClient httpClient;
  private final URI baseUri;
  private final String apiKey;
  private final UUID organizationId;
  private final UUID customerId;
  private final JsonMapper jsonMapper;
  private final Clock clock;

  LocalIngestWarmupClient(
      HttpClient httpClient,
      URI baseUri,
      String apiKey,
      UUID organizationId,
      UUID customerId,
      JsonMapper jsonMapper,
      Clock clock) {
    this.httpClient = httpClient;
    this.baseUri = baseUri;
    this.apiKey = apiKey;
    this.organizationId = organizationId;
    this.customerId = customerId;
    this.jsonMapper = jsonMapper;
    this.clock = clock;
  }

  @Override
  public void sendEvent() {
    post(EVENTS_PATH, event());
  }

  // 묶음은 원소가 거절돼도 200이라 결과까지 본다.
  @Override
  public void sendEventBatch() {
    var batch = new IngestEventBatchRequest(List.of(jsonMapper.valueToTree(event())));
    HttpResponse<String> response = post(EVENT_BATCHES_PATH, batch);
    if (!allAccepted(jsonMapper.readValue(response.body(), IngestEventBatchResponse.class))) {
      throw new IngestWarmupException(EVENT_BATCHES_PATH, response.statusCode(), response.body());
    }
  }

  @Override
  public void close() {
    httpClient.close();
  }

  private IngestEventRequest event() {
    return new IngestEventRequest(
        TRANSACTION_ID, customerId, EVENT_TYPE, PROPERTIES, OffsetDateTime.now(clock));
  }

  private HttpResponse<String> post(String path, Object body) {
    HttpRequest request =
        HttpRequest.newBuilder(baseUri.resolve(path))
            .timeout(REQUEST_TIMEOUT)
            .header(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + apiKey)
            .header(BusinessEnvironmentInterceptor.ORGANIZATION_HEADER, organizationId.toString())
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .POST(BodyPublishers.ofString(jsonMapper.writeValueAsString(body)))
            .build();
    HttpResponse<String> response = send(path, request);
    if (!HttpStatusCode.valueOf(response.statusCode()).is2xxSuccessful()) {
      throw new IngestWarmupException(path, response.statusCode(), response.body());
    }
    return response;
  }

  private HttpResponse<String> send(String path, HttpRequest request) {
    try {
      return httpClient.send(request, BodyHandlers.ofString());
    } catch (IOException exception) {
      throw new IngestWarmupException(path, exception);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IngestWarmupException(path, exception);
    }
  }

  private static boolean allAccepted(IngestEventBatchResponse response) {
    return response.results() != null
        && !response.results().isEmpty()
        && response.results().stream()
            .allMatch(result -> ACCEPTED_RESULTS.contains(result.result()));
  }
}
