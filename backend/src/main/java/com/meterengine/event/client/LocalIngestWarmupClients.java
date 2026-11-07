package com.meterengine.event.client;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class LocalIngestWarmupClients implements IngestWarmupClients {

  // 서버가 듣기 시작한 뒤에만 생기는 값이라 빈을 만들 때가 아니라 열 때 읽는다.
  private static final String LOCAL_SERVER_PORT = "local.server.port";

  private static final String LOOPBACK_ADDRESS = "http://127.0.0.1:";

  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);

  private final Environment environment;
  private final JsonMapper jsonMapper;
  private final Clock clock;

  LocalIngestWarmupClients(Environment environment, JsonMapper jsonMapper, Clock clock) {
    this.environment = environment;
    this.jsonMapper = jsonMapper;
    this.clock = clock;
  }

  // ALB에서 오는 요청과 같게 HTTP/1.1로 고정한다.
  @Override
  public IngestWarmupClient open(String apiKey, UUID organizationId, UUID customerId) {
    int port = environment.getRequiredProperty(LOCAL_SERVER_PORT, Integer.class);
    HttpClient httpClient =
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(CONNECT_TIMEOUT)
            .build();
    return new LocalIngestWarmupClient(
        httpClient,
        URI.create(LOOPBACK_ADDRESS + port),
        apiKey,
        organizationId,
        customerId,
        jsonMapper,
        clock);
  }
}
